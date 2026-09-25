package ru.r3xed.qsolog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
                    conn.setRequestProperty("User-Agent", "QSOLog/1.1 (+https://github.com/vladkondratyev/qso-log)")
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

/**
 * Map with my station, the other station and the great-circle path.
 * [interactive]: drag to pan, wheel to zoom, +/- buttons. Otherwise a static preview.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TileMap(
    me: LatLon,
    them: LatLon,
    myLabel: String,
    theirLabel: String,
    interactive: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val tile = 256f * density // tile edge in pixels; scaled so map text stays readable on HiDPI screens
    val path = remember(me, them) { unwrapped(Geo.greatCircle(me, them, 128)) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(me, them) { mutableStateOf(-1) }
    // Map centre in "world pixels" at the current zoom.
    var cx by remember(me, them) { mutableStateOf(0.0) }
    var cy by remember(me, them) { mutableStateOf(0.0) }
    var scrollAcc by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val primary = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val mapBg = MaterialTheme.colorScheme.surfaceVariant

    fun fit() {
        if (size.width == 0) return
        val minX = path.minOf { unitX(it.lon) }; val maxX = path.maxOf { unitX(it.lon) }
        val minY = path.minOf { unitY(it.lat) }; val maxY = path.maxOf { unitY(it.lat) }
        var z = MAX_ZOOM
        while (z > MIN_ZOOM) {
            val scale = tile * 2.0.pow(z)
            if ((maxX - minX) * scale <= size.width * 0.72 && (maxY - minY) * scale <= size.height * 0.62) break
            z--
        }
        val scale = tile * 2.0.pow(z)
        zoom = z
        cx = (minX + maxX) / 2 * scale
        cy = (minY + maxY) / 2 * scale - size.height * 0.04 // a little extra room above for the labels
    }

    fun zoomBy(dz: Int, anchor: Offset) {
        val nz = (zoom + dz).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (nz == zoom) return
        val f = 2.0.pow(nz - zoom)
        val wx = cx - size.width / 2 + anchor.x
        val wy = cy - size.height / 2 + anchor.y
        cx = wx * f - anchor.x + size.width / 2
        cy = wy * f - anchor.y + size.height / 2
        zoom = nz
    }

    LaunchedEffect(size, me, them) { if (zoom < 0) fit() }

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
            }
        }.onPointerEvent(PointerEventType.Scroll) { e ->
            val c = e.changes.first()
            scrollAcc += c.scrollDelta.y
            if (abs(scrollAcc) >= 1f) {
                zoomBy(if (scrollAcc < 0) 1 else -1, c.position)
                scrollAcc = 0f
            }
        }
    }

    Box(m) {
        Canvas(Modifier.fillMaxSize()) {
            if (zoom < 0) return@Canvas
            val n = 1 shl zoom
            val scale = tile * n
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
            fun screen(p: LatLon) = Offset((unitX(p.lon) * scale - ox).toFloat(), (unitY(p.lat) * scale - oy).toFloat())

            val line = Path()
            path.forEachIndexed { i, p -> val s = screen(p); if (i == 0) line.moveTo(s.x, s.y) else line.lineTo(s.x, s.y) }
            drawPath(line, Color.White.copy(alpha = 0.85f), style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(line, primary, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            listOf(Triple(path.first(), myLabel, ink), Triple(path.last(), theirLabel, primary)).forEach { (p, label, color) ->
                val s = screen(p)
                drawCircle(Color.White, radius = 9.dp.toPx(), center = s)
                drawCircle(color, radius = 6.5.dp.toPx(), center = s)
                if (label.isNotBlank()) {
                    val text = measurer.measure(label, TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF14202B)))
                    val pad = 5.dp.toPx()
                    val tl = Offset(s.x - text.size.width / 2f, s.y - 14.dp.toPx() - text.size.height)
                    drawRoundRect(
                        Color.White.copy(alpha = 0.92f),
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
                MapButton({ fit() }) { Icon(Icons.Filled.CenterFocusStrong, "Показать всю трассу") }
            }
        }
    }
}

@Composable
private fun MapButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(12.dp)) { content() }
}
