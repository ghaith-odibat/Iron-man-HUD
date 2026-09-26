package com.ghaith.ironhud.plane

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** A point in WGS-84 with altitude in metres. */
data class GeoPoint(val lat: Double, val lon: Double, val altM: Double)

/** East / North / Up offset in metres from the viewer. */
data class Enu(val e: Double, val n: Double, val u: Double) {
    val horizontal: Double get() = hypot(e, n)
    val range: Double get() = sqrt(e * e + n * n + u * u)

    operator fun plus(o: Enu) = Enu(e + o.e, n + o.n, u + o.u)
    operator fun times(k: Double) = Enu(e * k, n * k, u * k)
}

object Geo {
    const val EARTH_RADIUS_M = 6_371_000.0
    const val FT_TO_M = 0.3048
    const val KT_TO_MS = 0.514444
    const val FPM_TO_MS = 0.00508
    const val NM_TO_KM = 1.852

    fun rad(deg: Double) = deg * PI / 180.0
    fun deg(rad: Double) = rad * 180.0 / PI

    /**
     * Offset of [target] from [viewer]. A local flat-earth approximation is plenty within the ~50 km
     * we care about, plus the drop of the Earth's curvature so distant planes sit lower, as they do.
     */
    fun enu(viewer: GeoPoint, target: GeoPoint): Enu {
        val meanLat = rad((viewer.lat + target.lat) / 2)
        val n = rad(target.lat - viewer.lat) * EARTH_RADIUS_M
        val e = rad(wrapLon(target.lon - viewer.lon)) * EARTH_RADIUS_M * cos(meanLat)
        val d = hypot(e, n)
        val u = target.altM - viewer.altM - d * d / (2 * EARTH_RADIUS_M)
        return Enu(e, n, u)
    }

    /** True bearing from the viewer, 0-360° clockwise from north. */
    fun bearingDeg(v: Enu): Double = (deg(atan2(v.e, v.n)) + 360.0) % 360.0

    fun elevationDeg(v: Enu): Double = deg(atan2(v.u, v.horizontal))

    /** Moves [distM] metres from a point along [bearingDeg]. */
    fun destination(lat: Double, lon: Double, bearingDeg: Double, distM: Double): Pair<Double, Double> {
        val b = rad(bearingDeg)
        val dLat = distM * cos(b) / EARTH_RADIUS_M
        val dLon = distM * sin(b) / (EARTH_RADIUS_M * cos(rad(lat)))
        return (lat + deg(dLat)) to (lon + deg(dLon))
    }

    /**
     * The rotation-vector sensor is referenced to *magnetic* north; ADS-B positions are true.
     * Rotates a true-north ENU vector into the magnetic frame ([declinationDeg] east-positive).
     */
    fun toMagnetic(v: Enu, declinationDeg: Double): Enu {
        val d = rad(declinationDeg)
        return Enu(v.e * cos(d) - v.n * sin(d), v.e * sin(d) + v.n * cos(d), v.u)
    }

    private fun wrapLon(d: Double): Double = ((d + 540.0) % 360.0) - 180.0
}
