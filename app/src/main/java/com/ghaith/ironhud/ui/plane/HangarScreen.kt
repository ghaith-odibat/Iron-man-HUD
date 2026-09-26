package com.ghaith.ironhud.ui.plane

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaith.ironhud.plane.AircraftModels
import com.ghaith.ironhud.plane.Lod
import com.ghaith.ironhud.plane.ModelKind
import com.ghaith.ironhud.plane.models.AircraftTypes
import com.ghaith.ironhud.plane.models.Airframe
import com.ghaith.ironhud.ui.hud.HudButton
import com.ghaith.ironhud.ui.theme.Hud
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The holo hangar: one aircraft type at a time as a large wireframe you can turn (drag), zoom
 * (pinch) and reset (double-tap), with its spec sheet; every type in the catalogue can be browsed.
 */
@Composable
fun HangarScreen(
    startId: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val catalog = AircraftTypes.catalog
    var index by remember { mutableIntStateOf(catalog.indexOfFirst { it.id == startId }.coerceAtLeast(0)) }
    var yaw by remember { mutableFloatStateOf(DEFAULT_YAW) }
    var pitch by remember { mutableFloatStateOf(DEFAULT_PITCH) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var touchedAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    val airframe = catalog[index]

    // Slow turntable spin, paused for a moment after the user turns the model by hand.
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var last = 0L
        while (true) withFrameMillis { t ->
            if (last != 0L && t - touchedAt > 2_500) yaw += (t - last) * 0.012f
            last = t
            now = t
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Hud.Black)
            .background(Brush.verticalGradient(listOf(Hud.blue(0.10f), Hud.blue(0.02f), Hud.blue(0.06f))))
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        val landscape = maxWidth > maxHeight
        val turn: (Float, Float, Float) -> Unit = { dYaw, dPitch, dZoom ->
            yaw += dYaw
            pitch = (pitch + dPitch).coerceIn(-80f, 89f)
            zoom = (zoom * dZoom).coerceIn(0.5f, 4f)
            touchedAt = now
        }
        val reset = {
            yaw = DEFAULT_YAW
            pitch = DEFAULT_PITCH
            zoom = 1f
        }
        Column(Modifier.fillMaxSize()) {
            Header(index, catalog.size, onClose)
            if (landscape) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    ModelView(airframe, yaw, pitch, zoom, now, Modifier.weight(0.62f).fillMaxHeight(), turn, reset)
                    SpecSheet(airframe, Modifier.weight(0.38f).fillMaxHeight().padding(end = 24.dp, top = 8.dp, bottom = 8.dp))
                }
            } else {
                ModelView(airframe, yaw, pitch, zoom, now, Modifier.weight(0.55f).fillMaxWidth(), turn, reset)
                SpecSheet(airframe, Modifier.weight(0.45f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp))
            }
            Browser(catalog, index, animate, onSelect = { index = it })
        }
    }
}

private const val DEFAULT_YAW = 215f
private const val DEFAULT_PITCH = 18f

@Composable
private fun Header(index: Int, count: Int, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Hud.Black.copy(alpha = 0.6f)).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("◀ HUD", style = Hud.text(14.sp, weight = FontWeight.Bold),
            modifier = Modifier.clickable(onClick = onClose).padding(end = 18.dp, top = 6.dp, bottom = 6.dp))
        BasicText("◈ HOLO HANGAR", style = Hud.text(22.sp, weight = FontWeight.Bold, spacing = 3.sp), modifier = Modifier.weight(1f))
        BasicText(
            "TYPE ${index + 1} / $count",
            style = Hud.text(12.sp, alpha = 0.75f, spacing = 1.5.sp),
        )
    }
}

@Composable
private fun ModelView(
    airframe: Airframe,
    yaw: Float,
    pitch: Float,
    zoom: Float,
    timeMs: Long,
    modifier: Modifier,
    onTurn: (dYaw: Float, dPitch: Float, dZoom: Float) -> Unit,
    onReset: () -> Unit,
) {
    val painter = remember { WirePainter() }
    val turn by rememberUpdatedState(onTurn)
    val reset by rememberUpdatedState(onReset)
    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoomChange, _ -> turn(pan.x * 0.35f, pan.y * 0.25f, zoomChange) }
                }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { reset() }) },
        ) {
            val mesh = AircraftModels.mesh(airframe, Lod.DETAIL)
            val d = 3.2
            val px = min(size.width, size.height) * 0.82f * zoom
            val cx = size.width / 2
            val cy = size.height * 0.5f
            val basis = AircraftModels.viewBasis(yaw.toDouble(), pitch.toDouble(), d)
            pedestal(basis, px * d, cx, cy, floorZ(mesh.vertices))
            painter.draw(this, mesh, basis, px * d, cx, cy, timeMs / 1000.0, alpha = 1f, strokePx = 1.2.dp.toPx(), glowPx = 4.dp.toPx())
            corners()
        }
        BasicText(
            "DRAG TO ROTATE · PINCH TO ZOOM · DOUBLE-TAP TO RESET",
            style = Hud.text(10.sp, alpha = 0.5f, glow = false, spacing = 1.sp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
        )
    }
}

private fun floorZ(v: FloatArray): Double {
    var m = 0f
    for (i in 2 until v.size step 3) m = min(m, v[i])
    return m - 0.04
}

