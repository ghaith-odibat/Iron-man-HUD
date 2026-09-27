package com.ghaith.ironhud.plane.models

import com.ghaith.ironhud.plane.Lod
import com.ghaith.ironhud.plane.Spin
import com.ghaith.ironhud.plane.WireMesh
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/** A point in airframe space, metres: x towards the right wingtip, s aft from the nose tip, z up. */
data class P3(val x: Double, val s: Double, val z: Double) {
    operator fun plus(o: P3) = P3(x + o.x, s + o.s, z + o.z)
    operator fun minus(o: P3) = P3(x - o.x, s - o.s, z - o.z)
    operator fun times(k: Double) = P3(x * k, s * k, z * k)
    val length: Double get() = sqrt(x * x + s * s + z * z)
    fun unit(): P3 = length.let { if (it < 1e-9) this else this * (1.0 / it) }
    fun cross(o: P3) = P3(s * o.z - z * o.s, z * o.x - x * o.z, x * o.s - s * o.x)
    fun flipX() = P3(-x, s, z)

    companion object {
        val AFT = P3(0.0, 1.0, 0.0)
        val FWD = P3(0.0, -1.0, 0.0)
        val UP = P3(0.0, 0.0, 1.0)
        val RIGHT = P3(1.0, 0.0, 0.0)
    }
}

internal fun rad(deg: Double) = deg * PI / 180.0

/** One piece of an airframe, in metres. */
sealed interface Part

/**
 * A cross-section of a lofted body at station [s]: half-width, centre height, and the heights of
 * the upper and lower halves ([topW] lets the upper half bulge wider, as on a cargo lobe).
 */
data class Section(val s: Double, val halfW: Double, val zc: Double, val up: Double, val down: Double = up, val topW: Double = halfW) {
    val collapsed: Boolean get() = halfW < 1e-3 && up < 1e-3 && down < 1e-3
    val top: Double get() = zc + up
    val bottom: Double get() = zc - down
}

/** A row of cabin windows [angleDeg] above the side line (both sides). */
data class WindowRow(val s0: Double, val s1: Double, val angleDeg: Double, val pitch: Double = 0.53, val size: Double = 0.26)

/**
 * A window or frame drawn on a body's surface. Corners are (u, deg): u runs 0..1 through the
 * glazing zone of a [Cockpit], deg goes round the section from the side line (0) over the top (90)
 * to the far side (180), negative below the side line. Panes are repeated on the left unless they
 * cross the centreline ([mirror] = false); [closed] = false draws an open frame (canopy bow, sill);
 * [essential] panes are also drawn on live, mid-detail models.
 */
data class Pane(
    val corners: List<Pair<Double, Double>>,
    val mirror: Boolean = true,
    val closed: Boolean = true,
    val essential: Boolean = false,
)

/** A type's flight-deck glazing: its panes, an optional faint outline (the A350's mask), and a spec-sheet label. */
class Windshield(val label: String, val panes: List<Pane>, val mask: Pane? = null)

/** The glazing zone, stations [s] to [s] + [len], and the windshield in it. */
data class Cockpit(val s: Double, val len: Double, val shield: Windshield)

/** A lofted shell: fuselage, pod, tip tank, canopy. [box] > 2 squares the sections off. */
data class Body(
    val sections: List<Section>,
    val x: Double = 0.0,
    val mirror: Boolean = false,
    val box: Double = 2.0,
    val sides: Int = 12,
    val windows: List<WindowRow> = emptyList(),
    val cockpit: Cockpit? = null,
    /** False for glass (canopies): no frames at every station, just the outline and the glazing's own frames. */
    val rings: Boolean = true,
) : Part {
    val length: Double get() = sections.last().s - sections.first().s

    fun at(s: Double): Section {
        val list = sections
        if (s <= list.first().s) return list.first().copy(s = s)
        if (s >= list.last().s) return list.last().copy(s = s)
        val i = list.indexOfLast { it.s <= s }
        val a = list[i]
        val b = list[i + 1]
        val t = if (b.s - a.s < 1e-9) 0.0 else (s - a.s) / (b.s - a.s)
        fun l(p: Double, q: Double) = p + (q - p) * t
        return Section(s, l(a.halfW, b.halfW), l(a.zc, b.zc), l(a.up, b.up), l(a.down, b.down), l(a.topW, b.topW))
    }

    /** Surface point at [angle] (radians, 0 = right side, π/2 = top). */
    fun point(sec: Section, angle: Double): P3 {
        val c = cos(angle)
        val sn = sin(angle)
        val e = 2.0 / box
        val px = (if (sn >= 0) sec.topW else sec.halfW) * sp(c, e)
        val pz = sec.zc + (if (sn >= 0) sec.up else sec.down) * sp(sn, e)
        return P3(x + px, sec.s, pz)
    }

    private fun sp(v: Double, e: Double) = sign(v) * abs(v).pow(e)
}

