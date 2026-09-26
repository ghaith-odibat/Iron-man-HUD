package com.ghaith.ironhud.plane

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A wireframe: [vertices] as x,y,z triples, [edges] as index pairs. Model axes: x = right wing,
 * y = nose, z = up; the airframe is about 1 unit long, centred on the origin.
 */
class WireMesh(val vertices: FloatArray, val edges: IntArray) {
    val vertexCount: Int get() = vertices.size / 3
}

enum class ModelKind(val label: String) {
    AIRLINER("NARROW-BODY JET"),
    WIDEBODY("WIDE-BODY JET"),
    QUAD("FOUR-ENGINE HEAVY"),
    BIZJET("BUSINESS JET"),
    LIGHT("LIGHT AIRCRAFT"),
    HELI("ROTORCRAFT"),
}

object AircraftModels {

    fun kindFor(a: Aircraft): ModelKind {
        val t = a.typeCode?.uppercase()
        if (t != null) {
            if (t in QUAD_TYPES) return ModelKind.QUAD
            if (t in WIDE_TYPES) return ModelKind.WIDEBODY
            if (t in BIZJET_TYPES) return ModelKind.BIZJET
            if (t in HELI_TYPES) return ModelKind.HELI
            if (t in LIGHT_TYPES) return ModelKind.LIGHT
        }
        return when (a.category?.uppercase()) {
            "A7" -> ModelKind.HELI
            "A1", "B1", "B2", "B4", "B6" -> ModelKind.LIGHT
            "A5" -> ModelKind.WIDEBODY
            else -> ModelKind.AIRLINER
        }
    }

    fun mesh(kind: ModelKind): WireMesh = CACHE.getValue(kind)

    /**
     * Orients a model in the world: heading [trackDeg] (clockwise from north, in the same frame as
     * the output) and climb [pitchDeg], scaled by [scale] metres per model unit, around [center].
     */
    fun worldVertices(mesh: WireMesh, trackDeg: Double, pitchDeg: Double, scale: Double, center: Enu): Array<Enu> {
        val h = Geo.rad(trackDeg)
        val p = Geo.rad(pitchDeg)
        val sh = sin(h)
        val ch = cos(h)
        val sp = sin(p)
        val cp = cos(p)
        // Model axes expressed in ENU.
        val rx = doubleArrayOf(ch, -sh, 0.0)
        val fy = doubleArrayOf(sh * cp, ch * cp, sp)
        val uz = doubleArrayOf(-sh * sp, -ch * sp, cp)
        val v = mesh.vertices
        return Array(mesh.vertexCount) { i ->
            val x = v[i * 3] * scale
            val y = v[i * 3 + 1] * scale
            val z = v[i * 3 + 2] * scale
            Enu(
                center.e + x * rx[0] + y * fy[0] + z * uz[0],
                center.n + x * rx[1] + y * fy[1] + z * uz[1],
                center.u + x * rx[2] + y * fy[2] + z * uz[2],
            )
        }
    }

    // ---- model construction --------------------------------------------------------------------

    private class Builder {
        private val v = ArrayList<Float>()
        private val e = ArrayList<Int>()

        fun vert(x: Double, y: Double, z: Double): Int {
            v += x.toFloat(); v += y.toFloat(); v += z.toFloat()
            return v.size / 3 - 1
        }

        fun line(a: Int, b: Int) {
            e += a; e += b
        }

        fun polyline(ids: List<Int>, closed: Boolean = false) {
            for (i in 0 until ids.size - 1) line(ids[i], ids[i + 1])
            if (closed && ids.size > 2) line(ids.last(), ids.first())
        }

        /** A ring in the x–z plane at [y], centred at ([cx], [cz]). */
        fun ringXZ(y: Double, cx: Double, cz: Double, r: Double, n: Int): List<Int> =
            (0 until n).map { k ->
                val a = 2 * PI * k / n
                vert(cx + r * cos(a), y, cz + r * sin(a))
            }.also { polyline(it, closed = true) }

