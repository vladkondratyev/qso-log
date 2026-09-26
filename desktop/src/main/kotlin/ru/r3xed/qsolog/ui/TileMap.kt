package ru.r3xed.qsolog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import ru.r3xed.qsolog.AppDirs
import ru.r3xed.qsolog.USER_AGENT
import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.LatLon
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/** OpenStreetMap tiles: memory cache for the session, disk cache in the app data folder. */
private object Tiles {
    val images = mutableStateMapOf<String, ImageBitmap>()
    private val order = ArrayDeque<String>()
    private val inFlight = mutableSetOf<String>()
    // OSM tile usage policy: identify the app and keep parallel downloads low.
    private val gate = Semaphore(2)
    private const val MAX_IN_MEMORY = 400
    private const val MAX_AGE_MS = 30L * 24 * 3600 * 1000

    suspend fun request(z: Int, x: Int, y: Int) {
        val key = "$z/$x/$y"
        if (images.containsKey(key) || !inFlight.add(key)) return
        try {
            val img = withContext(Dispatchers.IO) { load(z, x, y) } ?: return
            images[key] = img
            order.addLast(key)
            while (order.size > MAX_IN_MEMORY) images.remove(order.removeFirst())
        } finally {
            inFlight.remove(key)
        }
    }

    private suspend fun load(z: Int, x: Int, y: Int): ImageBitmap? {
        val file = File(AppDirs.tiles, "$z/$x/$y.png")
        val bytes = if (file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS) {
            file.readBytes()
        } else {
            gate.withPermit {
                try {
                    val conn = URL("https://tile.openstreetmap.org/$z/$x/$y.png").openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", USER_AGENT)
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 15_000
                    if (conn.responseCode != 200) return null
                    conn.inputStream.use { it.readBytes() }.also {
                        file.parentFile.mkdirs()
                        file.writeBytes(it)
                    }
                } catch (e: Exception) {
                    if (file.exists()) file.readBytes() else return null
                }
            }
        }
        return try { Image.makeFromEncoded(bytes).toComposeImageBitmap() } catch (e: Exception) { null }
    }
}

private const val MIN_ZOOM = 1
private const val MAX_ZOOM = 17

/** Web Mercator position in tile units (1 unit = one tile at zoom 0). */
private fun unitX(lon: Double) = (lon + 180.0) / 360.0
private fun unitY(lat: Double): Double {
    val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
    return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2
}

/** Great-circle points with longitudes made continuous, so a path across 180° is not drawn around the globe. */
private fun unwrapped(points: List<LatLon>): List<LatLon> {
    var shift = 0.0
    return points.mapIndexed { i, p ->
        if (i > 0) {
            val prev = points[i - 1].lon
            if (p.lon - prev > 180) shift -= 360 else if (p.lon - prev < -180) shift += 360
        }
        LatLon(p.lat, p.lon + shift)
    }
}

/** A labelled point on the map; [onClick] makes it clickable. */
/** [home]: my own station, drawn larger as a target. */
class MapMarker(val pos: LatLon, val label: String, val color: Color, val onClick: (() -> Unit)? = null, val home: Boolean = false)

/** Zoom level and centre in world pixels, kept by the caller to return to the same view. */
data class MapCamera(val zoom: Int, val cx: Double, val cy: Double)

/**
 * Map with my station, the other station and the great-circle path.
 * [interactive]: drag to pan, wheel to zoom, +/- buttons. Otherwise a static preview.
 */
@Composable
fun TileMap(
    me: LatLon,
    them: LatLon,
    myLabel: String,
    theirLabel: String,
    interactive: Boolean,
    modifier: Modifier = Modifier,
) {
    val path = remember(me, them) { unwrapped(Geo.greatCircle(me, them, 128)) }
    val ink = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val markers = remember(path, myLabel, theirLabel, ink, primary) {
        listOf(MapMarker(path.first(), myLabel, ink), MapMarker(path.last(), theirLabel, primary))
    }
    MarkerMap(markers, path, interactive, filledLabels = false, fitHint = "Показать всю трассу", modifier = modifier)
}