/** One rib of a lifting surface: leading-edge point, chord (running aft) and thickness. */
data class Rib(val le: P3, val chord: Double, val t: Double)

/** Wing, tailplane, fin, canard or winglet: ribs joined along the span, mirrored for a pair. */
data class Surface(val ribs: List<Rib>, val mirror: Boolean = true, val hinge: Boolean = true) : Part

enum class NacelleStyle { FAN, JET, PROP, POD }

/** An engine nacelle or pod along the s axis, [front] = centre of its intake. */
data class Nacelle(
    val front: P3,
    val len: Double,
    val dia: Double,
    val style: NacelleStyle = NacelleStyle.FAN,
    val mirror: Boolean = true,
    val chevrons: Boolean = false,
    val flat: Boolean = false,
) : Part

/** Propeller or rotor: blades around [axis] (pointing the way the spinner points). */
data class Rotor(
    val hub: P3,
    val axis: P3,
    val dia: Double,
    val blades: Int,
    val chord: Double = dia * 0.07,
    val rev: Double = 2.0,
    val spinner: Double = 0.0,
    val mast: Double = 0.0,
    val disc: Boolean = true,
    val duct: Double = 0.0,
    val droop: Double = 0.0,
    val phase: Double = 0.0,
    val mirror: Boolean = false,
) : Part

/** One or two rings around [axis]: wheels, ducts, radomes. */
data class Drum(
    val center: P3,
    val axis: P3,
    val r: Double,
    val depth: Double = 0.0,
    val sides: Int = 12,
    val rev: Double = 0.0,
    val faint: Boolean = false,
    val spokes: Int = 0,
    val mirror: Boolean = false,
) : Part

/** Loose lines: struts, skids, pylons, fences, booms. */
data class Wire(
    val points: List<P3>,
    val mirror: Boolean = false,
    val closed: Boolean = false,
    val faint: Boolean = false,
    /** The coarsest level of detail that still draws it. */
    val coarsest: Lod = Lod.LITE,
) : Part

/** Turns parts into a normalised [WireMesh] at one level of detail. */
internal class Mesher(private val lod: Lod) {
    private val pts = ArrayList<P3>()
    private val solid = ArrayList<Int>()
    private val dim = ArrayList<Int>()
    private val bold = ArrayList<Int>()
    private val spins = ArrayList<SpinRaw>()

    private class SpinRaw(val first: Int, val end: Int, val origin: P3, val axis: P3, val rev: Double)

    private val detail get() = lod == Lod.DETAIL
    private val lite get() = lod == Lod.LITE

    private fun v(p: P3): Int {
        pts += p
        return pts.size - 1
    }

    private fun line(a: Int, b: Int, faint: Boolean = false, accent: Boolean = false) {
        val l = when {
            accent -> bold
            faint -> dim
            else -> solid
        }
        l += a
        l += b
    }

    private fun path(ids: List<Int>, closed: Boolean = false, faint: Boolean = false, accent: Boolean = false) {
        for (i in 0 until ids.size - 1) line(ids[i], ids[i + 1], faint, accent)
        if (closed && ids.size > 2) line(ids.last(), ids.first(), faint, accent)
    }

    private fun sides(mirror: Boolean) = if (mirror) doubleArrayOf(1.0, -1.0) else doubleArrayOf(1.0)
    private fun P3.side(k: Double) = if (k < 0) flipX() else this

    /** Two unit vectors perpendicular to [a] and to each other. */
    private fun basis(a: P3): Pair<P3, P3> {
        val ref = if (abs(a.z) > 0.9) P3.RIGHT else P3.UP
        val u = a.cross(ref).unit()
        return u to a.cross(u).unit()
    }