        /** A ring in the x–y plane (horizontal, e.g. a rotor disc) at height [z]. */
        fun ringXY(cx: Double, cy: Double, z: Double, r: Double, n: Int): List<Int> =
            (0 until n).map { k ->
                val a = 2 * PI * k / n
                vert(cx + r * cos(a), cy + r * sin(a), z)
            }.also { polyline(it, closed = true) }

        /** A ring in the y–z plane (a disc facing sideways, e.g. a tail rotor). */
        fun ringYZ(x: Double, cy: Double, cz: Double, r: Double, n: Int): List<Int> =
            (0 until n).map { k ->
                val a = 2 * PI * k / n
                vert(x, cy + r * cos(a), cz + r * sin(a))
            }.also { polyline(it, closed = true) }

        /** Fuselage: rings at each (y, radius, centreZ) station, joined by longerons; pointed nose. */
        fun fuselage(stations: List<Triple<Double, Double, Double>>, sides: Int = 8) {
            val noseTip = stations.first()
            val nose = vert(0.0, noseTip.first, noseTip.third)
            var prev: List<Int>? = null
            for ((y, r, cz) in stations.drop(1)) {
                val ring = ringXZ(y, 0.0, cz, r, sides)
                if (prev == null) ring.forEach { line(nose, it) } else ring.indices.forEach { line(prev!![it], ring[it]) }
                prev = ring
            }
        }

        /** A flat surface outline (wing, tailplane), mirrored left/right when [mirror]. */
        fun surface(rootLe: Triple<Double, Double, Double>, rootTe: Triple<Double, Double, Double>,
                    tipLe: Triple<Double, Double, Double>, tipTe: Triple<Double, Double, Double>, mirror: Boolean = true) {
            for (side in if (mirror) listOf(1.0, -1.0) else listOf(1.0)) {
                val a = vert(rootLe.first * side, rootLe.second, rootLe.third)
                val b = vert(tipLe.first * side, tipLe.second, tipLe.third)
                val c = vert(tipTe.first * side, tipTe.second, tipTe.third)
                val d = vert(rootTe.first * side, rootTe.second, rootTe.third)
                polyline(listOf(a, b, c, d), closed = true)
                // Spar line for a bit of structure.
                val m1 = vert((rootLe.first + rootTe.first) / 2 * side, (rootLe.second * 0.6 + rootTe.second * 0.4), rootLe.third)
                val m2 = vert((tipLe.first + tipTe.first) / 2 * side, (tipLe.second * 0.6 + tipTe.second * 0.4), tipLe.third)
                line(m1, m2)
            }
        }

        /** Engine nacelle along y: front and back rings joined by lines. */
        fun nacelle(x: Double, yFront: Double, yBack: Double, z: Double, r: Double) {
            val front = ringXZ(yFront, x, z, r, 6)
            val back = ringXZ(yBack, x, z, r * 0.8, 6)
            front.indices.forEach { line(front[it], back[it]) }
        }

        fun build() = WireMesh(v.toFloatArray(), e.toIntArray())
    }

    private fun t(a: Double, b: Double, c: Double) = Triple(a, b, c)

    private fun jet(radius: Double, halfSpan: Double, engines: List<Double>, engineR: Double): WireMesh = Builder().apply {
        fuselage(listOf(
            t(0.50, 0.0, 0.0), t(0.46, radius * 0.5, -0.005), t(0.40, radius * 0.85, 0.0), t(0.30, radius, 0.0),
            t(0.05, radius, 0.0), t(-0.20, radius, 0.0), t(-0.34, radius * 0.8, 0.012), t(-0.44, radius * 0.5, 0.025),
            t(-0.50, radius * 0.18, 0.035),
        ))
        surface(t(radius, 0.10, -radius * 0.4), t(radius, -0.10, -radius * 0.4),
            t(halfSpan, -0.16, 0.02), t(halfSpan, -0.22, 0.02))
        surface(t(0.02, -0.36, 0.025), t(0.02, -0.47, 0.025), t(0.17, -0.45, 0.035), t(0.17, -0.50, 0.035))
        surface(t(0.0, -0.33, radius * 0.9), t(0.0, -0.48, radius * 0.9), t(0.0, -0.45, 0.20), t(0.0, -0.51, 0.20), mirror = false)
        for (x in engines) {
            nacelle(x, 0.10 - x * 0.25, -0.02 - x * 0.25, -radius * 0.9, engineR)
            nacelle(-x, 0.10 - x * 0.25, -0.02 - x * 0.25, -radius * 0.9, engineR)
        }
    }.build()

