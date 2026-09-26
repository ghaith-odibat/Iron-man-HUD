package com.ghaith.ironhud

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import com.ghaith.ironhud.ai.Brief
import com.ghaith.ironhud.ai.ProviderId
import com.ghaith.ironhud.plane.CameraOptics
import com.ghaith.ironhud.vision.TrackedObject

enum class LockPhase { ANALYZING, LOCKED, OFFLINE }

data class FocusRequest(val id: Int, val x: Float, val y: Float)

enum class FocusStatus { FOCUSING, LOCKED, FAILED }

data class FocusMarker(val id: Int, val x: Float, val y: Float, val status: FocusStatus)

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
    /** Torch brightness: levels 1..[torchMaxLevel]; a max of 1 means the hardware only does on/off. */
    val torchMaxLevel: Int = 1,
    val torchLevel: Int = 1,
    val torchLevelTarget: Int? = null,
    /** Focus: the manual strip, a manual distance (0 = nearest … 1 = infinity; null = autofocus),
     *  a pending tap-to-focus request, its on-screen marker, and a counter that releases an AF lock. */
    val focusStrip: Boolean = false,
    val manualFocus: Float? = null,
    val focusRequest: FocusRequest? = null,
    val focusMarker: FocusMarker? = null,
    val focusResetSeq: Int = 0,
    /** Camera zoom as reported by the camera, and the range it supports. */
    val zoom: Float = 1f,
    val zoomMin: Float = 1f,
    val zoomMax: Float = 1f,
    /** Zoom the user asked for (pinch / buttons); the camera layer applies it. */
    val zoomTarget: Float? = null,
    /** Plane Mode: live aircraft overlays replace object scanning. */
    val planeMode: Boolean = false,
    /** ICAO hex of the tapped plane, and its AI brief card. */
    val selectedHex: String? = null,
    val planePanel: PanelUi? = null,
    val optics: CameraOptics = CameraOptics(),
    /** Plane Mode observer-location map is open / resolving the picked point. */
    val locationPicker: Boolean = false,
    val pickerBusy: Boolean = false,
    val toast: String? = null,
)