    fun build(parts: List<Part>): WireMesh {
        for (p in parts) when (p) {
            is Body -> body(p)
            is Surface -> surface(p)
            is Nacelle -> nacelle(p)
            is Rotor -> rotor(p)
            is Drum -> drum(p)
            is Wire -> wire(p)
        }
        var minX = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var minS = Double.MAX_VALUE
        var maxS = -Double.MAX_VALUE
        var minZ = Double.MAX_VALUE
        var maxZ = -Double.MAX_VALUE
        for (p in pts) {
            minX = minOf(minX, p.x); maxX = maxOf(maxX, p.x)
            minS = minOf(minS, p.s); maxS = maxOf(maxS, p.s)
            minZ = minOf(minZ, p.z); maxZ = maxOf(maxZ, p.z)
        }
        val extent = maxOf(maxX - minX, maxS - minS, maxZ - minZ).coerceAtLeast(1e-3)
        val cx = (minX + maxX) / 2
        val cs = (minS + maxS) / 2
        fun model(p: P3) = floatArrayOf(((p.x - cx) / extent).toFloat(), ((cs - p.s) / extent).toFloat(), (p.z / extent).toFloat())
        val verts = FloatArray(pts.size * 3)
        pts.forEachIndexed { i, p -> model(p).copyInto(verts, i * 3) }
        return WireMesh(
            verts, solid.toIntArray(), dim.toIntArray(),
            spins.map { Spin(it.first, it.end, model(it.origin), floatArrayOf(it.axis.x.toFloat(), (-it.axis.s).toFloat(), it.axis.z.toFloat()), it.rev.toFloat()) },
            extent,
            bold.toIntArray(),
        )
    }

    /** Joins rings (a one-vertex ring is a point) with longerons; [drawRing] picks which rings to outline. */
    private fun loft(rings: List<List<Int>>, n: Int, longeronStep: Int, drawRing: (Int) -> Boolean) {
        rings.forEachIndexed { i, r -> if (r.size > 1 && drawRing(i)) path(r, closed = true) }
        for (i in 0 until rings.size - 1) {
            val a = rings[i]
            val c = rings[i + 1]
            for (j in 0 until n step longeronStep) {
                val p = a[if (a.size == 1) 0 else j]
                val q = c[if (c.size == 1) 0 else j]
                if (p != q) line(p, q)
            }
        }
    }

    private fun body(b: Body) {
        val n = if (lite) 8 else b.sides
        for (k in sides(b.mirror)) {
            val rings = b.sections.map { sec ->
                if (sec.collapsed) listOf(v(P3(b.x, sec.s, sec.zc).side(k)))
                else List(n) { i -> v(b.point(sec, 2 * PI * i / n).side(k)) }
            }
            val last = rings.lastIndex
            // Every station is a ring up close; further out every other one keeps the shape without clutter.
            loft(rings, n, 1) { i -> b.rings && (detail || i == last || i % 2 == 0) }
            if (k > 0) {
                if (detail) windows(b)
                if (!lite) cockpit(b)
            }
        }
    }

    private fun windows(b: Body) {
        for (row in b.windows) {
            val count = ((row.s1 - row.s0) / row.pitch).toInt()
            for (i in 0..count) {
                val s = row.s0 + i * row.pitch
                for (ang in doubleArrayOf(row.angleDeg, 180 - row.angleDeg)) {
                    val a = rad(ang)
                    line(v(b.point(b.at(s), a)), v(b.point(b.at(s + row.size), a)), faint = true)
                }
            }
        }
    }

    /** Windshield panes, their edges sampled along the surface so they curve with the nose. */
    private fun cockpit(b: Body) {
        val c = b.cockpit ?: return
        val degPerStep = if (detail) 12.0 else 30.0
        val minSteps = if (detail) 3 else 1
        fun pt(u: Double, deg: Double) = b.point(b.at(c.s + u * c.len), rad(deg))
        fun draw(p: Pane, faint: Boolean) {
            for (left in if (p.mirror) listOf(false, true) else listOf(false)) {
                val cs = p.corners.map { (u, d) -> u to (if (left) 180 - d else d) }
                val n = cs.size
                val ids = ArrayList<Int>()
                for (i in 0 until if (p.closed) n else n - 1) {
                    val (u0, d0) = cs[i]
                    val (u1, d1) = cs[(i + 1) % n]
                    val steps = max(minSteps, ceil(abs(d1 - d0) / degPerStep).toInt())
                    for (j in 0 until steps) {
                        val t = j.toDouble() / steps
                        ids += v(pt(u0 + (u1 - u0) * t, d0 + (d1 - d0) * t))
                    }
                }
                if (!p.closed) cs.last().let { (u, d) -> ids += v(pt(u, d)) }
                path(ids, closed = p.closed, faint = faint, accent = !faint)
            }
        }
        for (p in c.shield.panes) if (detail || p.essential) draw(p, faint = false)
        if (detail) c.shield.mask?.let { draw(it, faint = true) }
    }