    private fun bizjet(): WireMesh = Builder().apply {
        fuselage(listOf(
            t(0.50, 0.0, 0.0), t(0.44, 0.03, 0.0), t(0.35, 0.05, 0.005), t(0.15, 0.055, 0.005),
            t(-0.15, 0.05, 0.01), t(-0.35, 0.035, 0.02), t(-0.50, 0.012, 0.03),
        ))
        surface(t(0.05, 0.0, -0.03), t(0.05, -0.16, -0.03), t(0.42, -0.14, 0.0), t(0.42, -0.20, 0.0))
        // T-tail: fin with the tailplane on top.
        surface(t(0.0, -0.32, 0.04), t(0.0, -0.46, 0.04), t(0.0, -0.44, 0.20), t(0.0, -0.52, 0.20), mirror = false)
        surface(t(0.0, -0.43, 0.20), t(0.0, -0.52, 0.20), t(0.16, -0.49, 0.21), t(0.16, -0.54, 0.21))
        // Rear-mounted engines.
        nacelle(0.085, -0.20, -0.34, 0.045, 0.028)
        nacelle(-0.085, -0.20, -0.34, 0.045, 0.028)
    }.build()

    private fun light(): WireMesh = Builder().apply {
        fuselage(listOf(
            t(0.50, 0.0, 0.0), t(0.46, 0.05, 0.0), t(0.30, 0.075, 0.01), t(0.05, 0.075, 0.02),
            t(-0.20, 0.045, 0.03), t(-0.50, 0.015, 0.045),
        ))
        // High, straight wing.
        surface(t(0.0, 0.20, 0.10), t(0.0, 0.02, 0.10), t(0.66, 0.20, 0.11), t(0.66, 0.04, 0.11))
        surface(t(0.0, -0.40, 0.045), t(0.0, -0.50, 0.045), t(0.20, -0.42, 0.045), t(0.20, -0.50, 0.045))
        surface(t(0.0, -0.36, 0.06), t(0.0, -0.50, 0.06), t(0.0, -0.46, 0.22), t(0.0, -0.51, 0.22), mirror = false)
        // Propeller disc and landing gear.
        ringXZ(0.51, 0.0, 0.0, 0.13, 12)
        val hub = vert(0.0, 0.52, 0.0)
        for (side in listOf(1.0, -1.0)) line(hub, vert(0.13 * side, 0.51, 0.0))
        for (side in listOf(1.0, -1.0)) line(vert(0.05 * side, 0.12, -0.06), vert(0.12 * side, 0.12, -0.16))
        line(vert(0.0, 0.40, -0.06), vert(0.0, 0.40, -0.16))
    }.build()

