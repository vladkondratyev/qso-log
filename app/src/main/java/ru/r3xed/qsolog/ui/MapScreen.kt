package ru.r3xed.qsolog.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import ru.r3xed.qsolog.AppViewModel
import ru.r3xed.qsolog.Screen
import ru.r3xed.qsolog.data.Qso

/** One point per callsign: the most recent contact and how many there were. */
private class Station(val latest: Qso, val count: Int)

/** Map of every station in the log. Tapping a point opens the latest contact with that callsign. */
@Composable
fun MapScreen(vm: AppViewModel) {
    BackHandler { vm.screen = Screen.Log }
    val x = LocalExtra.current
    val stations = remember(vm.allQsos) {
        vm.allQsos.filter { it.lat != null && it.lon != null }
            .groupBy { it.call }
            .map { (_, list) -> Station(list.maxBy { it.timeUtc }, list.size) }
    }
    val withoutPosition = remember(vm.allQsos) { vm.allQsos.filter { it.lat == null || it.lon == null }.map { it.call }.distinct().size }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.screen = Screen.Log }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", Modifier.size(30.dp))
            }
            Column {
                Text("Карта связей", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "позывных на карте: ${stations.size}" + if (withoutPosition > 0) " · без координат: $withoutPosition" else "",
                    style = MaterialTheme.typography.bodyMedium, color = x.muted,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (stations.isEmpty()) {
                Text(
                    "В логе пока нет связей с известным QTH. Точки появятся, когда QRZ.ru вернёт координаты абонента или вы впишете его локатор.",
                    style = MaterialTheme.typography.bodyLarge, color = x.muted,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            } else {
                StationsMap(vm, stations)
            }
        }
    }
}

@Composable
private fun StationsMap(vm: AppViewModel, stations: List<Station>) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val dark = MaterialTheme.colorScheme.onBackground.toArgb()
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            minZoomLevel = 2.0
            fillViewport()
            outlineProvider = android.view.ViewOutlineProvider.BOUNDS
            clipToOutline = true
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose {
            // Remember the view so returning from a card shows the same place.
            vm.mapPosition = Triple(map.mapCenter.latitude, map.mapCenter.longitude, map.zoomLevelDouble)
            map.onPause()
            map.onDetach()
        }
    }
    AndroidView(factory = { map }, modifier = Modifier.fillMaxSize().clipToBounds(), update = { v ->
        v.overlays.clear()
        val points = mutableListOf<GeoPoint>()
        vm.settings.myPosition?.let { me ->
            val p = GeoPoint(me.lat, me.lon)
            points += p
            v.overlays.add(labelMarker(v, p, vm.settings.myCall.ifBlank { "Я" }, dark) { true })
        }
        stations.forEach { st ->
            val q = st.latest
            val p = GeoPoint(q.lat!!, q.lon!!)
            points += p
            val label = if (st.count > 1) "${q.call} ×${st.count}" else q.call
            v.overlays.add(labelMarker(v, p, label, accent) { vm.edit(q, from = Screen.Map); true })
        }
        val saved = vm.mapPosition
        if (saved != null) {
            v.controller.setZoom(saved.third)
            v.controller.setCenter(GeoPoint(saved.first, saved.second))
        } else if (points.size == 1) {
            v.controller.setZoom(6.0)
            v.controller.setCenter(points[0])
        } else {
            val box = BoundingBox.fromGeoPointsSafe(points).increaseByScale(1.3f)
            v.addOnFirstLayoutListener { _, _, _, _, _ -> v.zoomToBoundingBox(box, false) }
            if (v.width > 0) v.zoomToBoundingBox(box, false)
        }
        v.invalidate()
    })
}

private fun labelMarker(map: MapView, p: GeoPoint, label: String, color: Int, onClick: () -> Boolean) =
    Marker(map).apply {
        position = p
        title = label
        val (drawable, dotY) = labelIcon(map.context, label, color)
        icon = drawable
        // Anchor on the dot, not on the label, so the point is exactly on the QTH.
        setAnchor(Marker.ANCHOR_CENTER, dotY)
        setOnMarkerClickListener { _, _ -> onClick() }
        setInfoWindow(null)
    }

/**
 * Callsign in a rounded label above a dot, drawn large enough to read outdoors.
 * Returns the icon and the dot's vertical position as a fraction of the icon height.
 */
private fun labelIcon(context: Context, text: String, color: Int): Pair<BitmapDrawable, Float> {
    val d = context.resources.displayMetrics.density
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        textSize = 16 * d
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val padH = 8 * d
    val padV = 5 * d
    val textW = textPaint.measureText(text)
    val fm = textPaint.fontMetrics
    val labelH = (fm.descent - fm.ascent) + 2 * padV
    val gap = 4 * d
    val dotR = 7 * d
    val w = (textW + 2 * padH).coerceAtLeast(2 * dotR + 4 * d)
    val h = labelH + gap + 2 * dotR + 2 * d
    val bmp = Bitmap.createBitmap(w.toInt() + 2, h.toInt() + 2, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE }
    c.drawRoundRect(RectF(1f, 1f, w, labelH), 8 * d, 8 * d, fill)
    c.drawText(text, 1f + (w - textW) / 2, 1f + padV - fm.ascent, textPaint)
    val cy = labelH + gap + dotR
    c.drawCircle(w / 2 + 1f, cy, dotR + 2 * d, white)
    c.drawCircle(w / 2 + 1f, cy, dotR, fill)
    return BitmapDrawable(context.resources, bmp) to cy / bmp.height
}
