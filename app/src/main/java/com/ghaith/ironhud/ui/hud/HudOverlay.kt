package com.ghaith.ironhud.ui.hud

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaith.ironhud.ui.theme.Hud
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/** The static-ish HUD chrome: frame, compass tape, horizon, reticle and read-outs. */
@Composable
fun HudOverlay(
    attitude: Attitude,
    timeMs: Long,
    battery: Int?,
    uplink: String,
    tracking: Int,
    autoLock: Boolean,
    dwellProgress: Float,
    analyzing: Boolean,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "hud")
    val spin = if (animate) {
        transition.animateFloat(
            0f, 360f, infiniteRepeatable(tween(if (analyzing) 2_400 else 14_000, easing = LinearEasing)), label = "spin",
        ).value
    } else 30f
    val sweep = if (animate) {
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(5_500, easing = LinearEasing)), label = "sweep").value
    } else 0.35f
    val blink = if (animate) {
        transition.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "blink").value
    } else 1f
    val tm = rememberTextMeasurer()

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawFrame()
            drawScanSweep(sweep)
            drawCompass(tm, attitude.heading)
            drawHorizon(attitude.pitch, attitude.roll)
            drawReticle(spin, dwellProgress, analyzing, blink)
        }

        val clock = Date(timeMs)
        Column(Modifier.align(Alignment.TopStart).padding(start = 34.dp, top = 58.dp)) {
            BasicText("J.A.R.V.I.S.", style = Hud.text(20.sp, weight = FontWeight.Bold, spacing = 3.sp))
            BasicText("MARK VII · VISUAL INTEL", style = Hud.text(10.sp, alpha = 0.7f, spacing = 2.sp))
            BasicText(TIME.get()!!.format(clock), style = Hud.text(16.sp, alpha = 0.95f))
            BasicText(DATE.get()!!.format(clock).uppercase(Locale.US), style = Hud.text(10.sp, alpha = 0.6f))
        }

        Column(
            Modifier.align(Alignment.TopEnd).padding(end = 112.dp, top = 58.dp),
            horizontalAlignment = Alignment.End,
        ) {
            BasicText(
                "PWR " + (battery?.let { "$it%" } ?: "--"),
                style = Hud.text(14.sp, weight = FontWeight.Bold),
            )
            Canvas(Modifier.padding(top = 4.dp, bottom = 6.dp).size(width = 92.dp, height = 8.dp)) {
                drawRect(Hud.blue(0.8f), style = Stroke(1.dp.toPx()))
                val pct = (battery ?: 0) / 100f
                drawRect(Hud.blue(0.7f), topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                    size = Size((size.width - 4.dp.toPx()) * pct, size.height - 4.dp.toPx()))
            }
            BasicText(uplink, style = Hud.text(10.sp, alpha = 0.85f), modifier = Modifier.padding(top = 2.dp))
        }

        Column(Modifier.align(Alignment.BottomStart).padding(start = 34.dp, bottom = 30.dp)) {
            BasicText("TRACKING  ${tracking.toString().padStart(2, '0')} OBJ", style = Hud.text(12.sp))
            BasicText(
                if (autoLock) "AUTO-LOCK  ARMED" else "AUTO-LOCK  OFF · TAP TO SCAN",
                style = Hud.text(10.sp, alpha = if (autoLock) 0.85f else 0.55f),
            )
            BasicText(
                "PITCH ${attitude.pitch.roundToInt()}°  ROLL ${attitude.roll.roundToInt()}°",
                style = Hud.text(10.sp, alpha = 0.55f),
            )
        }
    }
}

private val TIME = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss", Locale.US) }
private val DATE = ThreadLocal.withInitial { SimpleDateFormat("EEE dd MMM yyyy", Locale.US) }

