package com.ghaith.ironhud.vision

import androidx.compose.ui.geometry.Rect
import com.google.mlkit.vision.objects.DetectedObject

/** An on-device detection in PreviewView pixel coordinates. */
data class TrackedObject(val id: Int?, val box: Rect, val category: String?) {
    val area: Float get() = box.width * box.height

    companion object {
        fun from(o: DetectedObject): TrackedObject {
            val b = o.boundingBox
            return TrackedObject(
                id = o.trackingId,
                box = Rect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat()),
                category = o.labels.maxByOrNull { it.confidence }?.text,
            )
        }
    }
}
