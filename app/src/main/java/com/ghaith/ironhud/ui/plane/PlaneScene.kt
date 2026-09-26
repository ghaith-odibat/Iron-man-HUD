package com.ghaith.ironhud.ui.plane

import com.ghaith.ironhud.PanelUi
import com.ghaith.ironhud.airspace.AirspaceState
import com.ghaith.ironhud.plane.Aircraft
import com.ghaith.ironhud.plane.CameraOptics
import com.ghaith.ironhud.plane.PlaneHitIndex
import com.ghaith.ironhud.ui.hud.SensorHub
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Everything the Plane Mode layers need for one frame. */
class PlaneScene(
    val airspace: AirspaceState,
    val sensors: SensorHub,
    val optics: CameraOptics,
    val zoom: Float,
    val selectedHex: String?,
    val panel: PanelUi?,
    val hits: PlaneHitIndex?,
    val clock: () -> Long = System::currentTimeMillis,
) {
    val selected: Aircraft? get() = selectedHex?.let { hex -> airspace.aircraft.firstOrNull { it.hex == hex } }
}

object PlaneFormat {
    fun altitude(a: Aircraft): String {
        val ft = a.altFt
        return if (ft >= 10_000) "FL${(ft / 100).roundToInt().toString().padStart(3, '0')}"
        else "${((ft / 100).roundToInt() * 100)} FT"
    }

    fun speed(a: Aircraft) = a.gsKt?.let { "${it.roundToInt()} KT" } ?: "-- KT"
    fun heading(a: Aircraft) = a.trackDeg?.let { "${it.roundToInt().mod(360).toString().padStart(3, '0')}°" } ?: "---°"
    fun distance(km: Double) = if (km < 10) "%.1f KM".format(Locale.US, km) else "${km.roundToInt()} KM"

    fun verticalRate(a: Aircraft): String {
        val v = a.vRateFpm ?: return "LEVEL"
        return when {
            abs(v) < 200 -> "LEVEL"
            v > 0 -> "▲ ${v.roundToInt()} FPM"
            else -> "▼ ${(-v).roundToInt()} FPM"
        }
    }

    fun trend(a: Aircraft): String = when {
        (a.vRateFpm ?: 0.0) > 300 -> " ▲"
        (a.vRateFpm ?: 0.0) < -300 -> " ▼"
        else -> ""
    }
}