    private fun refine(ribs: List<Rib>, pieces: Int): List<Rib> {
        if (ribs.size < 2) return ribs
        val total = ribs.zipWithNext { a, b -> (b.le - a.le).length }.sum()
        if (total < 1e-6) return ribs
        val maxSeg = total / pieces
        val out = ArrayList<Rib>()
        for (i in 0 until ribs.size - 1) {
            val a = ribs[i]
            val b = ribs[i + 1]
            val m = ceil((b.le - a.le).length / maxSeg - 0.25).toInt().coerceAtLeast(1)
            for (j in 0 until m) {
                val t = j.toDouble() / m
                out += Rib(a.le + (b.le - a.le) * t, a.chord + (b.chord - a.chord) * t, a.t + (b.t - a.t) * t)
            }
        }
        out += ribs.last()
        return out
    }

    private fun surface(sf: Surface) {
        val ribs = if (lite) sf.ribs else refine(sf.ribs, if (detail) 7 else 4)
        val n = ribs.size
        if (n < 2) return
        for (k in sides(sf.mirror)) {
            val le = IntArray(n)
            val te = IntArray(n)
            val up = IntArray(n)
            val lo = IntArray(n)
            for ((i, r) in ribs.withIndex()) {
                le[i] = v(r.le.side(k))
                te[i] = v((r.le + P3.AFT * r.chord).side(k))
                if (!lite) {
                    val spanDir = if (i < n - 1) ribs[i + 1].le - r.le else r.le - ribs[i - 1].le
                    val nrm = spanDir.cross(P3.AFT).unit()
                    val mid = r.le + P3.AFT * (r.chord * 0.3)
                    up[i] = v((mid + nrm * (r.t / 2)).side(k))
                    lo[i] = v((mid - nrm * (r.t / 2)).side(k))
                }
            }
            path(le.toList())
            path(te.toList())
            if (lite) {
                line(le[0], te[0])
                line(le[n - 1], te[n - 1])
                continue
            }
            path(up.toList())
            path(lo.toList())
            for (i in 0 until n) path(listOf(le[i], up[i], te[i], lo[i]), closed = true)
            if (detail && sf.hinge) {
                path(ribs.map { r -> v((r.le + P3.AFT * (r.chord * 0.74)).side(k)) }, faint = true)
            }
        }
    }

    private fun nacelle(nc: Nacelle) {
        val prof = when (nc.style) {
            NacelleStyle.FAN -> FAN
            NacelleStyle.JET -> JET
            NacelleStyle.PROP -> PROP
            NacelleStyle.POD -> POD
        }
        val n = if (lite) 6 else 12
        val r0 = nc.dia / 2
        val maxIdx = prof.indices.maxBy { prof[it].second }
        for (k in sides(nc.mirror)) {
            val f0 = nc.front
            fun at(f: Double, rf: Double, a: Double, ds: Double = 0.0): P3 {
                val cz = sin(a)
                val zScale = if (nc.flat && cz < 0) 0.72 else 1.0
                val xScale = if (nc.flat) 1.06 else 1.0
                return P3(f0.x + r0 * rf * cos(a) * xScale, f0.s + f * nc.len + ds, f0.z + r0 * rf * cz * zScale)
            }
            fun ring(f: Double, rf: Double, zig: Boolean = false): List<Int> =
                if (rf < 1e-3) listOf(v(P3(f0.x, f0.s + f * nc.len, f0.z).side(k)))
                else List(n) { i ->
                    val odd = zig && i % 2 == 1
                    v(at(f, rf * (if (odd) 0.9 else 1.0), 2 * PI * i / n, if (zig && !odd) 0.05 * nc.len else 0.0).side(k))
                }
            val rings = prof.mapIndexed { idx, (f, rf) -> ring(f, rf, zig = nc.chevrons && !lite && idx == prof.lastIndex) }
            loft(rings, n, if (lite) 1 else 2) { i -> !lite || i == 0 || i == maxIdx || i == rings.lastIndex }
            if (lite) continue
            when (nc.style) {
                NacelleStyle.FAN -> {
                    // Exhaust core and plug.
                    val core = ring(0.82, 0.5)
                    path(core, closed = true)
                    val plug = v(P3(f0.x, f0.s + 1.02 * nc.len, f0.z).side(k))
                    for (i in core.indices step 3) line(core[i], plug)
                    if (detail) {
                        // Fan face with spinner.
                        val fan = ring(0.1, 0.78)
                        path(fan, closed = true)
                        val hub = v(P3(f0.x, f0.s + 0.03 * nc.len, f0.z).side(k))
                        for (i in fan.indices step 2) line(fan[i], hub)
                    }
                }
                NacelleStyle.JET -> if (detail) path(ring(0.98, 0.5), closed = true)
                else -> Unit
            }
        }
    }