private fun DrawScope.drawFrame() {
    val inset = 16.dp.toPx()
    val len = 64.dp.toPx()
    val stroke = 2.dp.toPx()
    val c = Hud.blue(0.85f)
    val w = size.width
    val h = size.height
    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        drawLine(c, Offset(x, y), Offset(x + dx * len, y), stroke, StrokeCap.Square)
        drawLine(c, Offset(x, y), Offset(x, y + dy * len), stroke, StrokeCap.Square)
    }
    corner(inset, inset, 1f, 1f)
    corner(w - inset, inset, -1f, 1f)
    corner(inset, h - inset, 1f, -1f)
    corner(w - inset, h - inset, -1f, -1f)

    // Ruler ticks along both sides.
    val step = 22.dp.toPx()
    var y = inset + len + step
    var i = 0
    while (y < h - inset - len) {
        val long = i % 5 == 0
        val t = if (long) 12.dp.toPx() else 6.dp.toPx()
        drawLine(Hud.blue(if (long) 0.45f else 0.22f), Offset(inset, y), Offset(inset + t, y), 1.dp.toPx())
        drawLine(Hud.blue(if (long) 0.45f else 0.22f), Offset(w - inset, y), Offset(w - inset - t, y), 1.dp.toPx())
        y += step
        i++
    }
}

