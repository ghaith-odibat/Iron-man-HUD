package com.ghaith.ironhud.plane.models

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

/**
 * Lays a [Cockpit]'s windows out on its [Body]: corners onto the skin, edges sampled along it,
 * corners rounded, and the glass edge set inside the frame.
 */
internal class Glazing(private val b: Body, private val c: Cockpit) {
    private val e = 2.0 / b.box

    /** A point on the skin and the skin's outward normal there. */
    class Sample(val p: P3, val n: P3) {
        fun flip() = Sample(p.flipX(), n.flipX())
    }

    /** Station in metres. */
    private fun sm(s: Double) = c.s0 + s * c.unit

    /** How far round the section a point sits, given how far it is from the centre (0..1 of the half-axis). */
    private fun root(t: Double) = t.coerceIn(0.0, 1.0).pow(1 / e)

    /** (station m, angle rad) of [g] on the right-hand side. */
    fun param(g: Gp): DoubleArray = when (g) {
        is Gp.Deg -> doubleArrayOf(sm(g.s), rad(g.deg))
        is Gp.Side -> {
            val s = sm(g.s)
            val sec = b.at(s)
            val dz = g.z * c.unit - sec.zc
            val h = if (dz >= 0) sec.up else sec.down
            doubleArrayOf(s, (sign(dz) * asin(root(abs(dz) / max(h, 1e-6))) + rad(g.lift)).coerceAtMost(rad(88.0)))
        }
        is Gp.Plan -> {
            val s = sm(g.s)
            val t = g.x * c.unit / max(b.at(s).topW, 1e-6)
            doubleArrayOf(s, acos(sign(t) * root(abs(t))))
        }
    }

    /** Whether [g] lands on the skin rather than being clamped to its edge (the section is big enough there). */
    fun onSkin(g: Gp): Boolean = when (g) {
        is Gp.Deg -> true
        is Gp.Side -> {
            val sec = b.at(sm(g.s))
            val dz = g.z * c.unit - sec.zc
            abs(dz) <= (if (dz >= 0) sec.up else sec.down) + 1e-6
        }
        is Gp.Plan -> abs(g.x * c.unit) <= b.at(sm(g.s)).topW + 1e-6
    }

    private fun at(s: Double, a: Double): P3 = b.point(b.at(s), a)

    private fun sample(q: DoubleArray): Sample = Sample(at(q[0], q[1]), normal(q[0], q[1]))

    /** Outward skin normal by finite differences. */
    private fun normal(s: Double, a: Double): P3 {
        val h = 0.02 * c.unit
        val k = 0.01
        val ds = at(s + h, a) - at(s - h, a)
        val da = at(s, a + k) - at(s, a - k)
        val n = da.cross(ds).unit()
        val p = at(s, a)
        val out = P3(p.x - b.x, 0.0, p.z - b.at(s).zc)
        return if (n.dot(out) < 0) n * -1.0 else n
    }

    /** Point [t] of the way from [p] to [q]: straight in side or plan view when both corners are pinned that way. */
    fun along(p: Gp, q: Gp, t: Double): DoubleArray = when {
        p is Gp.Side && q is Gp.Side -> param(Gp.Side(lerp(p.s, q.s, t), lerp(p.z, q.z, t), lerp(p.lift, q.lift, t)))
        p is Gp.Plan && q is Gp.Plan -> param(Gp.Plan(lerp(p.s, q.s, t), lerp(p.x, q.x, t)))
        else -> {
            val a = param(p)
            val z = param(q)
            doubleArrayOf(lerp(a[0], z[0], t), lerp(a[1], z[1], t))
        }
    }

    /**
     * The outline of [pane] on the right-hand side: edges sampled every [seg] metres or less, rounded
     * corners drawn with [arc] points between their ends.
     */
    fun outline(pane: Pane, seg: Double, arc: Int): List<Sample> {
        val cs = pane.corners
        val n = cs.size
        val edges = if (pane.closed) n else n - 1
        val corner = cs.map { sample(param(it)) }
        val len = DoubleArray(edges) { i -> (corner[(i + 1) % n].p - corner[i].p).length.coerceAtLeast(1e-6) }
        val r = DoubleArray(n) { i ->
            val want = (pane.radii?.getOrNull(i) ?: pane.round) * c.unit
            when {
                want <= 0 -> 0.0
                !pane.closed && (i == 0 || i == n - 1) -> 0.0
                else -> min(want, 0.45 * min(len[(i - 1 + edges) % edges], len[i % edges]))
            }
        }
        val out = ArrayList<Sample>()
        for (i in 0 until edges) {
            val j = (i + 1) % n
            val t0 = r[i] / len[i]
            val t1 = 1 - r[j] / len[i]
            val steps = max(1, ceil(len[i] * (t1 - t0) / seg).toInt())
            for (k in 0 until steps) out += sample(along(cs[i], cs[j], lerp(t0, t1, k.toDouble() / steps)))
            if (!pane.closed && i == edges - 1) {
                out += corner[j]
            } else if (r[j] > 0) {
                val a = sample(along(cs[i], cs[j], t1))
                val z = sample(along(cs[j], cs[(j + 1) % n], r[j] / len[j % edges]))
                out += a
                for (k in 1..arc) {
                    val t = k.toDouble() / (arc + 1)
                    val p = a.p * ((1 - t) * (1 - t)) + corner[j].p * (2 * t * (1 - t)) + z.p * (t * t)
                    out += Sample(p, corner[j].n)
                }
            }
        }
        return out
    }

    /** [outline] moved [d] metres inwards along the skin: the glass edge inside the frame. */
    fun inset(outline: List<Sample>, d: Double, closed: Boolean): List<P3> {
        val n = outline.size
        if (n < 2) return outline.map { it.p }
        val w = List(n) { i ->
            val prev = outline[if (i > 0) i - 1 else if (closed) n - 1 else 0].p
            val next = outline[if (i < n - 1) i + 1 else if (closed) 0 else n - 1].p
            outline[i].n.cross(next - prev).unit()
        }
        val centre = outline.fold(P3(0.0, 0.0, 0.0)) { acc, s -> acc + s.p } * (1.0 / n)
        val inward = outline.indices.sumOf { w[it].dot(centre - outline[it].p) } >= 0
        val k = if (inward) d else -d
        return outline.indices.map { outline[it].p + w[it] * k }
    }

    /** A parked wiper: arm from the pivot to the tip, and the blade alongside it, just off the glass. */
    fun wiper(w: Wiper, pane: Pane): List<List<P3>> {
        val cs = pane.corners
        val a = cs[w.edge]
        val z = cs[(w.edge + 1) % cs.size]
        val centre = cs.map { sample(param(it)).p }.let { l -> l.fold(P3(0.0, 0.0, 0.0)) { acc, p -> acc + p } * (1.0 / l.size) }
        fun spot(t: Double, inset: Double): P3 {
            val s = sample(along(a, z, t))
            val toward = (centre - s.p).let { v -> v - s.n * v.dot(s.n) }.unit()
            return s.p + toward * (inset * c.unit) + s.n * (0.004 * c.unit)
        }
        val pivot = spot(w.pivot, w.inset)
        val tip = spot(w.tip, w.inset)
        val bladeFrom = spot(lerp(w.pivot, w.tip, 0.3), w.inset + 0.006)
        val bladeTo = spot(w.tip, w.inset + 0.006)
        return listOf(listOf(pivot, tip), listOf(bladeFrom, bladeTo))
    }

    private companion object {
        fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
    }
}

internal fun P3.dot(o: P3) = x * o.x + s * o.s + z * o.z
