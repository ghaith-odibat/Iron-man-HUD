package com.ghaith.ironhud.ui.plane

import android.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.ghaith.ironhud.plane.LineBuffer
import com.ghaith.ironhud.plane.WireMesh
import com.ghaith.ironhud.plane.WireProjection
import com.ghaith.ironhud.ui.theme.Hud

/**
 * Draws wireframe holograms with the platform's batched line call: the near half of a model
 * bright, the far half dimmer (a depth cue), fine detail fainter still, and an optional glow.
 */
class WirePainter {
    val projection = WireProjection()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /** See [WireProjection.project] for [basis]; [timeS] turns propellers and rotors. */
    fun draw(
        scope: DrawScope,
        mesh: WireMesh,
        basis: DoubleArray,
        fView: Double,
        cx: Float,
        cy: Float,
        timeS: Double,
        alpha: Float,
        strokePx: Float,
        glowPx: Float = 0f,
    ) {
        val p = projection
        p.project(mesh, basis, fView, cx, cy, timeS)
        val canvas = scope.drawContext.canvas.nativeCanvas
        if (glowPx > 0f) {
            lines(canvas, p.near, Hud.blue(0.22f * alpha).toArgb(), glowPx)
            lines(canvas, p.far, Hud.blue(0.12f * alpha).toArgb(), glowPx)
        }
        lines(canvas, p.faint, Hud.blue(0.38f * alpha).toArgb(), strokePx * 0.8f)
        lines(canvas, p.far, Hud.blue(0.45f * alpha).toArgb(), strokePx)
        lines(canvas, p.near, Hud.blue(alpha).toArgb(), strokePx)
    }

    private fun lines(canvas: android.graphics.Canvas, buf: LineBuffer, argb: Int, width: Float) {
        if (buf.size == 0) return
        paint.color = argb
        paint.strokeWidth = width
        canvas.drawLines(buf.points, 0, buf.size, paint)
    }
}
