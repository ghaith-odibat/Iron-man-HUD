package com.ghaith.ironhud.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class QuickLabel(val text: String, val confidence: Float)

/** ML Kit's bundled 400-label classifier: an instant, offline first guess shown while the AI thinks. */
class OnDeviceLabeler {
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.45f).build()
    )

    suspend fun label(bitmap: Bitmap): QuickLabel? = suspendCancellableCoroutine { cont ->
        labeler.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { labels ->
                val best = labels.maxByOrNull { it.confidence }
                cont.resume(best?.let { QuickLabel(it.text, it.confidence) })
            }
            .addOnFailureListener { cont.resume(null) }
    }

    fun close() = labeler.close()
}
