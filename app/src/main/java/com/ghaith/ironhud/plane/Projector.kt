package com.ghaith.ironhud.plane

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/** A vector in the back camera's frame: right / up on screen, and forward along the lens. */
data class CamVec(val right: Double, val up: Double, val forward: Double)

data class ScreenPoint(val x: Float, val y: Float, val depth: Double)

/** Camera-frame attitude: compass heading of the lens, its pitch, and the screen's roll (degrees). */
data class Orientation(val headingDeg: Double, val pitchDeg: Double, val rollDeg: Double)

/**
 * Maps world directions onto the screen.
 *
 * [rot] is the row-major 3×3 matrix from `SensorManager.getRotationMatrixFromVector`: it takes device
 * coordinates (x right, y up, z out of the screen, in the device's natural orientation) to world
 * coordinates (x east, y magnetic north, z up). The back camera looks along −z. [displayRotation] is
 * `Surface.ROTATION_*` (0-3): turning the device counter-clockwise by 90° gives ROTATION_90, where the
 * screen's up is the device's +x and its right is the device's −y.
 */
object Projector {

    fun toCamera(v: Enu, rot: FloatArray, displayRotation: Int): CamVec {
        // device = Rᵀ · world
        val dx = rot[0] * v.e + rot[3] * v.n + rot[6] * v.u
        val dy = rot[1] * v.e + rot[4] * v.n + rot[7] * v.u
        val dz = rot[2] * v.e + rot[5] * v.n + rot[8] * v.u
        val (right, up) = screenAxes(dx, dy, displayRotation)
        return CamVec(right, up, -dz)
    }

    private fun screenAxes(dx: Double, dy: Double, displayRotation: Int): Pair<Double, Double> = when (displayRotation) {
        1 -> -dy to dx
        2 -> -dx to -dy
        3 -> dy to -dx
        else -> dx to dy
    }

    /** Pinhole projection; null when the point is behind (or practically at) the camera. */
    fun project(c: CamVec, fView: Double, cx: Float, cy: Float): ScreenPoint? {
        if (c.forward <= MIN_DEPTH) return null
        return ScreenPoint(
            (cx + fView * c.right / c.forward).toFloat(),
            (cy - fView * c.up / c.forward).toFloat(),
            c.forward,
        )
    }

    fun project(v: Enu, rot: FloatArray, displayRotation: Int, fView: Double, cx: Float, cy: Float): ScreenPoint? =
        project(toCamera(v, rot, displayRotation), fView, cx, cy)

    /**
     * Screen-space direction (radians, 0 = right, +π/2 = down) towards a target that is off-screen
     * or behind the viewer, for the edge arrows.
     */
    fun edgeAngle(c: CamVec): Double = atan2(-c.up, c.right)

    /**
     * Focal length in view pixels. The preview stream shows the full sensor width and PreviewView's
     * FILL_CENTER scales it to cover the view, so the sensor's long side spans
     * max(viewLong, viewShort × sensorAspect) pixels. Zoom narrows the view proportionally.
     */
    fun fViewPx(focalMm: Float?, sensorWmm: Float?, sensorHmm: Float?, viewW: Int, viewH: Int, zoom: Float): Double {
        val long = max(sensorWmm ?: 0f, sensorHmm ?: 0f)
        val short = min(sensorWmm ?: 0f, sensorHmm ?: 0f)
        val aspect = if (long > 0f && short > 0f) long / short.toDouble() else 4.0 / 3.0
        val fOverLong = if (focalMm != null && focalMm > 0f && long > 0f) focalMm / long.toDouble()
        else 1.0 / (2.0 * tan(Geo.rad(DEFAULT_LONG_FOV_DEG / 2)))
        val viewLong = max(viewW, viewH).toDouble()
        val viewShort = min(viewW, viewH).toDouble()
        return fOverLong * max(viewLong, viewShort * aspect) * zoom.coerceAtLeast(0.1f)
    }

    /** Heading/pitch/roll of what the camera sees, consistent with [toCamera]. */
    fun orientation(rot: FloatArray, displayRotation: Int, declinationDeg: Double = 0.0): Orientation {
        // Columns of R are the device axes in world coordinates.
        fun col(i: Int) = doubleArrayOf(rot[i].toDouble(), rot[3 + i].toDouble(), rot[6 + i].toDouble())
        val x = col(0)
        val y = col(1)
        val z = col(2)
        val forward = doubleArrayOf(-z[0], -z[1], -z[2])
        fun neg(a: DoubleArray) = doubleArrayOf(-a[0], -a[1], -a[2])
        val (right, up) = when (displayRotation) {
            1 -> neg(y) to x
            2 -> neg(x) to neg(y)
            3 -> y to neg(x)
            else -> x to y
        }
        val heading = (Geo.deg(atan2(forward[0], forward[1])) + declinationDeg + 720.0) % 360.0
        val pitch = Geo.deg(asin(forward[2].coerceIn(-1.0, 1.0)))
        val roll = Geo.deg(atan2(-right[2], up[2]))
        return Orientation(heading, pitch, roll)
    }

    const val DEFAULT_LONG_FOV_DEG = 66.0
    private const val MIN_DEPTH = 1.0
}

/** Back-camera lens facts needed to size the projection (null when the device doesn't say). */
data class CameraOptics(val focalMm: Float? = null, val sensorWmm: Float? = null, val sensorHmm: Float? = null)
