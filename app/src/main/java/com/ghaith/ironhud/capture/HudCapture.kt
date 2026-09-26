package com.ghaith.ironhud.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.provider.MediaStore
import com.ghaith.ironhud.ui.theme.Hud
import com.ghaith.ironhud.vision.NightVisionBitmaps
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Composites the camera frame and the HUD overlay into one JPEG in Pictures/IronHUD. */
object HudCapture {

    /** [nightGain] non-null: the camera frame gets the night-vision intensifier look instead of the tint. */
    fun compose(camera: Bitmap?, overlay: Bitmap, tint: Boolean, nightGain: Double? = null): Bitmap {
        val out = Bitmap.createBitmap(overlay.width, overlay.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(android.graphics.Color.BLACK)
        if (camera != null) {
            val frame = if (nightGain != null) NightVisionBitmaps.toHud(camera, nightGain, seed = camera.generationId) else camera
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                if (tint && nightGain == null) colorFilter = ColorMatrixColorFilter(Hud.TINT_MATRIX)
            }
            canvas.drawBitmap(frame, null, Rect(0, 0, out.width, out.height), paint)
        }
        // Compose layers can hand back HARDWARE bitmaps, which a software canvas can't draw.
        val soft = if (overlay.config == Bitmap.Config.HARDWARE) overlay.copy(Bitmap.Config.ARGB_8888, false) else overlay
        canvas.drawBitmap(soft, 0f, 0f, null)
        return out
    }

    /** Returns true when saved. */
    fun save(context: Context, bitmap: Bitmap): Boolean {
        val name = "IronHUD_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/IronHUD")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) } ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }
}