/** A holographic floor under the model: rings and spokes in model space, so it turns with it. */
private fun DrawScope.pedestal(m: DoubleArray, fView: Double, cx: Float, cy: Float, z: Double) {
    fun p(x: Double, y: Double): Offset? {
        val r = m[0] + x * m[3] + y * m[6] + z * m[9]
        val u = m[1] + x * m[4] + y * m[7] + z * m[10]
        val f = m[2] + x * m[5] + y * m[8] + z * m[11]
        if (f <= 0.05) return null
        return Offset((cx + fView * r / f).toFloat(), (cy - fView * u / f).toFloat())
    }
    for ((radius, alpha) in listOf(0.3 to 0.18f, 0.5 to 0.28f, 0.7 to 0.14f)) {
        var prev: Offset? = null
        for (i in 0..72) {
            val a = 2 * PI * i / 72
            val q = p(radius * cos(a), radius * sin(a))
            if (prev != null && q != null) drawLine(Hud.blue(alpha), prev, q, 1.dp.toPx())
            prev = q
        }
    }
    for (i in 0 until 12) {
        val a = 2 * PI * i / 12
        val a0 = p(0.3 * cos(a), 0.3 * sin(a))
        val a1 = p(0.7 * cos(a), 0.7 * sin(a))
        if (a0 != null && a1 != null) drawLine(Hud.blue(0.12f), a0, a1, 1.dp.toPx())
    }
}

private fun DrawScope.corners() {
    val len = 26.dp.toPx()
    val inset = 16.dp.toPx()
    val w = 2.dp.toPx()
    for ((x, y) in listOf(inset to inset, size.width - inset to inset, inset to size.height - inset, size.width - inset to size.height - inset)) {
        val sx = if (x < size.width / 2) 1f else -1f
        val sy = if (y < size.height / 2) 1f else -1f
        drawLine(Hud.blue(0.6f), Offset(x, y), Offset(x + sx * len, y), w)
        drawLine(Hud.blue(0.6f), Offset(x, y), Offset(x, y + sy * len), w)
    }
}

@Composable
private fun SpecSheet(a: Airframe, modifier: Modifier) {
    val shape = CutCornerShape(topEnd = 22.dp, bottomStart = 22.dp)
    Column(
        modifier
            .background(Hud.Black.copy(alpha = 0.55f), shape)
            .border(1.dp, Hud.blue(0.7f), shape)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        BasicText(a.kind.label, style = Hud.text(10.sp, alpha = 0.7f, spacing = 2.sp))
        BasicText(a.name, style = Hud.text(22.sp, weight = FontWeight.Bold, spacing = 1.sp), modifier = Modifier.padding(top = 4.dp))
        BasicText("ICAO " + a.codes.joinToString(" · "), style = Hud.text(12.sp, alpha = 0.8f, spacing = 1.5.sp),
            modifier = Modifier.padding(top = 2.dp))
        Canvas(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(2.dp)) {
            drawLine(Hud.blue(0.8f), Offset.Zero, Offset(size.width * 0.35f, 0f), size.height)
            drawLine(Hud.blue(0.25f), Offset(size.width * 0.37f, 0f), Offset(size.width, 0f), 1f)
        }
        val mesh = AircraftModels.mesh(a, Lod.DETAIL)
        val rows = buildList {
            when {
                a.rotorM != null -> {
                    add("LENGTH" to ModelFormat.metres(a.lengthM))
                    add("ROTOR Ø" to ModelFormat.metres(a.rotorM!!))
                }
                a.kind == ModelKind.BALLOON -> {
                    add("HEIGHT" to ModelFormat.metres(a.lengthM))
                    add("ENVELOPE Ø" to ModelFormat.metres(a.spanM))
                }
                else -> {
                    add("LENGTH" to ModelFormat.metres(a.lengthM))
                    add("WINGSPAN" to ModelFormat.metres(a.spanM))
                }
            }
            a.power?.let { add("POWER" to it.uppercase(Locale.US)) }
            add("HOLOGRAM" to String.format(Locale.US, "%,d LINES · %,d POINTS", (mesh.edges.size + mesh.faint.size) / 2, mesh.vertexCount))
        }
        for ((k, v) in rows) {
            Row(Modifier.padding(vertical = 3.dp)) {
                BasicText(k, style = Hud.text(10.sp, alpha = 0.55f, glow = false), modifier = Modifier.width(92.dp).padding(top = 2.dp))
                BasicText(v, style = Hud.text(13.sp, glow = false))
            }
        }
        Spacer(Modifier.height(8.dp))
        BasicText("CONFIGURATION", style = Hud.text(10.sp, alpha = 0.55f, glow = false, spacing = 1.5.sp))
        for (f in a.features) {
            Row(Modifier.padding(top = 3.dp)) {
                BasicText("▸ ", style = Hud.text(12.sp))
                BasicText(f, style = Hud.text(12.sp, alpha = 0.9f, glow = false))
            }
        }
    }
}

@Composable
private fun Browser(catalog: List<Airframe>, index: Int, animate: Boolean, onSelect: (Int) -> Unit) {
    val list = rememberLazyListState()
    LaunchedEffect(index) {
        val target = (index - 3).coerceAtLeast(0)
        if (animate) list.animateScrollToItem(target) else list.scrollToItem(target)
    }
    Row(
        Modifier.fillMaxWidth().background(Hud.Black.copy(alpha = 0.6f)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HudButton("◀ PREV", active = true, onClick = { onSelect((index - 1 + catalog.size) % catalog.size) })
        LazyRow(state = list, modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(catalog, key = { _, a -> a.id }) { i, a ->
                val on = i == index
                BasicText(
                    a.id,
                    style = Hud.text(12.sp, alpha = if (on) 1f else 0.6f, weight = if (on) FontWeight.Bold else FontWeight.Normal, glow = on),
                    modifier = Modifier
                        .border(if (on) 2.dp else 1.dp, Hud.blue(if (on) 1f else 0.35f))
                        .background(Hud.blue(if (on) 0.22f else 0.04f))
                        .clickable { onSelect(i) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        HudButton("NEXT ▶", active = true, onClick = { onSelect((index + 1) % catalog.size) })
    }
}
