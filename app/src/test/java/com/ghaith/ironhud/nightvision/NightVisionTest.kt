package com.ghaith.ironhud.nightvision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightVisionTest {

    @Test fun gainRunsFromOneToSixteen() {
        assertEquals(1.0, NightVision.gain(0f), 1e-9)
        assertEquals(4.0, NightVision.gain(NightVision.DEFAULT_FRACTION), 1e-9)
        assertEquals(16.0, NightVision.gain(1f), 1e-9)
        assertEquals(16.0, NightVision.gain(3f), 1e-9)          // clamped
        assertEquals(2.0 * NightVision.gain(0.5f), NightVision.gain(0.5f + 2 * NightVision.STEP), 1e-9)  // two steps = one stop
    }

    @Test fun toneCurveLiftsShadowsAndKeepsEnds() {
        for (g in listOf(1.0, 4.0, 16.0)) {
            assertEquals(0.0, NightVision.tone(0.0, g), 1e-12)
            assertEquals(1.0, NightVision.tone(1.0, g), 1e-9)
            var prev = -1.0
            for (i in 0..100) {
                val v = NightVision.tone(i / 100.0, g)
                assertTrue("monotonic at gain $g", v >= prev)
                assertTrue(v in 0.0..1.0)
                prev = v
            }
        }
        assertTrue(NightVision.tone(0.05, 8.0) > 0.3)           // a nearly black pixel becomes readable
        assertTrue(NightVision.tone(0.05, 16.0) > NightVision.tone(0.05, 4.0))
        val lut = NightVision.lut(4.0)
        assertEquals(0, lut[0])
        assertEquals(255, lut[255])
    }

    @Test fun screenshotPathIsHudBlueAndBrighter() {
        // A dark grey ramp, as a phone camera sees a dim room.
        val px = IntArray(64) { i -> val v = 4 + i / 2; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        val before = px.sumOf { NightVision.luma(it) }
        NightVision.toHud(px, NightVision.gain(0.5f), seed = 7, grain = 0.0)
        val after = px.sumOf { NightVision.luma(it) }
        assertTrue("amplified ($before → $after)", after > before * 3)
        for (c in px) {
            val mx = maxOf(c shr 16 and 0xFF, c shr 8 and 0xFF, c and 0xFF)
            if (mx >= 60) assertTrue("pixel ${Integer.toHexString(c)} is HUD blue", NightVision.isHudHue(c))
        }
    }

    @Test fun aiPathKeepsColour() {
        val red = (0xFF shl 24) or (40 shl 16) or (10 shl 8) or 5
        val px = intArrayOf(red, 0xFF000000.toInt())
        NightVision.brighten(px, 2.0)
        val r = px[0] shr 16 and 0xFF
        val g = px[0] shr 8 and 0xFF
        val b = px[0] and 0xFF
        assertTrue(r > 40)
        assertEquals(40.0 / 10.0, r.toDouble() / g, 0.35)     // hue ratios survive
        assertEquals(10.0 / 5.0, g.toDouble() / b, 0.35)
        assertEquals(0xFF000000.toInt(), px[1])                 // black stays black
    }

    @Test fun picksTheLongestExposureRange() {
        val ranges = listOf(15 to 30, 30 to 30, 7 to 30, 7 to 15, 24 to 24)
        assertEquals(7 to 30, NightVision.pickFpsRange(ranges))
        assertNull(NightVision.pickFpsRange(emptyList()))
    }

    @Test fun darkHintHasHysteresis() {
        assertFalse(NightVision.isDark(false, 12.0))
        assertTrue(NightVision.isDark(false, 5.0))
        assertTrue(NightVision.isDark(true, 12.0))               // stays dark until clearly bright
        assertFalse(NightVision.isDark(true, 25.0))
    }

    @Test fun label() {
        assertEquals("NV ×4.0 · EV +2.0 · ≥7 FPS · 3.5 LX", NightVision.label(0.5f, 2f, 7, 3.5f))
        assertEquals("NV ×16.0", NightVision.label(1f, null, null, null))
    }

    @Test fun fallbackMatrixIsHudBlue() {
        val m = NightVision.fallbackMatrix(4.0)
        assertEquals(20, m.size)
        // Blue row strongest, red weakest: the output hue is HUD blue.
        assertTrue(m[10] > m[5] && m[5] > m[0])
    }
}
