package com.ghaith.ironhud.ui.hud

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaith.ironhud.LockPhase
import com.ghaith.ironhud.LockUi
import com.ghaith.ironhud.ui.theme.Hud
import com.ghaith.ironhud.vision.TrackedObject
import java.util.Locale
import kotlin.math.min

/**
 * Corner brackets on every tracked object, a charging bar on the one under the reticle, and the
 * heavy animated lock (plus a leader line to the brief panel) on the locked target.
 */
@Composable
fun TargetBrackets(
    targets: List<TrackedObject>,
    dwellId: Int?,
    dwellProgress: Float,
    lock: LockUi?,
    lockLabel: String?,
    panelAnchor: Offset?,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "lock")
    val pulse = if (animate) {
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse").value
    } else 0.5f
    val spin = if (animate) {
        transition.animateFloat(0f, 360f, infiniteRepeatable(tween(3_000, easing = LinearEasing)), label = "spin").value
    } else 20f
    val tm = rememberTextMeasurer()

    Canvas(modifier) {
        for (t in targets) {
            if (lock?.targetId != null && t.id == lock.targetId) continue
            val charging = t.id != null && t.id == dwellId
            brackets(t.box, Hud.blue(if (charging) 0.95f else 0.55f), (if (charging) 2.dp else 1.5.dp).toPx())
            val tag = buildString {
                append("OBJ-").append(t.id?.toString()?.padStart(3, '0') ?: "---")
                t.category?.let { append(" · ").append(it.uppercase(Locale.US)) }
            }
            label(tm, tag, Offset(t.box.left, t.box.top), filled = false, alpha = if (charging) 1f else 0.7f)
            if (charging) {
                val y = t.box.bottom + 6.dp.toPx()
                drawLine(Hud.blue(0.3f), Offset(t.box.left, y), Offset(t.box.right, y), 3.dp.toPx())
                drawLine(Hud.Blue, Offset(t.box.left, y), Offset(t.box.left + t.box.width * dwellProgress, y), 3.dp.toPx())
            }
        }

        if (lock != null) {
            val b = lock.box
            val offline = lock.phase == LockPhase.OFFLINE
            val inset = if (lock.phase == LockPhase.ANALYZING) 6.dp.toPx() * pulse else 0f
            val alpha = if (offline) 0.4f + 0.6f * pulse else 1f
            brackets(
                Rect(b.left - inset, b.top - inset, b.right + inset, b.bottom + inset),
                Hud.blue(alpha), 3.dp.toPx(),
                dashed = offline,
            )
            val radius = (min(b.width, b.height) * 0.32f).coerceIn(18.dp.toPx(), 90.dp.toPx())
            rotate(spin, b.center) {
                drawCircle(
                    Hud.blue(0.7f * alpha), radius, b.center,
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
            }
            drawCircle(Hud.blue(alpha), 3.dp.toPx(), b.center)
            val text = lockLabel ?: if (lock.phase == LockPhase.ANALYZING) "ANALYZING" else "LOCKED"
            label(tm, text.uppercase(Locale.US), Offset(b.left, b.top), filled = true, alpha = alpha)

            if (panelAnchor != null) {
                // Elbow leader line from the target to the panel, like the film HUD.
                val from = if (panelAnchor.x < b.left) Offset(b.left, b.center.y) else Offset(b.center.x, b.top)
                val elbow = Offset(panelAnchor.x + (from.x - panelAnchor.x) * 0.35f, from.y)
                drawLine(Hud.blue(0.6f), from, elbow, 1.dp.toPx())
                drawLine(Hud.blue(0.6f), elbow, panelAnchor, 1.dp.toPx())
                drawCircle(Hud.Blue, 3.dp.toPx(), panelAnchor)
            }
        }
    }
}

private fun DrawScope.brackets(box: Rect, color: Color, stroke: Float, dashed: Boolean = false) {
    val len = (min(box.width, box.height) * 0.22f).coerceIn(10.dp.toPx(), 44.dp.toPx())
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null
    fun seg(a: Offset, b: Offset) = drawLine(color, a, b, stroke, StrokeCap.Square, effect)
    seg(box.topLeft, box.topLeft + Offset(len, 0f)); seg(box.topLeft, box.topLeft + Offset(0f, len))
    seg(box.topRight, box.topRight + Offset(-len, 0f)); seg(box.topRight, box.topRight + Offset(0f, len))
    seg(box.bottomLeft, box.bottomLeft + Offset(len, 0f)); seg(box.bottomLeft, box.bottomLeft + Offset(0f, -len))
    seg(box.bottomRight, box.bottomRight + Offset(-len, 0f)); seg(box.bottomRight, box.bottomRight + Offset(0f, -len))
}

private fun DrawScope.label(tm: TextMeasurer, text: String, anchor: Offset, filled: Boolean, alpha: Float) {
    val style = Hud.text(if (filled) 12.sp else 10.sp, alpha = if (filled) 1f else alpha, glow = false,
        weight = if (filled) FontWeight.Bold else FontWeight.Normal)
    val m = tm.measure(text.take(42), if (filled) style.copy(color = Hud.Black) else style)
    val pad = 4.dp.toPx()
    val top = (anchor.y - m.size.height - pad * 2).coerceAtLeast(0f)
    if (filled) {
        drawRect(Hud.blue(0.9f * alpha), Offset(anchor.x, top), Size(m.size.width + pad * 2, m.size.height + pad))
    }
    drawText(m, topLeft = Offset(anchor.x + pad, top + pad / 2))
}
