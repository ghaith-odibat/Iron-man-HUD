package com.ghaith.ironhud.plane

import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Where each plane was drawn last frame, so a tap can pick the nearest one. */
class PlaneHitIndex {
    @Volatile private var points: List<Pair<String, Pair<Float, Float>>> = emptyList()

    fun update(points: List<Pair<String, Pair<Float, Float>>>) {
        this.points = points
    }

    fun nearest(x: Float, y: Float, maxDistPx: Float): String? =
        points.map { (hex, p) -> hex to hypot(p.first - x, p.second - y) }
            .filter { it.second <= maxDistPx }
            .minByOrNull { it.second }?.first
}

object PlanePrompt {
    /** The live facts handed to the AI for a plane brief. */
    fun user(a: Aircraft, viewer: GeoPoint?): String = buildString {
        append("Callsign: ").append(a.callsign ?: "unknown").append('\n')
        AircraftInfo.airline(a)?.let { append("Airline/operator: ").append(it).append('\n') }
        val type = AircraftInfo.typeName(a)
        append("Aircraft type: ").append(type ?: "unknown")
        a.typeCode?.let { append(" (ICAO ").append(it).append(')') }
        append('\n')
        a.registration?.let { append("Registration: ").append(it).append('\n') }
        AircraftInfo.categoryName(a.category)?.let { append("Category: ").append(it.lowercase(Locale.US)).append('\n') }
        append("Altitude: ").append(a.altFt.roundToInt()).append(" ft\n")
        a.gsKt?.let { append("Ground speed: ").append(it.roundToInt()).append(" kt\n") }
        a.trackDeg?.let { append("Track: ").append(it.roundToInt()).append("°\n") }
        a.vRateFpm?.let { append("Vertical rate: ").append(it.roundToInt()).append(" ft/min\n") }
        if (viewer != null) {
            val d = Geo.enu(viewer, GeoPoint(a.lat, a.lon, a.altM)).horizontal / 1000
            append("Distance from me: ").append("%.1f".format(Locale.US, d)).append(" km\n")
        }
        append("Brief me on this aircraft.")
    }

    /** Cache key: briefs are about the type and the operator, not the exact position. */
    fun cacheKey(a: Aircraft): String = "${a.typeCode}|${AircraftInfo.airline(a)}|${a.callsign}"
}
