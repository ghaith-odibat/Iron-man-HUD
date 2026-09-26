package com.ghaith.ironhud.ui.plane

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaith.ironhud.plane.Enu
import com.ghaith.ironhud.plane.Geo
import com.ghaith.ironhud.plane.Projector
import com.ghaith.ironhud.plane.ScreenPoint
import com.ghaith.ironhud.ui.theme.Hud
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * When watching a far-away place, the camera shows the wrong world, so this paints a HUD sky
 * instead: a horizon, altitude rings, compass spokes and a faint ground grid — all following the
 * way the tablet is pointed.
 */
@Composable
fun VirtualSky(scene: PlaneScene, modifier: Modifier = Modifier, animate: Boolean = true) {
    val tm = rememberTextMeasurer(cacheSize = 32)
    val frame by if (animate) produceState(0L) { while (true) withFrameMillis { value = it } }
    else remember { androidx.compose.runtime.mutableLongStateOf(0L) }

    Canvas(modifier) {
        if (frame < 0) return@Canvas
        drawRect(Hud.Black)
        drawRect(Brush.verticalGradient(listOf(Hud.blue(0.10f), Hud.blue(0.02f))))
        val rot = scene.sensors.rot ?: return@Canvas
        val rotation = scene.sensors.displayRotation
        val decl = scene.airspace.declinationDeg
        val fView = Projector.fViewPx(scene.optics.focalMm, scene.optics.sensorWmm, scene.optics.sensorHmm,
            size.width.toInt(), size.height.toInt(), scene.zoom)
        val cx = size.width / 2
        val cy = size.height / 2

        fun project(azDeg: Double, elDeg: Double): ScreenPoint? {
            val az = Geo.rad(azDeg)
            val el = Geo.rad(elDeg)
            val v = Enu(sin(az) * cos(el), cos(az) * cos(el), sin(el)) * 100_000.0
            return Projector.project(Geo.toMagnetic(v, decl), rot, rotation, fView, cx, cy)
        }

        fun ring(elDeg: Double, alpha: Float, width: Float, dashed: Boolean) {
            polyline((0..360 step 3).map { project(it.toDouble(), elDeg) }, alpha, width, dashed)
        }

        // Ground: faint rings below the horizon.
        for (el in listOf(-4.0, -9.0, -18.0, -35.0)) ring(el, 0.12f, 1.dp.toPx(), dashed = true)
        // Sky: altitude rings.
        for (el in listOf(15.0, 30.0, 45.0, 60.0, 75.0)) {
            ring(el, 0.22f, 1.dp.toPx(), dashed = true)
            project(0.0 + scene.headingDeg(), el)?.let { p ->
                val m = tm.measure("${el.toInt()}°", Hud.text(9.sp, alpha = 0.45f, glow = false))
                drawText(m, topLeft = Offset(p.x + 4.dp.toPx(), p.y - m.size.height - 2.dp.toPx()))
            }
        }
        // Compass spokes up to the zenith, labelled at the horizon.
        for (az in 0 until 360 step 30) {
            polyline((0..88 step 4).map { project(az.toDouble(), it.toDouble()) }, if (az % 90 == 0) 0.35f else 0.18f,
                1.dp.toPx(), dashed = false)
            project(az.toDouble(), 1.5)?.let { p -> label(tm, az, p) }
        }
        // The horizon itself.
        ring(0.0, 0.85f, 2.dp.toPx(), dashed = false)
        project(0.0, 90.0)?.let { z ->
            drawCircle(Hud.blue(0.6f), 4.dp.toPx(), Offset(z.x, z.y), style = Stroke(1.5.dp.toPx()))
            val m = tm.measure("ZENITH", Hud.text(9.sp, alpha = 0.6f, glow = false))
            drawText(m, topLeft = Offset(z.x - m.size.width / 2f, z.y + 6.dp.toPx()))
        }
    }
}

private fun DrawScope.label(tm: TextMeasurer, az: Int, p: ScreenPoint) {
    val text = when (az) {
        0 -> "N"
        90 -> "E"
        180 -> "S"
        270 -> "W"
        else -> az.toString().padStart(3, '0')
    }
    val cardinal = az % 90 == 0
    val m = tm.measure(text, Hud.text(if (cardinal) 15.sp else 10.sp, alpha = if (cardinal) 1f else 0.6f,
        weight = if (cardinal) FontWeight.Bold else FontWeight.Normal, glow = cardinal))
    drawText(m, topLeft = Offset(p.x - m.size.width / 2f, p.y - m.size.height - 4.dp.toPx()))
}

/** Joins projected points, breaking the line where points go behind the camera or jump across the view. */
private fun DrawScope.polyline(points: List<ScreenPoint?>, alpha: Float, width: Float, dashed: Boolean) {
    val path = Path()
    var prev: ScreenPoint? = null
    val maxJump = size.maxDimension * 1.5f
    for (p in points) {
        if (p == null || abs(p.x) > 1e5f || abs(p.y) > 1e5f) {
            prev = null
            continue
        }
        val q = prev
        if (q == null || hypot(p.x - q.x, p.y - q.y) > maxJump) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        prev = p
    }
    drawPath(
        path, Hud.blue(alpha),
        style = Stroke(width, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null),
    )
}
