package com.ghaith.ironhud.ui.plane

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Point
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ghaith.ironhud.plane.GeoPoint
import com.ghaith.ironhud.plane.ObserverParsers
import com.ghaith.ironhud.plane.ObserverPlace
import com.ghaith.ironhud.ui.hud.HudButton
import com.ghaith.ironhud.ui.theme.Hud
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Overlay
import java.io.File
import org.osmdroid.util.GeoPoint as OsmPoint

/**
 * Plane Mode's observer picker: a world map (OpenStreetMap, rendered in HUD blue) — tap anywhere to
 * watch the sky from there — or go back to the tablet's own GPS position.
 */
@Composable
fun LocationPickerScreen(
    current: ObserverPlace?,
    gps: GeoPoint?,
    busy: Boolean,
    onPick: (lat: Double, lon: Double) -> Unit,
    onUseMyLocation: () -> Unit,
    onClose: () -> Unit,
    /** The map itself; replaced by a placeholder in screenshot tests (osmdroid needs live tiles). */
    map: @Composable (pin: Pair<Double, Double>?, gps: GeoPoint?, onTap: (Double, Double) -> Unit, modifier: Modifier) -> Unit =
        { pin, g, onTap, modifier -> OsmMap(pin, g, onTap, modifier) },
) {
    var pin by remember { mutableStateOf(current?.let { it.lat to it.lon }) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Hud.Black)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        map(pin, gps, { lat, lon -> pin = lat to lon }, Modifier.fillMaxSize())

        // Header
        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Hud.Black.copy(alpha = 0.7f))
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("◀ HUD", style = Hud.text(14.sp, weight = FontWeight.Bold),
                    modifier = Modifier.clickable(onClick = onClose).padding(end = 18.dp, top = 6.dp, bottom = 6.dp))
                BasicText("OBSERVER LOCATION", style = Hud.text(22.sp, weight = FontWeight.Bold, spacing = 3.sp))
            }
            BasicText(
                "Tap anywhere on the map to watch the sky from there, or use your own position.",
                style = Hud.text(12.sp, alpha = 0.7f, glow = false),
                modifier = Modifier.padding(top = 4.dp),
            )
            BasicText(
                "NOW: " + (current?.name?.uppercase() ?: "MY LOCATION (GPS)"),
                style = Hud.text(11.sp, alpha = 0.85f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // Footer: selection + actions
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Hud.Black.copy(alpha = 0.72f))
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            val p = pin
            BasicText(
                when {
                    busy -> "LOCATING…"
                    p != null -> "PIN · " + ObserverParsers.coordLabel(p.first, p.second)
                    else -> "NO PIN · TAP THE MAP"
                },
                style = Hud.text(13.sp, weight = FontWeight.Bold),
            )
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .border(2.dp, Hud.blue(if (p != null && !busy) 1f else 0.4f))
                        .background(Hud.blue(if (p != null && !busy) 0.25f else 0.05f))
                        .clickable(enabled = p != null && !busy) { p?.let { onPick(it.first, it.second) } }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    BasicText("WATCH FROM HERE", style = Hud.text(13.sp, weight = FontWeight.Bold, alpha = if (p != null) 1f else 0.5f))
                }
                Box(
                    Modifier
                        .border(1.dp, Hud.blue(0.8f))
                        .clickable(enabled = !busy, onClick = onUseMyLocation)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    BasicText("◎ USE MY CURRENT LOCATION", style = Hud.text(13.sp, weight = FontWeight.Bold))
                }
                HudButton("CLOSE", active = false, onClick = onClose, modifier = Modifier.width(74.dp))
            }
            BasicText(
                "MAP © OPENSTREETMAP CONTRIBUTORS · PLACE NAMES PHOTON · ELEVATION OPEN-METEO",
                style = Hud.text(9.sp, alpha = 0.5f, glow = false),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** osmdroid map with HUD-blue tiles, a pin with the 50 km scan radius, and a dot for your GPS position. */
@Composable
fun OsmMap(pin: Pair<Double, Double>?, gps: GeoPoint?, onTap: (Double, Double) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestTap by rememberUpdatedState(onTap)
    val marks = remember { PinOverlay() }
    val mapView = remember {
        configureOsmdroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 2.0
            maxZoomLevel = 18.0
            setTilesScaledToDpi(true)
            overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(NIGHT_HUD_MATRIX))
            overlayManager.tilesOverlay.setLoadingBackgroundColor(android.graphics.Color.BLACK)
            overlayManager.tilesOverlay.setLoadingLineColor(android.graphics.Color.argb(60, 0x66, 0xD9, 0xFF))
            val start = pin ?: gps?.let { it.lat to it.lon }
            controller.setZoom(if (start != null) 9.0 else 3.0)
            controller.setCenter(if (start != null) OsmPoint(start.first, start.second) else OsmPoint(30.0, 20.0))
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: OsmPoint): Boolean {
                    latestTap(p.latitude, p.longitude)
                    return true
                }

                override fun longPressHelper(p: OsmPoint): Boolean = false
            }))
            overlays.add(marks)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = {
            marks.pin = pin
            marks.gps = gps
            it.invalidate()
        },
    )
}

private fun configureOsmdroid(context: Context) {
    val config = Configuration.getInstance()
    config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    // OpenStreetMap's tile policy asks apps to identify themselves; tiles are cached privately.
    config.userAgentValue = context.packageName
    config.osmdroidBasePath = File(context.cacheDir, "osmdroid")
    config.osmdroidTileCache = File(config.osmdroidBasePath, "tiles")
}

/** Inverts the map's brightness and maps it onto HUD blue: dark land, glowing roads and labels. */
private val NIGHT_HUD_MATRIX: FloatArray = run {
    val (r, g, b) = Triple(0x66 / 255f, 0xD9 / 255f, 1f)
    val k = 0.95f
    fun row(c: Float) = floatArrayOf(-0.299f * c * k, -0.587f * c * k, -0.114f * c * k, 0f, 255f * c * k)
    row(r) + row(g) + row(b) + floatArrayOf(0f, 0f, 0f, 1f, 0f)
}

/** Draws the chosen pin (with the 50 km radius Plane Mode scans) and the GPS position. */
private class PinOverlay : Overlay() {
    var pin: Pair<Double, Double>? = null
    var gps: GeoPoint? = null
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = android.graphics.Color.rgb(0x66, 0xD9, 0xFF)
        strokeWidth = 5f
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.argb(40, 0x66, 0xD9, 0xFF)
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.rgb(0x66, 0xD9, 0xFF)
    }
    private val pt = Point()

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow) return
        val proj = osmv.projection
        gps?.let { g ->
            proj.toPixels(OsmPoint(g.lat, g.lon), pt)
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 14f, stroke)
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 6f, dot)
        }
        pin?.let { (lat, lon) ->
            proj.toPixels(OsmPoint(lat, lon), pt)
            val x = pt.x.toFloat()
            val y = pt.y.toFloat()
            val radiusPx = proj.metersToPixels(50_000f)
            c.drawCircle(x, y, radiusPx, fill)
            c.drawCircle(x, y, radiusPx, stroke)
            c.drawLine(x - 30f, y, x - 8f, y, stroke)
            c.drawLine(x + 8f, y, x + 30f, y, stroke)
            c.drawLine(x, y - 30f, x, y - 8f, stroke)
            c.drawLine(x, y + 8f, x, y + 30f, stroke)
            c.drawCircle(x, y, 5f, dot)
        }
    }
}
