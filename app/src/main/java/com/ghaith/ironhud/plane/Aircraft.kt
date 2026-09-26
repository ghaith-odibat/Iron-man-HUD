package com.ghaith.ironhud.plane

/** One airborne aircraft from a live ADS-B feed, as of [posTimeMs]. */
data class Aircraft(
    val hex: String,
    val callsign: String?,
    val registration: String?,
    val typeCode: String?,
    /** Long type name when the feed has one, e.g. "AIRBUS A-320neo". */
    val description: String?,
    val operator: String?,
    /** ADS-B emitter category, e.g. "A3" (large) or "A7" (rotorcraft). */
    val category: String?,
    val lat: Double,
    val lon: Double,
    val altM: Double,
    val gsKt: Double?,
    val trackDeg: Double?,
    val vRateFpm: Double?,
    val squawk: String?,
    /** When lat/lon were measured (epoch millis). */
    val posTimeMs: Long,
    val source: FeedSource,
) {
    /** What the HUD calls it: the flight callsign, else registration, else the ICAO hex. */
    val label: String
        get() = callsign?.takeIf { it.isNotBlank() } ?: registration?.takeIf { it.isNotBlank() } ?: hex.uppercase()

    val altFt: Double get() = altM / Geo.FT_TO_M

    /** Climb angle from vertical rate and ground speed, degrees. */
    val pitchDeg: Double
        get() {
            val gs = (gsKt ?: 0.0) * Geo.KT_TO_MS
            val vs = (vRateFpm ?: 0.0) * Geo.FPM_TO_MS
            return if (gs < 1.0) 0.0 else Geo.deg(kotlin.math.atan2(vs, gs))
        }

    /**
     * Dead-reckoned position at [nowMs] so models keep gliding between feed polls
     * (capped: a stale track shouldn't fly off on its own).
     */
    fun positionAt(nowMs: Long): GeoPoint {
        val dt = ((nowMs - posTimeMs) / 1000.0).coerceIn(0.0, MAX_EXTRAPOLATION_S)
        val track = trackDeg
        val gs = gsKt
        val alt = altM + (vRateFpm ?: 0.0) * Geo.FPM_TO_MS * dt
        if (track == null || gs == null || dt == 0.0) return GeoPoint(lat, lon, alt)
        val (la, lo) = Geo.destination(lat, lon, track, gs * Geo.KT_TO_MS * dt)
        return GeoPoint(la, lo, alt)
    }

    companion object {
        const val MAX_EXTRAPOLATION_S = 60.0
    }
}