    private fun heli(): WireMesh = Builder().apply {
        fuselage(listOf(
            t(0.34, 0.0, -0.02), t(0.30, 0.07, -0.02), t(0.18, 0.12, 0.0), t(0.0, 0.12, 0.0),
            t(-0.10, 0.07, 0.02), t(-0.20, 0.025, 0.04), t(-0.50, 0.015, 0.06),
        ), sides = 8)
        // Main rotor disc with two blades, mast, tail fin + rotor, skids.
        val disc = ringXY(0.0, 0.0, 0.20, 0.52, 20)
        line(disc[0], disc[10])
        line(disc[5], disc[15])
        line(vert(0.0, 0.0, 0.12), vert(0.0, 0.0, 0.20))
        surface(t(0.0, -0.44, 0.06), t(0.0, -0.52, 0.06), t(0.0, -0.49, 0.16), t(0.0, -0.53, 0.16), mirror = false)
        ringYZ(0.03, -0.50, 0.10, 0.08, 10)
        for (side in listOf(1.0, -1.0)) {
            line(vert(0.09 * side, 0.22, -0.16), vert(0.09 * side, -0.14, -0.16))
            line(vert(0.07 * side, 0.12, -0.10), vert(0.09 * side, 0.12, -0.16))
            line(vert(0.07 * side, -0.06, -0.10), vert(0.09 * side, -0.06, -0.16))
        }
    }.build()

    private val CACHE: Map<ModelKind, WireMesh> by lazy {
        mapOf(
            ModelKind.AIRLINER to jet(radius = 0.055, halfSpan = 0.48, engines = listOf(0.17), engineR = 0.026),
            ModelKind.WIDEBODY to jet(radius = 0.065, halfSpan = 0.50, engines = listOf(0.19), engineR = 0.036),
            ModelKind.QUAD to jet(radius = 0.07, halfSpan = 0.50, engines = listOf(0.15, 0.30), engineR = 0.028),
            ModelKind.BIZJET to bizjet(),
            ModelKind.LIGHT to light(),
            ModelKind.HELI to heli(),
        )
    }

    private val QUAD_TYPES = setOf(
        "A388", "A380", "B741", "B742", "B743", "B744", "B748", "B74S", "B74R", "A342", "A343", "A345", "A346",
        "IL96", "A124", "A225", "C17", "C5M", "K35R", "E3TF", "A400", "C130", "C30J", "IL76", "B52",
    )
    private val WIDE_TYPES = setOf(
        "A306", "A30B", "A310", "A332", "A333", "A337", "A338", "A339", "A359", "A35K", "B762", "B763", "B764",
        "B772", "B773", "B77L", "B77W", "B778", "B779", "B788", "B789", "B78X", "MD11", "DC10", "KC10", "K46", "IL62",
    )
    private val BIZJET_TYPES = setOf(
        "GLF4", "GLF5", "GLF6", "GA5C", "GA6C", "GA7C", "GA8C", "GLEX", "GL5T", "GL7T", "CL30", "CL35", "CL60",
        "C25A", "C25B", "C25C", "C510", "C525", "C550", "C560", "C56X", "C650", "C680", "C68A", "C700", "C750",
        "E50P", "E55P", "E545", "E550", "F2TH", "F900", "FA50", "FA7X", "FA8X", "FA6X", "LJ35", "LJ45", "LJ60",
        "LJ75", "H25B", "HDJT", "PC24", "SF50", "PRM1", "G280", "ASTR", "BE40",
    )
    private val HELI_TYPES = setOf(
        "EC20", "EC25", "EC30", "EC35", "EC45", "EC55", "EC75", "H160", "AS32", "AS50", "AS55", "AS65", "A109",
        "A119", "A139", "A169", "A189", "B06", "B407", "B412", "B429", "B505", "R22", "R44", "R66", "S76", "S92",
        "H60", "H64", "H47", "UH1", "MI8", "MI17", "NH90", "EH10",
    )
    private val LIGHT_TYPES = setOf(
        "C150", "C152", "C162", "C170", "C172", "C177", "C182", "C206", "C208", "C210", "P28A", "P28B", "P28R",
        "PA18", "PA24", "PA32", "PA34", "PA44", "PA46", "SR20", "SR22", "DA20", "DA40", "DA42", "DA62", "BE33",
        "BE35", "BE36", "BE58", "M20P", "M20T", "TBM7", "TBM8", "TBM9", "PC6T", "PC12", "PC21", "PC7", "PC9",
        "AT3T", "GLID", "ULAC", "BALL", "C42", "CRUZ", "EV97", "DR40",
    )
}
