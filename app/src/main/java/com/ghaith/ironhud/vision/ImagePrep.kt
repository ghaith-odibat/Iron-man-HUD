package com.ghaith.ironhud.vision

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** What a lock sends up the pipeline: the crop and its (small) upload JPEG. */
class PreparedTarget(val crop: Bitmap, val jpeg: ByteArray)

object ImagePrep {
    private const val PADDING = 0.15f
    private const val MIN_SIDE = 96
    const val UPLOAD_MAX_SIDE = 512
    private const val JPEG_QUALITY = 80

    /**
     * Crops [box] (in view pixels of a view sized [viewW]x[viewH]) out of [frame] with some context
     * padding, and downsizes it: a ~40 KB JPEG uploads in a fraction of the time of a full frame.
     * [enhance], when given, transforms the crop first (night-vision brightening).
     */
    fun prepare(frame: Bitmap, box: Rect, viewW: Int, viewH: Int, enhance: ((Bitmap) -> Bitmap)? = null): PreparedTarget {
        val sx = frame.width / viewW.coerceAtLeast(1).toFloat()
        val sy = frame.height / viewH.coerceAtLeast(1).toFloat()
        val padX = box.width * PADDING
        val padY = box.height * PADDING
        var left = ((box.left - padX) * sx).roundToInt()
        var top = ((box.top - padY) * sy).roundToInt()
        var right = ((box.right + padX) * sx).roundToInt()
        var bottom = ((box.bottom + padY) * sy).roundToInt()

        // Grow tiny boxes around their centre so the model has something to look at.
        if (right - left < MIN_SIDE) {
            val c = (left + right) / 2
            left = c - MIN_SIDE / 2
            right = c + MIN_SIDE / 2
        }
        if (bottom - top < MIN_SIDE) {
            val c = (top + bottom) / 2
            top = c - MIN_SIDE / 2
            bottom = c + MIN_SIDE / 2
        }
        left = left.coerceIn(0, frame.width - 1)
        top = top.coerceIn(0, frame.height - 1)
        right = right.coerceIn(left + 1, frame.width)
        bottom = bottom.coerceIn(top + 1, frame.height)

        val raw = Bitmap.createBitmap(frame, left, top, right - left, bottom - top)
        // Night vision brightens just the crop (fast) so the AI and labeler can see in the dark.
        val crop = enhance?.invoke(raw) ?: raw
        val upload = scaleToMax(crop, UPLOAD_MAX_SIDE)
        val jpeg = ByteArrayOutputStream(64 * 1024).use { out ->
            upload.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        if (upload !== crop) upload.recycle()
        return PreparedTarget(crop, jpeg)
    }

    fun scaleToMax(src: Bitmap, maxSide: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxSide) return src
        val k = maxSide / longest.toFloat()
        return Bitmap.createScaledBitmap(
            src,
            (src.width * k).roundToInt().coerceAtLeast(1),
            (src.height * k).roundToInt().coerceAtLeast(1),
            true,
        )
    }
}
