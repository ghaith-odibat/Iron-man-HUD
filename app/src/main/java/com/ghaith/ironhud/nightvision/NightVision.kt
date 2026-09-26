package com.ghaith.ironhud.nightvision

import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Night-vision image maths, shared by the live GPU shader ([AGSL]) and the CPU paths (screenshots,
 * AI crops) so they look the same. Everything maps to the one HUD colour, #66D9FF.
 */
object NightVision {
    /** 4 stops: the gain slider runs from 1× to 16×. */
    const val MAX_STOPS = 4.0
    const val DEFAULT_FRACTION = 0.5f
    /** One slider step: half a stop. */
    const val STEP = 0.125f

    private const val CURVE_K = 1.6
    private const val CURVE_GAMMA = 0.85
    private const val HUD_R = 0x66
    private const val HUD_G = 0xD9
    private const val HUD_B = 0xFF

    /** Amplification for a slider position 0..1. */
    fun gain(fraction: Float): Double = 2.0.pow(MAX_STOPS * fraction.coerceIn(0f, 1f))

    private fun raw(y: Double, gain: Double) = (1 - exp(-y * gain * CURVE_K)).pow(CURVE_GAMMA)

    /** Scale that makes full white stay full white at any gain. */
    fun norm(gain: Double): Double = 1.0 / raw(1.0, gain)

    /**
     * Intensifier curve for luminance [y] in 0..1: exposure-like (shadows lifted hard, highlights
     * rolled off instead of clipping), 0 stays 0 and white stays white.
     */
    fun tone(y: Double, gain: Double): Double =
        if (y <= 0.0) 0.0 else (raw(y, gain) * norm(gain)).coerceIn(0.0, 1.0)

    /** [tone] for 8-bit luminance. */
    fun lut(gain: Double): IntArray = IntArray(256) { (tone(it / 255.0, gain) * 255).roundToInt() }

    /** Rec. 601 luma of an ARGB pixel, 0..255. */
    fun luma(c: Int): Int = ((c shr 16 and 0xFF) * 77 + (c shr 8 and 0xFF) * 150 + (c and 0xFF) * 29) shr 8

    /** HUD blue at intensity [v] (0..1), opaque. */
    fun hud(v: Double): Int {
        val k = v.coerceIn(0.0, 1.0)
        return (0xFF shl 24) or ((HUD_R * k).roundToInt() shl 16) or ((HUD_G * k).roundToInt() shl 8) or (HUD_B * k).roundToInt()
    }

    /** In place: amplified luminance in HUD blue, with a little grain (screenshots). */
    fun toHud(pixels: IntArray, gain: Double, seed: Int = 1, grain: Double = 0.08) {
        val lut = lut(gain)
        val rnd = Random(seed)
        for (i in pixels.indices) {
            var v = lut[luma(pixels[i])] / 255.0
            if (grain > 0) v += (rnd.nextDouble() - 0.5) * grain * (1 - 0.5 * v)
            pixels[i] = hud(v)
        }
    }

    /** In place: brighter but still in colour, for what the AI and on-device labeler look at. */
    fun brighten(pixels: IntArray, gain: Double) {
        val lut = lut(gain)
        for (i in pixels.indices) {
            val c = pixels[i]
            val y = luma(c)
            if (y == 0) continue
            val k = lut[y] / y.toDouble()
            val r = min(255, ((c shr 16 and 0xFF) * k).roundToInt())
            val g = min(255, ((c shr 8 and 0xFF) * k).roundToInt())
            val b = min(255, ((c and 0xFF) * k).roundToInt())
            pixels[i] = (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
        }
    }

    /**
     * The AE frame-rate range that lets the camera expose longest: the lowest minimum (a frame can
     * then take up to 1/min s), preferring a higher maximum so it still runs smoothly when bright.
     */
    fun pickFpsRange(ranges: List<Pair<Int, Int>>): Pair<Int, Int>? =
        ranges.filter { it.first >= 1 && it.second >= it.first }
            .minWithOrNull(compareBy<Pair<Int, Int>>({ it.first }, { -it.second }))

    /** Light-sensor hysteresis: dark below 8 lux, bright again only above 20 lux. */
    fun isDark(wasDark: Boolean, lux: Double): Boolean = if (wasDark) lux < 20.0 else lux < 8.0

    /** "NV ×4.0 · EV +2.0 · ≥7 FPS · 3 LX" for the gain strip. */
    fun label(fraction: Float, ev: Float?, minFps: Int?, lux: Float?): String = listOfNotNull(
        String.format(Locale.US, "NV ×%.1f", gain(fraction)),
        ev?.let { String.format(Locale.US, "EV %+.1f", it) },
        minFps?.let { "≥$it FPS" },
        lux?.let { "${if (it < 10) String.format(Locale.US, "%.1f", it) else it.roundToInt().toString()} LX" },
    ).joinToString(" · ")

    /**
     * Fallback for Android 8-12 (no runtime shaders): a colour matrix that turns luminance × gain
     * into HUD blue with a slight shadow lift. Linear, so no curve and no grain.
     */
    fun fallbackMatrix(gain: Double): FloatArray {
        val g = (min(gain, 8.0) * 0.9).toFloat()
        val lift = 6f
        fun row(c: Int) = (c / 255f).let { k -> floatArrayOf(0.299f * k * g, 0.587f * k * g, 0.114f * k * g, 0f, lift * k) }
        return row(HUD_R) + row(HUD_G) + row(HUD_B) + floatArrayOf(0f, 0f, 0f, 1f, 0f)
    }

    /** True when [argb]'s hue is within [toleranceDeg] of the HUD blue (for tests). */
    fun isHudHue(argb: Int, toleranceDeg: Float = 12f): Boolean {
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        return abs(hue(r, g, b) - hue(HUD_R, HUD_G, HUD_B)) <= toleranceDeg
    }

    private fun hue(r: Int, g: Int, b: Int): Float {
        val mx = maxOf(r, g, b)
        val mn = minOf(r, g, b)
        if (mx == mn) return 0f
        val d = (mx - mn).toFloat()
        val h = when (mx) {
            r -> ((g - b) / d) % 6
            g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        } * 60f
        return if (h < 0) h + 360f else h
    }

    /**
     * Live-view shader (AGSL, Android 13+): the same curve as [tone], mapped to HUD blue, with
     * grain that changes every frame. Vignette and scan lines are drawn by the HUD on top.
     * Uniforms: gain, norm (= [norm]), time (seconds, kept small), density (px per dp).
     */
    const val AGSL = """
uniform shader image;
uniform float gain;
uniform float norm;
uniform float time;
uniform float density;

float hash12(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

half4 main(float2 p) {
    half4 c = image.eval(p);
    float y = dot(float3(c.rgb), float3(0.299, 0.587, 0.114));
    float v = pow(1.0 - exp(-max(y, 0.0) * gain * 1.6), 0.85) * norm;
    float2 cell = floor(p / max(1.5 * density, 1.0));
    float n = hash12(cell + float2(time * 61.7, time * 23.3)) - 0.5;
    v = clamp(v + n * 0.12 * (1.0 - 0.5 * v), 0.0, 1.0);
    return half4(half3(0.4, 0.851, 1.0) * half(v), 1.0);
}
"""
}
