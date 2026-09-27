package com.ghaith.ironhud.plane

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Projects a [WireMesh] to the screen and sorts its edges into line lists, reusing its buffers from
 * frame to frame so drawing dozens of detailed models allocates nothing.
 *
 * The transform is 12 numbers: the model origin, then the model's x, y and z axes, each as
 * (right, up, forward) in camera space with the model scale already applied.
 */
class WireProjection {
    private var sx = FloatArray(0)
    private var sy = FloatArray(0)
    private var depth = FloatArray(0)
    private var local = FloatArray(0)

    /** Screen-space line segments (x1, y1, x2, y2)… for Canvas.drawLines. */
    val near = LineBuffer()
    val far = LineBuffer()
    val faint = LineBuffer()
    /** Glazing on the near side (the far side's joins [far]). */
    val accent = LineBuffer()

    /** Screen position of the model origin, valid after [project] when [originVisible]. */
    var originX = 0f
        private set
    var originY = 0f
        private set
    var originVisible = false
        private set

    fun project(mesh: WireMesh, m: DoubleArray, fView: Double, cx: Float, cy: Float, timeS: Double) {
        val n = mesh.vertexCount
        if (sx.size < n) {
            sx = FloatArray(n)
            sy = FloatArray(n)
            depth = FloatArray(n)
            local = FloatArray(n * 3)
        }
        mesh.vertices.copyInto(local, 0, 0, n * 3)
        for (spin in mesh.spins) spin(spin, timeS)

        for (i in 0 until n) {
            val x = local[i * 3].toDouble()
            val y = local[i * 3 + 1].toDouble()
            val z = local[i * 3 + 2].toDouble()
            val r = m[0] + x * m[3] + y * m[6] + z * m[9]
            val u = m[1] + x * m[4] + y * m[7] + z * m[10]
            val f = m[2] + x * m[5] + y * m[8] + z * m[11]
            if (f <= MIN_DEPTH) {
                depth[i] = -1f
            } else {
                depth[i] = f.toFloat()
                sx[i] = (cx + fView * r / f).toFloat()
                sy[i] = (cy - fView * u / f).toFloat()
            }
        }
        originVisible = m[2] > MIN_DEPTH
        if (originVisible) {
            originX = (cx + fView * m[0] / m[2]).toFloat()
            originY = (cy - fView * m[1] / m[2]).toFloat()
        }

        near.clear()
        far.clear()
        faint.clear()
        accent.clear()
        // Edges beyond the model's centre are "far" and drawn dimmer: a cheap depth cue.
        val split = (m[2] * 2).toFloat()
        collect(mesh.edges, split, near, far)
        collect(mesh.faint, Float.MAX_VALUE, faint, faint)
        collect(mesh.accent, split, accent, far)
    }

    private fun collect(edges: IntArray, split: Float, nearBuf: LineBuffer, farBuf: LineBuffer) {
        var i = 0
        while (i < edges.size) {
            val a = edges[i]
            val b = edges[i + 1]
            i += 2
            val da = depth[a]
            val db = depth[b]
            if (da < 0f || db < 0f) continue
            (if (da + db > split) farBuf else nearBuf).add(sx[a], sy[a], sx[b], sy[b])
        }
    }

    /** Rotates one spinning vertex run in place (Rodrigues' formula). */
    private fun spin(s: Spin, timeS: Double) {
        val ang = 2 * PI * ((s.revPerSec * timeS) % 1.0)
        val c = cos(ang)
        val sn = sin(ang)
        val kx = s.axis[0].toDouble()
        val ky = s.axis[1].toDouble()
        val kz = s.axis[2].toDouble()
        for (i in s.first until s.end) {
            val px = local[i * 3] - s.origin[0].toDouble()
            val py = local[i * 3 + 1] - s.origin[1].toDouble()
            val pz = local[i * 3 + 2] - s.origin[2].toDouble()
            val dot = kx * px + ky * py + kz * pz
            val cxp = ky * pz - kz * py
            val cyp = kz * px - kx * pz
            val czp = kx * py - ky * px
            local[i * 3] = (px * c + cxp * sn + kx * dot * (1 - c) + s.origin[0]).toFloat()
            local[i * 3 + 1] = (py * c + cyp * sn + ky * dot * (1 - c) + s.origin[1]).toFloat()
            local[i * 3 + 2] = (pz * c + czp * sn + kz * dot * (1 - c) + s.origin[2]).toFloat()
        }
    }

    private companion object {
        const val MIN_DEPTH = 1e-3
    }
}

/** A growable list of line segments. */
class LineBuffer {
    var points = FloatArray(1024)
        private set
    /** Number of floats used (4 per segment). */
    var size = 0
        private set

    fun clear() {
        size = 0
    }

    fun add(x1: Float, y1: Float, x2: Float, y2: Float) {
        if (size + 4 > points.size) points = points.copyOf(points.size * 2)
        points[size] = x1
        points[size + 1] = y1
        points[size + 2] = x2
        points[size + 3] = y2
        size += 4
    }
}