/**
 * Tiles with labelled markers and an optional path. The view first fits all markers (or the path).
 * [filledLabels]: labels in the marker colour with white text (stations map); otherwise white with dark text.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MarkerMap(
    markers: List<MapMarker>,
    path: List<LatLon>?,
    interactive: Boolean,
    filledLabels: Boolean,
    fitHint: String,
    modifier: Modifier = Modifier,
    camera: MapCamera? = null,
    onCamera: (MapCamera) -> Unit = {},
) {
    val density = LocalDensity.current.density
    val tile = 256f * density // tile edge in pixels; scaled so map text stays readable on HiDPI screens
    val fitPoints = path ?: markers.map { it.pos }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(fitPoints) { mutableStateOf(camera?.zoom ?: -1) }
    // Map centre in "world pixels" at the current zoom.
    var cx by remember(fitPoints) { mutableStateOf(camera?.cx ?: 0.0) }
    var cy by remember(fitPoints) { mutableStateOf(camera?.cy ?: 0.0) }
    var scrollAcc by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val primary = MaterialTheme.colorScheme.primary
    val mapBg = MaterialTheme.colorScheme.surfaceVariant
    val labelStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp)

    // Keeps the world filling the view vertically: no empty band above the Arctic or below the Antarctic.
    fun minZoomForHeight(): Int {
        var z = MIN_ZOOM
        while (z < MAX_ZOOM && tile * 2.0.pow(z) < size.height) z++
        return z
    }

    fun clampY() {
        val world = tile * 2.0.pow(zoom)
        val half = size.height / 2.0
        cy = if (world <= size.height) world / 2 else cy.coerceIn(half, world - half)
    }

    fun fit() {
        if (size.width == 0 || fitPoints.isEmpty()) return
        val minX = fitPoints.minOf { unitX(it.lon) }; val maxX = fitPoints.maxOf { unitX(it.lon) }
        val minY = fitPoints.minOf { unitY(it.lat) }; val maxY = fitPoints.maxOf { unitY(it.lat) }
        // A single point has no extent: show it at a regional zoom instead of the closest one.
        var z = if (fitPoints.size == 1) 6 else MAX_ZOOM
        val floor = minZoomForHeight()
        while (z > floor) {
            val scale = tile * 2.0.pow(z)
            if ((maxX - minX) * scale <= size.width * 0.72 && (maxY - minY) * scale <= size.height * 0.62) break
            z--
        }
        val scale = tile * 2.0.pow(z)
        zoom = z
        cx = (minX + maxX) / 2 * scale
        cy = (minY + maxY) / 2 * scale - size.height * 0.04 // a little extra room above for the labels
        clampY()
    }

    fun zoomBy(dz: Int, anchor: Offset) {
        val nz = (zoom + dz).coerceIn(minZoomForHeight(), MAX_ZOOM)
        if (nz == zoom) return
        val f = 2.0.pow(nz - zoom)
        val wx = cx - size.width / 2 + anchor.x
        val wy = cy - size.height / 2 + anchor.y
        cx = wx * f - anchor.x + size.width / 2
        cy = wy * f - anchor.y + size.height / 2
        zoom = nz
        clampY()
    }

    fun screen(p: LatLon): Offset {
        val scale = tile * (1 shl zoom)
        return Offset((unitX(p.lon) * scale - (cx - size.width / 2)).toFloat(), (unitY(p.lat) * scale - (cy - size.height / 2)).toFloat())
    }

    LaunchedEffect(size, fitPoints) { if (zoom < 0) fit() }
    LaunchedEffect(zoom, cx, cy) { if (zoom >= 0) onCamera(MapCamera(zoom, cx, cy)) }

    // Ask for every visible tile; downloads outlive panning so they are not restarted on each move.
    LaunchedEffect(zoom, cx, cy, size) {
        if (zoom < 0 || size.width == 0) return@LaunchedEffect
        val n = 1 shl zoom
        val ox = cx - size.width / 2; val oy = cy - size.height / 2
        val tx0 = floor(ox / tile).toInt(); val tx1 = floor((ox + size.width) / tile).toInt()
        val ty0 = floor(oy / tile).toInt().coerceAtLeast(0); val ty1 = floor((oy + size.height) / tile).toInt().coerceAtMost(n - 1)
        for (ty in ty0..ty1) for (tx in tx0..tx1) {
            val wx = ((tx % n) + n) % n
            scope.launch { Tiles.request(zoom, wx, ty) }
        }
    }

    var m = modifier.clipToBounds().background(mapBg).onSizeChanged { size = it }
    if (interactive) {
        m = m.pointerInput(Unit) {
            detectDragGestures { change, drag ->
                change.consume()
                cx -= drag.x
                cy -= drag.y
                clampY()
            }
        }.onPointerEvent(PointerEventType.Scroll) { e ->
            val c = e.changes.first()
            scrollAcc += c.scrollDelta.y
            if (abs(scrollAcc) >= 1f) {
                zoomBy(if (scrollAcc < 0) 1 else -1, c.position)
                scrollAcc = 0f
            }
        }.pointerInput(markers) {
            // A click on a dot or its label; the topmost (last drawn) marker wins.
            detectTapGestures { tap ->
                if (zoom < 0) return@detectTapGestures
                val hit = markers.lastOrNull { mk ->
                    if (mk.onClick == null) return@lastOrNull false
                    val s = screen(mk.pos)
                    val w = measurer.measure(mk.label, labelStyle).size
                    val pad = 5.dp.toPx()
                    val labelTop = s.y - 14.dp.toPx() - w.height - pad / 2
                    (tap - s).getDistance() <= 12.dp.toPx() ||
                        (tap.x in (s.x - w.width / 2f - pad)..(s.x + w.width / 2f + pad) && tap.y in labelTop..(s.y - 14.dp.toPx() + pad / 2))
                }
                hit?.onClick?.invoke()
            }
        }
    }

    Box(m) {
        Canvas(Modifier.fillMaxSize()) {
            if (zoom < 0) return@Canvas
            val n = 1 shl zoom
            val ox = cx - size.width / 2; val oy = cy - size.height / 2
            val tx0 = floor(ox / tile).toInt(); val tx1 = floor((ox + size.width) / tile).toInt()
            val ty0 = floor(oy / tile).toInt().coerceAtLeast(0); val ty1 = floor((oy + size.height) / tile).toInt().coerceAtMost(n - 1)
            val edge = kotlin.math.ceil(tile).toInt() + 1
            for (ty in ty0..ty1) for (tx in tx0..tx1) {
                val img = Tiles.images["$zoom/${((tx % n) + n) % n}/$ty"] ?: continue
                drawImage(
                    img,
                    dstOffset = IntOffset((tx * tile - ox).toInt(), (ty * tile - oy).toInt()),
                    dstSize = IntSize(edge, edge),
                )
            }

            if (path != null) {
                val line = Path()
                path.forEachIndexed { i, p -> val s = screen(p); if (i == 0) line.moveTo(s.x, s.y) else line.lineTo(s.x, s.y) }
                drawPath(line, Color.White.copy(alpha = 0.85f), style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(line, primary, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            markers.forEach { mk ->
                val s = screen(mk.pos)
                val r = if (mk.home) 10.dp.toPx() else 6.5.dp.toPx()
                drawCircle(Color.White, radius = r + 2.5.dp.toPx(), center = s)
                drawCircle(mk.color, radius = r, center = s)
                if (mk.home) {
                    drawCircle(Color.White, radius = r * 0.62f, center = s)
                    drawCircle(mk.color, radius = r * 0.34f, center = s)
                }
                if (mk.label.isNotBlank()) {
                    val text = measurer.measure(mk.label, labelStyle.copy(color = if (filledLabels) Color.White else Color(0xFF14202B)))
                    val pad = 5.dp.toPx()
                    val tl = Offset(s.x - text.size.width / 2f, s.y - 14.dp.toPx() - text.size.height)
                    drawRoundRect(
                        if (filledLabels) mk.color else Color.White.copy(alpha = 0.92f),
                        topLeft = tl - Offset(pad, pad / 2),
                        size = androidx.compose.ui.geometry.Size(text.size.width + 2 * pad, text.size.height + pad),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                    )
                    drawText(text, topLeft = tl)
                }
            }
        }
        Text(
            "© участники OpenStreetMap",
            fontSize = 11.sp,
            color = Color(0xFF14202B),
            modifier = Modifier.align(Alignment.BottomEnd).background(Color.White.copy(alpha = 0.8f)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
        if (interactive) {
            Column(Modifier.align(Alignment.CenterEnd).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                MapButton({ zoomBy(1, center) }) { Icon(Icons.Filled.Add, "Приблизить") }
                MapButton({ zoomBy(-1, center) }) { Icon(Icons.Filled.Remove, "Отдалить") }
                MapButton({ fit() }) { Icon(Icons.Filled.CenterFocusStrong, fitHint) }
            }
        }
    }
}

@Composable
private fun MapButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(12.dp)) { content() }
}
