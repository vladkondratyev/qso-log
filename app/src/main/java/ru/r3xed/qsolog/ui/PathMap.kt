package ru.r3xed.qsolog.ui

import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.ln
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.LatLon

/**
 * OpenStreetMap view with my station, the other station and the great-circle path between them.
 * With [interactive] = false it is a static preview (touches are handled by the caller).
 */
@Composable
fun PathMap(
    me: LatLon,
    them: LatLon,
    myLabel: String,
    theirLabel: String,
    interactive: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lineColor = 0xFF0A5C8A.toInt() // tiles are light in both themes
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(interactive)
            zoomController.setVisibility(
                if (interactive) org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT
                else org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
            )
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            minZoomLevel = 2.0
            fillViewport()
            // Without this the map paints tiles outside its bounds when it sits inside a scrolling column.
            outlineProvider = android.view.ViewOutlineProvider.BOUNDS
            clipToOutline = true
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose { map.onPause(); map.onDetach() }
    }
    AndroidView(factory = { map }, modifier = modifier.clipToBounds(), update = { v ->
        v.overlays.clear()
        val points = Geo.greatCircle(me, them).map { GeoPoint(it.lat, it.lon) }
        v.overlays.add(Polyline(v).apply {
            setPoints(points)
            outlinePaint.color = lineColor
            outlinePaint.strokeWidth = 9f
            outlinePaint.strokeCap = Paint.Cap.ROUND
        })
        listOf(me to myLabel, them to theirLabel).forEach { (p, label) ->
            v.overlays.add(Marker(v).apply {
                position = GeoPoint(p.lat, p.lon)
                title = label
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                if (!interactive) setInfoWindow(null)
            })
        }
        val box = BoundingBox.fromGeoPointsSafe(points)
        val padded = box.increaseByScale(1.35f)
        v.addOnFirstLayoutListener { _, _, _, _, _ -> v.zoomToBoundingBox(padded, false) }
        if (v.width > 0) v.zoomToBoundingBox(padded, false)
        v.invalidate()
    })
}

/**
 * Map text readable on dense screens, and no zooming out past the point where the world
 * is shorter than the view (which left an empty band above the map).
 */
internal fun MapView.fillViewport() {
    isTilesScaledToDpi = true
    val tiles = MapView.getTileSystem()
    setScrollableAreaLimitLatitude(tiles.maxLatitude, tiles.minLatitude, 0)
    addOnFirstLayoutListener { _, _, _, _, _ -> clampMinZoom() }
    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> clampMinZoom() }
}

private fun MapView.clampMinZoom() {
    if (height <= 0) return
    val tile = 256.0 * resources.displayMetrics.density
    minZoomLevel = maxOf(2.0, ln(height / tile) / ln(2.0))
    if (zoomLevelDouble < minZoomLevel) controller.setZoom(minZoomLevel)
}