    private fun rotor(r: Rotor) {
        for (k in sides(r.mirror)) {
            val hub = r.hub.side(k)
            val a = (if (k < 0) r.axis.flipX() else r.axis).unit()
            val (u, w) = basis(a)
            val rad = r.dia / 2
            val droop = a * (-r.droop)
            val start = pts.size
            val hubV = v(hub)
            for (b in 0 until r.blades) {
                val phi = 2 * PI * b / r.blades + r.phase
                val dir = u * cos(phi) + w * sin(phi)
                if (lite) {
                    line(hubV, v(hub + dir * rad + droop))
                    continue
                }
                val tan = a.cross(dir).unit()
                val c = r.chord / 2
                val root = hub + dir * (rad * 0.14)
                val p1 = v(root + tan * c)
                val p2 = v(hub + dir * rad + tan * (c * 0.7) + droop)
                val p3 = v(hub + dir * rad - tan * (c * 0.7) + droop)
                val p4 = v(root - tan * c)
                path(listOf(p1, p2, p3, p4), closed = true)
                line(hubV, v(root))
            }
            if (r.spinner > 0 && !lite) {
                val tip = v(hub + a * r.spinner)
                val ring = List(6) { i ->
                    val ph = 2 * PI * i / 6
                    v(hub + (u * cos(ph) + w * sin(ph)) * (r.spinner * 0.45))
                }
                path(ring, closed = true)
                ring.forEach { line(it, tip) }
            }
            if (r.rev != 0.0) spins += SpinRaw(start, pts.size, hub, a, r.rev)
            if (r.disc) {
                val segs = if (lite) 16 else 36
                path(List(segs) { i ->
                    val ph = 2 * PI * i / segs
                    v(hub + (u * cos(ph) + w * sin(ph)) * rad + droop * 0.6)
                }, closed = true, faint = true)
            }
            if (r.mast > 0) line(v(hub), v(hub - a * r.mast))
            if (r.duct > 0) {
                val segs = if (lite) 10 else 20
                val d = r.duct / 2
                fun ring(off: Double) = List(segs) { i ->
                    val ph = 2 * PI * i / segs
                    v(hub + a * off + (u * cos(ph) + w * sin(ph)) * (rad * 1.12))
                }
                val r1 = ring(d)
                val r2 = ring(-d)
                path(r1, closed = true)
                path(r2, closed = true)
                if (!lite) for (i in 0 until segs step 2) line(r1[i], r2[i])
            }
        }
    }

    private fun drum(d: Drum) {
        for (k in sides(d.mirror)) {
            val c = d.center.side(k)
            val a = (if (k < 0) d.axis.flipX() else d.axis).unit()
            val (u, w) = basis(a)
            val n = if (lite) max(6, d.sides / 2) else d.sides
            val start = pts.size
            fun ring(off: Double) = List(n) { i ->
                val ph = 2 * PI * i / n
                v(c + a * off + (u * cos(ph) + w * sin(ph)) * d.r)
            }
            val r1 = ring(d.depth / 2)
            path(r1, closed = true, faint = d.faint)
            if (d.depth > 0) {
                val r2 = ring(-d.depth / 2)
                path(r2, closed = true, faint = d.faint)
                if (!lite) for (i in 0 until n step max(1, n / 8)) line(r1[i], r2[i], d.faint)
            }
            if (d.spokes > 0 && !lite) {
                val hub = v(c)
                for (i in 0 until d.spokes) line(hub, r1[i * n / d.spokes], d.faint)
            }
            if (d.rev != 0.0) spins += SpinRaw(start, pts.size, c, a, d.rev)
        }
    }

    private fun wire(w: Wire) {
        if (lod.ordinal > w.coarsest.ordinal) return
        for (k in sides(w.mirror)) path(w.points.map { v(it.side(k)) }, w.closed, w.faint)
    }

    private companion object {
        // Nacelle profiles: (fraction of length, fraction of radius).
        val FAN = listOf(0.0 to 0.90, 0.07 to 0.99, 0.30 to 1.0, 0.62 to 0.93, 0.82 to 0.76)
        val JET = listOf(0.0 to 0.86, 0.10 to 1.0, 0.70 to 0.97, 0.92 to 0.80, 1.0 to 0.72)
        val PROP = listOf(0.0 to 0.55, 0.08 to 0.85, 0.28 to 1.0, 0.65 to 0.9, 1.0 to 0.22)
        val POD = listOf(0.0 to 0.0, 0.08 to 0.62, 0.3 to 1.0, 0.75 to 0.92, 1.0 to 0.35)
    }
}
