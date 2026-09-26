package com.ghaith.ironhud.vision

import android.graphics.Bitmap
import com.ghaith.ironhud.nightvision.NightVision

/** [NightVision]'s CPU paths on Android bitmaps. */
object NightVisionBitmaps {

    /** Brighter, still in colour: what a night-vision scan sends to the AI. */
    fun brighten(src: Bitmap, gain: Double): Bitmap = transform(src) { NightVision.brighten(it, gain) }

    /** The intensifier look in HUD blue, with grain: the camera layer of a night-vision SNAP. */
    fun toHud(src: Bitmap, gain: Double, seed: Int = 1): Bitmap = transform(src) { NightVision.toHud(it, gain, seed) }

    private inline fun transform(src: Bitmap, f: (IntArray) -> Unit): Bitmap {
        val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
        val w = soft.width
        val h = soft.height
        val px = IntArray(w * h)
        soft.getPixels(px, 0, w, 0, 0, w, h)
        f(px)
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}
