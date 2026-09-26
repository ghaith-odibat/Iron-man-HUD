package com.ghaith.ironhud.ui.plane

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaith.ironhud.plane.Aircraft
import com.ghaith.ironhud.plane.AircraftModels
import com.ghaith.ironhud.plane.CamVec
import com.ghaith.ironhud.plane.Enu
import com.ghaith.ironhud.plane.Geo
import com.ghaith.ironhud.plane.Projector
import com.ghaith.ironhud.ui.theme.Hud
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The AR layer of Plane Mode: every aircraft in view as a blue wireframe hologram, oriented to its
 * real track and climb and seen from where you stand, with a data tag; planes outside the view get
 * an arrow on the screen edge. Redrawn every frame (dead-reckoned between feed updates).
 */
@Composable
fun PlaneLayer(scene: PlaneScene, modifier: Modifier = Modifier, animate: Boolean = true) {
    val tm = rememberTextMeasurer(cacheSize = 96)
    val frame by if (animate) produceState(0L) { while (true) withFrameMillis { value = it } }
    else remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val path = remember { Path() }

    Canvas(modifier) {
        if (frame < 0) return@Canvas // reading the frame clock re-draws this layer every frame
        val rot = scene.sensors.rot
        val viewer = scene.airspace.viewer
        if (rot == null || viewer == null) {
            val msg = if (viewer == null) scene.airspace.status.ifBlank { "ACQUIRING GPS" } else "CALIBRATING SENSORS"
            centerText(tm, msg)
            scene.hits?.update(emptyList())
            return@Canvas
        }
        val rotation = scene.sensors.displayRotation
        val decl = scene.airspace.declinationDeg
        val fView = Projector.fViewPx(scene.optics.focalMm, scene.optics.sensorWmm, scene.optics.sensorHmm,
            size.width.toInt(), size.height.toInt(), scene.zoom)
        val cx = size.width / 2
        val cy = size.height / 2
        val now = scene.clock()

        val items = scene.airspace.aircraft.map { a ->
            val trueEnu = Geo.enu(viewer, a.positionAt(now))
            Item(a, trueEnu, Projector.toCamera(Geo.toMagnetic(trueEnu, decl), rot, rotation))
        }
        val hits = ArrayList<Pair<String, Pair<Float, Float>>>()
        val offscreen = ArrayList<Item>()
        val margin = 30.dp.toPx()

        // Far planes first so near ones draw on top.
        for (item in items.sortedByDescending { it.trueEnu.range }) {
            val sp = Projector.project(item.cam, fView, cx, cy)
            if (sp == null || sp.x < -margin || sp.x > size.width + margin || sp.y < -margin || sp.y > size.height + margin) {
                offscreen += item
                continue
            }
            val a = item.a
            val selected = a.hex == scene.selectedHex
            val distKm = item.trueEnu.horizontal / 1000
            val near = 1.0 - (distKm / 50.0).coerceIn(0.0, 1.0)
            val lengthPx = ((60 + 90 * near) * (if (selected) 1.35 else 1.0)).dp.toPx()
            val alpha = (0.45 + 0.55 * near).toFloat().coerceIn(0.45f, 1f)

            // The model is scaled up so it reads on screen, then every vertex is projected for true perspective.
            val mesh = AircraftModels.mesh(AircraftModels.kindFor(a))
            val scale = lengthPx * item.cam.forward / fView
            val trackMag = (a.trackDeg ?: Geo.bearingDeg(item.trueEnu)) - decl
            val verts = AircraftModels.worldVertices(mesh, trackMag, a.pitchDeg, scale, Geo.toMagnetic(item.trueEnu, decl))
            val pts = verts.map { Projector.project(it, rot, rotation, fView, cx, cy) }
            path.reset()
            val e = mesh.edges
            var i = 0
            while (i < e.size) {
                val p1 = pts[e[i]]
                val p2 = pts[e[i + 1]]
                if (p1 != null && p2 != null) {
                    path.moveTo(p1.x, p1.y)
                    path.lineTo(p2.x, p2.y)
                }
                i += 2
            }
            if (selected) drawPath(path, Hud.blue(0.25f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, Hud.blue(alpha), style = Stroke((if (selected) 1.8 else 1.3).dp.toPx(), cap = StrokeCap.Round))

            // Data tag with a leader line (flipped to the left near the right edge).
            val title = tm.measure(
                a.label + (a.typeCode?.let { "  $it" } ?: ""),
                Hud.text(12.sp, alpha = alpha, weight = FontWeight.Bold, glow = false),
            )
            val line2 = tm.measure(
                "${PlaneFormat.altitude(a)}${PlaneFormat.trend(a)} · ${PlaneFormat.speed(a)}",
                Hud.text(10.sp, alpha = alpha * 0.9f, glow = false),
            )
            val line3 = tm.measure(PlaneFormat.distance(distKm), Hud.text(10.sp, alpha = alpha * 0.75f, glow = false))
            val tagW = maxOf(title.size.width, line2.size.width, line3.size.width)
            val flip = sp.x + lengthPx * 0.55f + tagW > size.width - 12.dp.toPx()
            val tagX = if (flip) sp.x - lengthPx * 0.55f - tagW else sp.x + lengthPx * 0.55f
            val tagY = sp.y - lengthPx * 0.45f
            val anchorX = if (flip) tagX + tagW else tagX
            drawLine(Hud.blue(alpha * 0.7f), Offset(sp.x + (if (flip) -1 else 1) * lengthPx * 0.2f, sp.y - lengthPx * 0.15f),
                Offset(anchorX, tagY + 4.dp.toPx()), 1.dp.toPx())
            drawText(title, topLeft = Offset(tagX, tagY - title.size.height))
            drawText(line2, topLeft = Offset(tagX, tagY + 2.dp.toPx()))
            drawText(line3, topLeft = Offset(tagX, tagY + 2.dp.toPx() + line2.size.height))

            if (selected) lockBrackets(Offset(sp.x, sp.y), lengthPx * 0.62f)
            hits += a.hex to (sp.x to sp.y)
        }

        // Edge arrows for the nearest planes outside the view.
        for (item in offscreen.sortedBy { it.trueEnu.horizontal }.take(MAX_EDGE_ARROWS)) {
            edgeArrow(tm, item.a, Projector.edgeAngle(item.cam), item.trueEnu.horizontal / 1000,
                item.a.hex == scene.selectedHex)
        }
        scene.hits?.update(hits)
    }
}

private const val MAX_EDGE_ARROWS = 8

private class Item(val a: Aircraft, val trueEnu: Enu, val cam: CamVec)

private fun DrawScope.lockBrackets(c: Offset, half: Float) {
    val len = half * 0.35f
    val s = 2.dp.toPx()
    for ((sx, sy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) {
        val corner = Offset(c.x + sx * half, c.y + sy * half)
        drawLine(Hud.Blue, corner, corner + Offset(-sx * len, 0f), s)
        drawLine(Hud.Blue, corner, corner + Offset(0f, -sy * len), s)
    }
}

private fun DrawScope.edgeArrow(tm: TextMeasurer, a: Aircraft, angle: Double, distKm: Double, selected: Boolean) {
    val left = 60.dp.toPx()
    val right = 116.dp.toPx()
    val top = 150.dp.toPx()
    val bottom = 60.dp.toPx()
    val c = center
    val dx = cos(angle).toFloat()
    val dy = sin(angle).toFloat()
    val tx = if (dx > 1e-4f) (size.width - right - c.x) / dx else if (dx < -1e-4f) (left - c.x) / dx else Float.MAX_VALUE
    val ty = if (dy > 1e-4f) (size.height - bottom - c.y) / dy else if (dy < -1e-4f) (top - c.y) / dy else Float.MAX_VALUE
    val t = min(tx, ty)
    val p = Offset(c.x + dx * t, c.y + dy * t)
    val len = 16.dp.toPx()
    val tip = p + Offset(dx * len, dy * len)
    val nx = -dy
    val ny = dx
    val arrow = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(p.x + nx * len * 0.55f, p.y + ny * len * 0.55f)
        lineTo(p.x - nx * len * 0.55f, p.y - ny * len * 0.55f)
        close()
    }
    val alpha = if (selected) 1f else 0.75f
    drawPath(arrow, Hud.blue(alpha * 0.35f))
    drawPath(arrow, Hud.blue(alpha), style = Stroke(1.5.dp.toPx()))
    val label = tm.measure("${a.label} · ${PlaneFormat.distance(distKm)}", Hud.text(10.sp, alpha = alpha, glow = false))
    val lx = (p.x - dx * 14.dp.toPx() - label.size.width / 2f).coerceIn(8.dp.toPx(), size.width - label.size.width - 8.dp.toPx())
    val ly = (p.y - dy * 14.dp.toPx() - label.size.height / 2f).coerceIn(8.dp.toPx(), size.height - label.size.height - 8.dp.toPx())
    drawText(label, topLeft = Offset(lx, ly))
}

private fun DrawScope.centerText(tm: TextMeasurer, text: String) {
    val m = tm.measure(text, Hud.text(14.sp, weight = FontWeight.Bold))
    val topLeft = Offset(center.x - m.size.width / 2f, center.y + min(size.width, size.height) * 0.16f)
    drawRect(Hud.Black.copy(alpha = 0.5f), topLeft - Offset(10f, 6f), Size(m.size.width + 20f, m.size.height + 12f))
    drawText(m, topLeft = topLeft)
}
