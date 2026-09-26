package com.ghaith.ironhud.ui.plane

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaith.ironhud.plane.Geo
import com.ghaith.ironhud.plane.Projector
import com.ghaith.ironhud.ui.theme.Hud
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

/**
 * Heading-up radar: you at the centre, your camera's field of view as a wedge, every tracked
 * aircraft as a blip with a tick showing where it's heading. Rings at 10 / 25 / 50 km.
 */
@Composable
fun RadarScope(
    scene: PlaneScene,
    viewW: Int,
    viewH: Int,
    modifier: Modifier = Modifier,
    diameter: Dp = 190.dp,
    animate: Boolean = true,
) {
    val tm = rememberTextMeasurer(cacheSize = 32)
    val sweep = if (animate) {
        rememberInfiniteTransition(label = "radar")
            .animateFloat(0f, 360f, infiniteRepeatable(tween(4_000, easing = LinearEasing)), label = "sweep").value
    } else 300f
    val frame by if (animate) produceState(0L) { while (true) withFrameMillis { value = it } }
    else remember { androidx.compose.runtime.mutableLongStateOf(0L) }

    Column(modifier) {
        Canvas(Modifier.size(diameter)) {
            if (frame < 0) return@Canvas
            val c = center
            val r = size.minDimension / 2 - 6.dp.toPx()
            val rot = scene.sensors.rot
            val decl = scene.airspace.declinationDeg
            // Camera heading in the true-north frame.
            val heading = rot?.let { Projector.orientation(it, scene.sensors.displayRotation, decl).headingDeg } ?: 0.0

            drawCircle(Hud.Black.copy(alpha = 0.45f), r, c)
            for (km in RINGS) drawCircle(Hud.blue(if (km == MAX_KM) 0.8f else 0.3f), r * km / MAX_KM, c, style = Stroke(1.dp.toPx()))
            drawLine(Hud.blue(0.18f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
            drawLine(Hud.blue(0.18f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())

            // Camera field of view (always straight up in a heading-up scope).
            val fView = Projector.fViewPx(scene.optics.focalMm, scene.optics.sensorWmm, scene.optics.sensorHmm, viewW, viewH, scene.zoom)
            val halfFov = Geo.deg(atan((viewW / 2.0) / fView)).toFloat()
            drawArc(
                Hud.blue(0.16f), startAngle = -90f - halfFov, sweepAngle = 2 * halfFov, useCenter = true,
                topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r),
            )

            // Sweep.
            rotate(sweep, c) {
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, Hud.blue(0.22f)), c),
                    startAngle = -60f, sweepAngle = 60f, useCenter = true,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r),
                )
                drawLine(Hud.blue(0.55f), c, Offset(c.x + r, c.y), 1.dp.toPx())
            }

            // Cardinal points.
            for ((label, bearing) in listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0)) {
                val a = Geo.rad(bearing - heading)
                val m = tm.measure(label, Hud.text(if (label == "N") 11.sp else 9.sp, weight = FontWeight.Bold, glow = false))
                val p = Offset((c.x + (r - 9.dp.toPx()) * sin(a)).toFloat(), (c.y - (r - 9.dp.toPx()) * cos(a)).toFloat())
                drawText(m, topLeft = Offset(p.x - m.size.width / 2f, p.y - m.size.height / 2f))
            }

            // Blips.
            val viewer = scene.airspace.viewer
            if (viewer != null) {
                val now = scene.clock()
                for (ac in scene.airspace.aircraft) {
                    val enu = Geo.enu(viewer, ac.positionAt(now))
                    val km = enu.horizontal / 1000
                    if (km > MAX_KM) continue
                    val a = Geo.rad(Geo.bearingDeg(enu) - heading)
                    val p = Offset((c.x + r * (km / MAX_KM) * sin(a)).toFloat(), (c.y - r * (km / MAX_KM) * cos(a)).toFloat())
                    val selected = ac.hex == scene.selectedHex
                    drawCircle(Hud.blue(if (selected) 1f else 0.85f), (if (selected) 4 else 3).dp.toPx(), p)
                    ac.trackDeg?.let { t ->
                        val ta = Geo.rad(t - heading)
                        val len = 9.dp.toPx()
                        drawLine(Hud.blue(0.8f), p, Offset(p.x + len * sin(ta).toFloat(), p.y - len * cos(ta).toFloat()), 1.5.dp.toPx())
                    }
                    if (selected) drawCircle(Hud.Blue, 8.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
                }
            }
            // You.
            drawCircle(Hud.Blue, 3.dp.toPx(), c)
        }
        BasicText(
            "RANGE ${MAX_KM.toInt()} KM · ${scene.airspace.aircraft.size} CONTACTS",
            style = Hud.text(10.sp, alpha = 0.8f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private const val MAX_KM = 50f
private val RINGS = listOf(10f, 25f, 50f)
