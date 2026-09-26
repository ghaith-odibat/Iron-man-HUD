package com.ghaith.ironhud.vision

import android.graphics.Bitmap
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Turns a photo into a HUD-blue edge drawing (Sobel operator), like the hologram wireframe of the
 * Ferris wheel in the reference shot.
 */
object EdgeWireframe {
    private const val SIDE = 200
    private const val HUD_RGB = 0x66D9FF

    fun render(src: Bitmap): Bitmap {
        val small = ImagePrep.scaleToMax(src, SIDE)
        val w = small.width
        val h = small.height
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()

        val lum = FloatArray(w * h) { i ->
            val c = px[i]
            (0.299f * (c shr 16 and 0xFF) + 0.587f * (c shr 8 and 0xFF) + 0.114f * (c and 0xFF)) / 255f
        }
        val mag = FloatArray(w * h)
        var maxMag = 1e-3f
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val gx = -lum[i - w - 1] - 2 * lum[i - 1] - lum[i + w - 1] +
                    lum[i - w + 1] + 2 * lum[i + 1] + lum[i + w + 1]
                val gy = -lum[i - w - 1] - 2 * lum[i - w] - lum[i - w + 1] +
                    lum[i + w - 1] + 2 * lum[i + w] + lum[i + w + 1]
                val m = sqrt(gx * gx + gy * gy)
                mag[i] = m
                if (m > maxMag) maxMag = m
            }
        }
        val out = IntArray(w * h)
        for (i in out.indices) {
            val n = mag[i] / maxMag
            val a = if (n < 0.12f) 0 else (min(1f, (n - 0.12f) * 2.2f) * 255).toInt()
            out[i] = (a shl 24) or HUD_RGB
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }
}