private fun DrawScope.drawScanSweep(t: Float) {
    val y = size.height * t
    val band = 70.dp.toPx()
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Hud.blue(0.10f)), startY = y - band, endY = y),
        topLeft = Offset(0f, y - band),
        size = Size(size.width, band),
    )
    drawLine(Hud.blue(0.35f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
}

private val CARDINALS = mapOf(0 to "N", 45 to "NE", 90 to "E", 135 to "SE", 180 to "S", 225 to "SW", 270 to "W", 315 to "NW")

private fun DrawScope.drawCompass(tm: TextMeasurer, heading: Float) {
    val cx = size.width / 2
    val top = 22.dp.toPx()
    val half = min(size.width * 0.26f, 340.dp.toPx())
    val span = 50f
    val base = top + 18.dp.toPx()
    drawLine(Hud.blue(0.5f), Offset(cx - half, base), Offset(cx + half, base), 1.dp.toPx())

    val start = (heading - span).toInt() - 1
    val end = (heading + span).toInt() + 1
    for (d in start..end) {
        if (d % 5 != 0) continue
        val x = cx + (d - heading) / span * half
        if (x < cx - half || x > cx + half) continue
        val deg = ((d % 360) + 360) % 360
        val fade = 1f - kotlin.math.abs(x - cx) / half * 0.7f
        val tall = deg % 15 == 0
        drawLine(
            Hud.blue(0.8f * fade),
            Offset(x, base),
            Offset(x, base - (if (tall) 10.dp else 5.dp).toPx()),
            (if (tall) 1.5.dp else 1.dp).toPx(),
        )
        val label = CARDINALS[deg] ?: if (deg % 15 == 0) deg.toString().padStart(3, '0') else null
        if (label != null) {
            val style = Hud.text(if (deg in CARDINALS) 13.sp else 9.sp, alpha = fade, glow = false,
                weight = if (deg in CARDINALS) FontWeight.Bold else FontWeight.Normal)
            val m = tm.measure(label, style)
            drawText(m, topLeft = Offset(x - m.size.width / 2f, base + 3.dp.toPx()))
        }
    }
    // Heading caret and read-out.
    val caret = Path().apply {
        moveTo(cx, base + 1.dp.toPx())
        lineTo(cx - 6.dp.toPx(), base + 10.dp.toPx())
        lineTo(cx + 6.dp.toPx(), base + 10.dp.toPx())
        close()
    }
    translate(top = 18.dp.toPx()) { drawPath(caret, Hud.Blue) }
    val hdg = tm.measure("HDG ${heading.roundToInt().mod(360).toString().padStart(3, '0')}°", Hud.text(12.sp, weight = FontWeight.Bold))
    val boxW = hdg.size.width + 14.dp.toPx()
    val boxTop = top - 16.dp.toPx()
    drawRect(Hud.blue(0.8f), topLeft = Offset(cx - boxW / 2, boxTop), size = Size(boxW, hdg.size.height + 4.dp.toPx()), style = Stroke(1.dp.toPx()))
    drawText(hdg, topLeft = Offset(cx - hdg.size.width / 2f, boxTop + 2.dp.toPx()))
}

private fun DrawScope.drawHorizon(pitch: Float, roll: Float) {
    val c = center
    val r = min(size.width, size.height)
    val gap = r * 0.16f
    val arm = r * 0.14f
    val pxPerDeg = size.height / 90f
    val offset = (pitch * pxPerDeg).coerceIn(-size.height / 4, size.height / 4)
    rotate(-roll, c) {
        translate(top = offset) {
            val y = c.y
            drawLine(Hud.blue(0.7f), Offset(c.x - gap - arm, y), Offset(c.x - gap, y), 2.dp.toPx())
            drawLine(Hud.blue(0.7f), Offset(c.x + gap, y), Offset(c.x + gap + arm, y), 2.dp.toPx())
            drawLine(Hud.blue(0.7f), Offset(c.x - gap, y), Offset(c.x - gap, y + 8.dp.toPx()), 2.dp.toPx())
            drawLine(Hud.blue(0.7f), Offset(c.x + gap, y), Offset(c.x + gap, y + 8.dp.toPx()), 2.dp.toPx())
            // Pitch ladder.
            for (k in listOf(-20, -10, 10, 20)) {
                val ly = y - k * pxPerDeg
                val w = arm * 0.55f
                drawLine(
                    Hud.blue(0.3f), Offset(c.x - gap - w, ly), Offset(c.x - gap, ly), 1.dp.toPx(),
                    pathEffect = if (k < 0) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
                )
                drawLine(
                    Hud.blue(0.3f), Offset(c.x + gap, ly), Offset(c.x + gap + w, ly), 1.dp.toPx(),
                    pathEffect = if (k < 0) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
                )
            }
        }
    }
}

private fun DrawScope.drawReticle(spin: Float, dwell: Float, analyzing: Boolean, blink: Float) {
    val c = center
    val r = min(size.width, size.height) * 0.075f
    val thin = 1.dp.toPx()

    drawCircle(Hud.blue(0.45f), r, c, style = Stroke(thin))
    // Rotating segmented outer ring.
    rotate(spin, c) {
        for (k in 0 until 4) {
            drawArc(
                Hud.blue(0.8f), startAngle = k * 90f + 12f, sweepAngle = 56f, useCenter = false,
                topLeft = Offset(c.x - r * 1.4f, c.y - r * 1.4f), size = Size(r * 2.8f, r * 2.8f),
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Butt),
            )
        }
    }
    rotate(-spin * 1.6f, c) {
        for (k in 0 until 3) {
            drawArc(
                Hud.blue(0.35f), startAngle = k * 120f, sweepAngle = 70f, useCenter = false,
                topLeft = Offset(c.x - r * 1.7f, c.y - r * 1.7f), size = Size(r * 3.4f, r * 3.4f),
                style = Stroke(thin, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))),
            )
        }
    }
    // Cross-hair with a gap in the middle.
    val inner = r * 0.3f
    val outer = r * 0.8f
    drawLine(Hud.Blue, Offset(c.x - outer, c.y), Offset(c.x - inner, c.y), 1.5.dp.toPx())
    drawLine(Hud.Blue, Offset(c.x + inner, c.y), Offset(c.x + outer, c.y), 1.5.dp.toPx())
    drawLine(Hud.Blue, Offset(c.x, c.y - outer), Offset(c.x, c.y - inner), 1.5.dp.toPx())
    drawLine(Hud.Blue, Offset(c.x, c.y + inner), Offset(c.x, c.y + outer), 1.5.dp.toPx())
    drawCircle(Hud.blue(if (analyzing) blink else 0.9f), 2.5.dp.toPx(), c)

    if (dwell > 0f) {
        drawArc(
            Hud.Blue, startAngle = -90f, sweepAngle = 360f * dwell, useCenter = false,
            topLeft = Offset(c.x - r * 1.15f, c.y - r * 1.15f), size = Size(r * 2.3f, r * 2.3f),
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
