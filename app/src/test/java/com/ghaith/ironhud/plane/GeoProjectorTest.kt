package com.ghaith.ironhud.plane

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class GeoProjectorTest {
    private val amman = GeoPoint(31.95, 35.93, 800.0)

    @Test fun enuOffsetsAndCurvatureDrop() {
        val north = Geo.enu(amman, GeoPoint(amman.lat + 0.1, amman.lon, 800.0))
        assertEquals(11_119.0, north.n, 20.0)
        assertEquals(0.0, north.e, 1.0)
        // ~10 m of curvature drop at 11 km.
        assertEquals(-north.n * north.n / (2 * Geo.EARTH_RADIUS_M), north.u, 0.5)

        val east = Geo.enu(amman, GeoPoint(amman.lat, amman.lon + 0.1, 800.0))
        assertEquals(11_119.0 * cos(Geo.rad(amman.lat)), east.e, 20.0)
        assertEquals(90.0, Geo.bearingDeg(east), 0.1)
    }

    @Test fun declinationRotatesTrueNorthWest() {
        val m = Geo.toMagnetic(Enu(0.0, 1000.0, 0.0), declinationDeg = 5.0)
        // Magnetic north is 5° east of true north, so true north reads as 355° magnetic.
        assertEquals(355.0, Geo.bearingDeg(m), 0.01)
    }

    @Test fun destinationRoundTrip() {
        val (la, lo) = Geo.destination(amman.lat, amman.lon, 45.0, 10_000.0)
        val v = Geo.enu(amman, GeoPoint(la, lo, amman.altM))
        assertEquals(10_000.0, v.horizontal, 15.0)
        assertEquals(45.0, Geo.bearingDeg(v), 0.2)
    }

    // Tablet upright, natural orientation, camera facing north.
    private val facingNorth = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)
    // Same, but the tablet is turned 90° counter-clockwise (ROTATION_90).
    private val facingNorthRot90 = floatArrayOf(0f, -1f, 0f, 0f, 0f, -1f, 1f, 0f, 0f)

    @Test fun planeAheadAndAboveProjectsAboveCentre() {
        val p = Projector.project(Enu(0.0, 10_000.0, 1_000.0), facingNorth, 0, 1000.0, 640f, 400f)
        assertNotNull(p)
        assertEquals(640f, p!!.x, 0.01f)
        assertEquals(400f - 100f, p.y, 0.01f)
    }

    @Test fun rightAndBehind() {
        val right = Projector.project(Enu(2_000.0, 10_000.0, 0.0), facingNorth, 0, 1000.0, 640f, 400f)!!
        assertEquals(840f, right.x, 0.01f)
        assertNull(Projector.project(Enu(0.0, -10_000.0, 500.0), facingNorth, 0, 1000.0, 640f, 400f))
        // Edge arrow for something behind-left points left.
        val behindLeft = Projector.toCamera(Enu(-5_000.0, -1_000.0, 0.0), facingNorth, 0)
        assertTrue(kotlin.math.abs(Projector.edgeAngle(behindLeft)) > Math.PI / 2)
    }

    @Test fun displayRotationKeepsScreenAxesUpright() {
        val c = Projector.toCamera(Enu(2_000.0, 10_000.0, 1_000.0), facingNorthRot90, 1)
        assertEquals(2_000.0, c.right, 1e-6)
        assertEquals(1_000.0, c.up, 1e-6)
        assertEquals(10_000.0, c.forward, 1e-6)
    }

    @Test fun orientationFromMatrix() {
        val o = Projector.orientation(facingNorth, 0)
        assertEquals(0.0, o.headingDeg, 1e-6)
        assertEquals(0.0, o.pitchDeg, 1e-6)
        assertEquals(0.0, o.rollDeg, 1e-6)

        val east = floatArrayOf(0f, 0f, -1f, -1f, 0f, 0f, 0f, 1f, 0f)
        assertEquals(90.0, Projector.orientation(east, 0).headingDeg, 1e-6)
        assertEquals(95.0, Projector.orientation(east, 0, declinationDeg = 5.0).headingDeg, 1e-6)

        val s = sin(Geo.rad(30.0)).toFloat()
        val c = cos(Geo.rad(30.0)).toFloat()
        val tiltedUp = floatArrayOf(1f, 0f, 0f, 0f, -s, -c, 0f, c, -s)
        assertEquals(30.0, Projector.orientation(tiltedUp, 0).pitchDeg, 1e-4)
        // A plane 30° up, dead ahead, is now in the middle of the screen.
        val p = Projector.project(Enu(0.0, 10_000.0 * cos(Geo.rad(30.0)), 10_000.0 * sin(Geo.rad(30.0))), tiltedUp, 0, 1000.0, 0f, 0f)!!
        assertEquals(0f, p.x, 0.5f)
        assertEquals(0f, p.y, 0.5f)

        val r = Geo.rad(20.0)
        val rolled = floatArrayOf(cos(r).toFloat(), sin(r).toFloat(), 0f, 0f, 0f, -1f, -sin(r).toFloat(), cos(r).toFloat(), 0f)
        assertEquals(20.0, Projector.orientation(rolled, 0).rollDeg, 1e-4)
        assertEquals(0.0, Projector.orientation(facingNorthRot90, 1).rollDeg, 1e-6)
    }

    @Test fun focalLengthInViewPixels() {
        assertEquals(1600.0, Projector.fViewPx(4.0f, 6.4f, 4.8f, 2560, 1600, 1f), 0.5)
        assertEquals(3200.0, Projector.fViewPx(4.0f, 6.4f, 4.8f, 1600, 2560, 2f), 0.5)
        val fallback = Projector.fViewPx(null, null, null, 2560, 1600, 1f)
        assertEquals(2560 / (2 * kotlin.math.tan(Geo.rad(33.0))), fallback, 0.5)
    }
}
