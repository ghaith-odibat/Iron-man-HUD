package com.ghaith.ironhud

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import com.ghaith.ironhud.ai.Brief
import com.ghaith.ironhud.ai.ProviderId
import com.ghaith.ironhud.vision.TrackedObject

enum class LockPhase { ANALYZING, LOCKED, OFFLINE }

data class LockUi(val targetId: Int?, val box: Rect, val phase: LockPhase)

/** The brief panel (the Santa-Monica-Ferris-wheel box from the reference shot). */
data class PanelUi(
    val brief: Brief = Brief(),
    /** Instant on-device guess, e.g. "CUP · 87%". */
    val prelim: String? = null,
    val status: String = "ACQUIRING TARGET",
    val source: String? = null,
    val streaming: Boolean = true,
    val error: String? = null,
    val wireframe: ImageBitmap? = null,
    val fromCache: Boolean = false,
)

data class Uplink(
    val provider: ProviderId? = null,
    val keyIndex: Int = 0,
    val keyCount: Int = 0,
    val readyKeys: Int = 0,
    val totalKeys: Int = 0,
) {
    val label: String
        get() = when {
            totalKeys == 0 -> "UPLINK OFFLINE · NO KEYS"
            readyKeys == 0 -> "UPLINK HOLD · ALL KEYS COOLING"
            provider == null -> "UPLINK READY · $readyKeys/$totalKeys KEYS"
            else -> "UPLINK ${provider.display} · KEY $keyIndex/$keyCount · $readyKeys/$totalKeys READY"
        }
}

data class HudState(
    val targets: List<TrackedObject> = emptyList(),
    /** Auto-lock charge on the object under the reticle: its id and 0..1 progress. */
    val dwellId: Int? = null,
    val dwellProgress: Float = 0f,
    val lock: LockUi? = null,
    val panel: PanelUi? = null,
    val uplink: Uplink = Uplink(),
    /** Flashlight (camera torch) on. Mirrors the camera's real torch state. */
    val torch: Boolean = false,
    val toast: String? = null,
)
