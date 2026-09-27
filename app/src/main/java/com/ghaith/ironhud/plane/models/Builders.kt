package com.ghaith.ironhud.plane.models

import com.ghaith.ironhud.plane.Lod
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** An airframe's geometry plus the facts learnt while building it. */
class Design(
    val parts: List<Part>,
    val features: List<String>,
    val lengthM: Double,
    val spanM: Double,
    /** Main rotor diameter, for rotorcraft. */
    val rotorM: Double? = null,
)

/** Wingtip treatments. Sizes in metres; the aircraft's quoted span includes them. */
sealed interface Tip {
    data object None : Tip
    /** Extra, more swept tip panel (777-300ER, 787, 747-8). */
    data class Raked(val len: Double) : Tip
    /** Upturned winglet; [blend] rounds the joint, [lower] adds a downward blade (737 MAX, MD-11). */
    data class Winglet(
        val h: Double,
        val cant: Double = 12.0,
        val sweep: Double = 45.0,
        val blend: Double = 0.3,
        val taper: Double = 0.35,
        val lower: Double = 0.0,
        val label: String? = null,
    ) : Tip
    /** Small plate above and below the tip (A320ceo, A310, A380). */
    data class Fence(val h: Double) : Tip
    data class TipTank(val len: Double, val dia: Double) : Tip
    /** Raked tip with a hinge line [fold] metres in from the tip (777X). */
    data class Folding(val fold: Double, val rake: Double) : Tip
}

sealed interface TailKind {
    /** Tailplane on the fuselage. */
    data object Low : TailKind
    data object T : TailKind
    /** Tailplane part-way up the fin, [at] = fraction of fin height. */
    data class Cross(val at: Double) : TailKind
    /** Two surfaces in a V, [angle] above horizontal (negative = inverted V). */
    data class V(val angle: Double) : TailKind
    /** Fins on the tailplane tips. */
    data object H : TailKind
    /** Two canted fins on the fuselage, [offset] = fraction of half-width, [cant] outward from vertical. */
    data class Twin(val offset: Double, val cant: Double) : TailKind
}

data class Prop(val dia: Double, val blades: Int, val piston: Boolean = false, val pusher: Boolean = false)

sealed interface Eng

/** Engines on the wing at fractions [at] of the half-span (each mirrored). */
data class WingEng(
    val at: List<Double>,
    val len: Double,
    val dia: Double,
    val style: NacelleStyle = NacelleStyle.FAN,
    val overhang: Double = 0.6,
    val drop: Double = 0.15,
    val chevrons: Boolean = false,
    val flat: Boolean = false,
    val prop: Prop? = null,
    val above: Boolean = false,
    val twin: Boolean = false,
) : Eng

/** Engines on the rear fuselage, intake at fraction [at] of the length, [pairs] side by side. */
data class RearEng(
    val at: Double,
    val len: Double,
    val dia: Double,
    val z: Double = 0.35,
    val style: NacelleStyle = NacelleStyle.FAN,
    val pairs: Int = 1,
    val chevrons: Boolean = false,
) : Eng

/** A trijet's centre engine: in the fin root (DC-10, MD-11) or fed by an S-duct (727, L-1011, Falcon). */
data class TailEng(val at: Double, val len: Double, val dia: Double, val sDuct: Boolean) : Eng

/** A single jet on top of the rear fuselage (Vision Jet, Global Hawk). */
data class TopEng(val at: Double, val len: Double, val dia: Double) : Eng

enum class Gear { NONE, TRICYCLE, TAILDRAGGER, SKIDS, WHEELS }

/** Upper-deck or cargo-lobe bulge (747, Beluga) or a belly fairing. */
data class Hump(val start: Double, val end: Double, val h: Double, val rampIn: Double, val rampOut: Double, val widen: Double = 1.0) {
    fun frac(s: Double): Double = smooth((s - start) / rampIn) * smooth((end - s) / rampOut)
    val keys: List<Double> get() = listOf(start + rampIn * 0.35, start + rampIn * 0.7, start + rampIn, end - rampOut, end - rampOut * 0.5)
}

private fun smooth(x: Double) = x.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

/**
 * Tube fuselage: blunt-to-pointed nose over [nose] metres, a constant section, then a tail cone over
 * [tail] metres whose underside sweeps up to the tail end at [tailZ] (fraction of the half-height).
 */
internal fun tube(
    L: Double, W: Double, H: Double, nose: Double, tail: Double,
    noseTip: Double = -0.3, blunt: Double = 0.5, tailZ: Double = 0.45, tailR: Double = 0.12,
    hump: Hump? = null, keel: Hump? = null, cab: Cab? = null,
): List<Section> {
    val cylEnd = L - tail
    val zTip = noseTip * H / 2
    fun base(s: Double): Section = when {
        s < nose -> {
            val f = s / nose
            val g = 1 - (1 - f) * (1 - f)
            val top = if (cab != null) H * cab.top(s / H, zTip / H) else zTip + (H / 2 - zTip) * g.pow(blunt * 1.25)
            val bottom = zTip - (H / 2 + zTip) * g.pow(blunt * 0.8)
            Section(s, W / 2 * g.pow(blunt), (top + bottom) / 2, (top - bottom) / 2)
        }
        s <= cylEnd -> Section(s, W / 2, 0.0, H / 2)
        else -> {
            val f = ((s - cylEnd) / tail).coerceIn(0.0, 1.0)
            val zEnd = tailZ * H / 2
            val rEnd = tailR * H / 2
            val top = H / 2 + (zEnd + rEnd - H / 2) * f.pow(2.0)
            val bottom = -H / 2 + (zEnd - rEnd + H / 2) * f.pow(1.1)
            Section(s, W / 2 + (rEnd * W / H - W / 2) * f.pow(1.4), (top + bottom) / 2, (top - bottom) / 2)
        }
    }
    val frames = ((cylEnd - nose) / (1.2 * H)).roundToInt().coerceIn(2, 14)
    val stations = sortedSetOf<Double>()
    listOf(0.0, 0.007, 0.02, 0.06, 0.13, 0.23, 0.36, 0.52, 0.72, 1.0).forEach { stations += it * nose }
    // The windscreen's foot and head are creases in the crest: they get a station each, come what may.
    val creases = cab?.let { c -> listOf(c.sW, (c.sW + c.sT) / 2, c.sT, (c.sT + c.sR) / 2, c.sR).map { it * H }.filter { it < nose } }.orEmpty()
    for (i in 0..frames) stations += nose + (cylEnd - nose) * i / frames
    listOf(0.15, 0.32, 0.5, 0.67, 0.82, 0.93, 1.0).forEach { stations += cylEnd + it * tail }
    hump?.keys?.forEach { if (it in 0.0..L) stations += it }
    keel?.keys?.forEach { if (it in 0.0..L) stations += it }
    // Drop stations closer than 0.3 m to the previous one.
    val kept = ArrayList<Double>()
    for (s in stations) if (kept.isEmpty() || s - kept.last() > 0.3 || s == L) kept += s
    if (creases.isNotEmpty()) {
        kept.removeAll { k -> k > 0 && creases.any { abs(it - k) < min(0.3, 0.08 * H) } }
        kept += creases
        kept.sort()
    }
    return kept.map { s ->
        var sec = base(s)
        if (hump != null) {
            val k = hump.frac(s)
            sec = sec.copy(up = sec.up + hump.h * k, topW = sec.halfW * (1 + (hump.widen - 1) * k))
        }
        if (keel != null) sec = sec.copy(down = sec.down + keel.h * keel.frac(s))
        sec
    }
}

/** The first station where the crest of [sections] reaches [z] metres (searching up to [limit]). */
internal fun crest(sections: List<Section>, z: Double, limit: Double): Double {
    val probe = Body(sections)
    val step = limit / 500
    var s = sections.first().s
    while (s < limit && probe.at(s).top < z) s += step
    return s
}

/** Sections from a table of (fraction of L, half-width/W, centre/H, up/H, down/H), all over halves. */
internal fun table(L: Double, W: Double, H: Double, rows: Array<DoubleArray>, s0: Double = 0.0): List<Section> =
    rows.map { r -> Section(s0 + r[0] * L, r[1] * W / 2, r[2] * H / 2, r[3] * H / 2, r[4] * H / 2) }

/** A streamlined pod along s (tip tanks, floats, fairings). */
internal fun pod(s0: Double, len: Double, r: Double, z: Double): List<Section> =
    listOf(0.0 to 0.0, 0.1 to 0.65, 0.3 to 1.0, 0.7 to 0.95, 1.0 to 0.12).map { (f, k) -> Section(s0 + f * len, r * k, z, r * k) }

/** A tapered wing (or tailplane) planform with its tip treatment. */
internal class WingPlan(
    val semi: Double,
    val rootLE: Double,
    val root: Double,
    val tipChord: Double,
    sweep: Double,
    dihedral: Double,
    val zRoot: Double,
    val x0: Double,
    kink: Double = 0.0,
    val tc: Double = 0.13,
    val tip: Tip = Tip.None,
) {
    val tanS = tan(rad(sweep))
    private val sweepDeg = sweep
    private val tanD = tan(rad(dihedral))
    val xt: Double = when (tip) {
        is Tip.Raked -> semi - tip.len
        is Tip.Folding -> semi - tip.rake
        is Tip.Winglet -> semi - wingletReach(tip)
        is Tip.TipTank -> semi - tip.dia / 2
        else -> semi
    }
    private val xk = kink * semi
    private val hasKink = kink > 0 && xk > x0 + 0.3 && xk < xt - 0.5
    private val kinkChord = if (hasKink) max(root - xk * tanS, tipChord * 1.3) else 0.0

    fun le(x: Double) = rootLE + x * tanS
    fun z(x: Double) = zRoot + x * tanD
    fun te(x: Double): Double = when {
        !hasKink -> lerp(rootLE + root, le(xt) + tipChord, x / xt)
        x <= xk -> lerp(rootLE + root, le(xk) + kinkChord, x / xk)
        else -> lerp(le(xk) + kinkChord, le(xt) + tipChord, (x - xk) / (xt - xk))
    }
    fun chord(x: Double) = te(x) - le(x)
    fun thick(x: Double) = chord(x) * tc * (1 - 0.35 * (x / semi).coerceIn(0.0, 1.0))
    fun rib(x: Double) = Rib(P3(x, le(x), z(x)), chord(x), thick(x))

    fun parts(mirror: Boolean = true): List<Part> {
        val out = ArrayList<Part>()
        val ribs = mutableListOf(rib(x0))
        if (hasKink) ribs += rib(xk)
        if (tip is Tip.Folding && semi - tip.fold > x0 + 0.5 && semi - tip.fold < xt) ribs += rib(semi - tip.fold)
        ribs += rib(xt)
        when (tip) {
            is Tip.Raked -> ribs += rakedRib(tip.len)
            is Tip.Folding -> ribs += rakedRib(tip.rake)
            is Tip.Winglet -> {
                val base = rib(xt)
                ribs += wingletRibs(base, tip)
                if (tip.lower > 0) {
                    val le = base.le + P3(0.0, base.chord * 0.2, 0.0)
                    val a = rad(40.0)
                    out += Surface(listOf(
                        Rib(le, base.chord * 0.55, base.t * 0.5),
                        Rib(le + P3(tip.lower * sin(a), tip.lower * 0.9, -tip.lower * cos(a)), base.chord * 0.2, 0.02),
                    ), mirror = mirror, hinge = false)
                }
            }
            is Tip.Fence -> {
                val b = rib(semi)
                val c = b.chord
                val h = tip.h
                out += Wire(listOf(
                    P3(semi, b.le.s - 0.3 * c, b.le.z), P3(semi, b.le.s + 0.45 * c, b.le.z + 0.55 * h),
                    P3(semi, b.le.s + 0.85 * c, b.le.z + 0.1 * h), P3(semi, b.le.s + 0.35 * c, b.le.z - 0.45 * h),
                ), mirror = mirror, closed = true)
            }
            is Tip.TipTank -> {
                val x = semi - tip.dia / 2
                out += Body(pod(le(xt) + chord(xt) * 0.35 - tip.len * 0.4, tip.len, tip.dia / 2, z(x)), x = x, mirror = mirror, sides = 8)
            }
            Tip.None -> Unit
        }
        out.add(0, Surface(ribs, mirror = mirror))
        return out
    }

    fun features(): List<String> = when (tip) {
        is Tip.Raked -> listOf("RAKED WINGTIPS")
        is Tip.Folding -> listOf("FOLDING WINGTIPS")
        is Tip.Winglet -> listOf(tip.label ?: if (tip.lower > 0) "SPLIT WINGLETS" else if (tip.blend >= 0.3) "BLENDED WINGLETS" else "WINGLETS")
        is Tip.Fence -> listOf("WINGTIP FENCES")
        is Tip.TipTank -> listOf("TIP TANKS")
        Tip.None -> emptyList()
    }

    private fun rakedRib(len: Double): Rib {
        val slopeTE = te(xt) - te(xt - 1.0)
        val leR = le(xt) + len * tan(rad(min(sweepDeg + 22, 68.0)))
        val teR = te(xt) + len * slopeTE
        return Rib(P3(semi, leR, z(semi)), max(teR - leR, tipChord * 0.25), thick(xt) * 0.5)
    }

    private fun wingletRibs(base: Rib, w: Tip.Winglet): List<Rib> {
        val r = w.blend * w.h
        val phiMax = rad(90 - w.cant)
        val ell = ((w.h - r * (1 - cos(phiMax))) / sin(phiMax)).coerceAtLeast(0.1)
        val tanW = tan(rad(w.sweep))
        val out = ArrayList<Rib>()
        if (r > 0) for (f in listOf(0.5, 1.0)) {
            val phi = phiMax * f
            val dx = r * sin(phi)
            val dz = r * (1 - cos(phi))
            val c = base.chord * (1 - (1 - w.taper) * 0.3 * f)
            out += Rib(base.le + P3(dx, dx * tanS + dz * tanW + (base.chord - c) * 0.5, dz), c, base.t * 0.8)
        }
        val last = out.lastOrNull() ?: base
        val cTop = base.chord * w.taper
        out += Rib(last.le + P3(ell * cos(phiMax), ell * sin(phiMax) * tanW + (last.chord - cTop) * 0.4, ell * sin(phiMax)), cTop, base.t * 0.4)
        return out
    }

    companion object {
        fun wingletReach(w: Tip.Winglet): Double {
            val r = w.blend * w.h
            val phiMax = rad(90 - w.cant)
            val ell = ((w.h - r * (1 - cos(phiMax))) / sin(phiMax)).coerceAtLeast(0.1)
            return r * sin(phiMax) + ell * cos(phiMax)
        }
    }
}

/** Fin and tailplane for a body of length [L]. */
internal class Empennage(
    val finH: Double, val finRoot: Double, val finTip: Double, val finSweep: Double,
    val stabSpan: Double, val stabRoot: Double, val stabTip: Double, val stabSweep: Double,
    val tail: TailKind = TailKind.Low, val stabDihedral: Double = 5.0, val finEnd: Double = 0.985,
    val dorsal: Boolean = false,
) {
    fun parts(body: Body, L: Double, finBase: Pair<Double, Double>? = null): List<Part> {
        val out = ArrayList<Part>()
        val tanF = tan(rad(finSweep))
        val finRootLE = finBase?.first ?: (finEnd * L - finRoot)
        val z0 = finBase?.second ?: (body.at(finRootLE + 0.3 * finRoot).top - 0.02 * finRoot)
        val finTipLE = finRootLE + finH * tanF
        val single = tail !is TailKind.V && tail !is TailKind.H && tail !is TailKind.Twin
        if (single && finH > 0) {
            out += Surface(listOf(
                Rib(P3(0.0, finRootLE, z0), finRoot, finRoot * 0.1),
                Rib(P3(0.0, finTipLE, z0 + finH), finTip, finTip * 0.1),
            ), mirror = false)
            if (dorsal) {
                val s = finRootLE - finRoot * 0.9
                out += Wire(listOf(P3(0.0, s, body.at(s).top), P3(0.0, finRootLE + 0.14 * finH * tanF, z0 + 0.14 * finH)), coarsest = Lod.NORMAL)
            }
        }
        val semi = stabSpan / 2
        val tanSt = tan(rad(stabSweep))
        fun stab(rootLE: Double, z: Double, xRoot: Double, dih: Double): Surface {
            val tanDi = tan(rad(dih))
            fun rib(x: Double): Rib {
                val c = lerp(stabRoot, stabTip, (x / semi).coerceIn(0.0, 1.0))
                return Rib(P3(x, rootLE + x * tanSt, z + (x - xRoot) * tanDi), c, c * 0.1)
            }
            return Surface(listOf(rib(xRoot), rib(semi)))
        }
        if (semi <= 0) return out
        fun low(): Surface {
            val s = L * 0.975 - stabRoot
            val sec = body.at(s + stabRoot * 0.4)
            return stab(s, sec.zc + 0.15 * sec.up, sec.halfW * 0.85, stabDihedral)
        }
        when (tail) {
            TailKind.Low -> out += low()
            TailKind.T -> out += stab(finTipLE - 0.1 * stabRoot, z0 + finH, 0.0, stabDihedral)
            is TailKind.Cross -> {
                val c = lerp(finRoot, finTip, tail.at)
                out += stab(finRootLE + tail.at * finH * tanF + (c - stabRoot) * 0.5, z0 + tail.at * finH, 0.0, stabDihedral)
            }
            is TailKind.V -> {
                val s = L * 0.97 - stabRoot
                val sec = body.at(s + stabRoot * 0.4)
                val z = if (tail.angle >= 0) sec.top - 0.15 * sec.up else sec.bottom + 0.15 * sec.down
                out += stab(s, z, sec.halfW * 0.4, tail.angle)
            }
            TailKind.H -> {
                val st = low()
                out += st
                val tipRib = st.ribs.last()
                out += Surface(listOf(
                    Rib(P3(semi, tipRib.le.s - 0.2 * finRoot, tipRib.le.z - 0.35 * finH), finRoot, finRoot * 0.1),
                    Rib(P3(semi, tipRib.le.s - 0.2 * finRoot + finH * tanF, tipRib.le.z + 0.65 * finH), finTip, finTip * 0.1),
                ))
            }
            is TailKind.Twin -> {
                val sec = body.at(finRootLE + 0.3 * finRoot)
                val xo = tail.offset * sec.halfW
                val c = rad(tail.cant)
                out += Surface(listOf(
                    Rib(P3(xo, finRootLE, sec.top - 0.1 * sec.up), finRoot, finRoot * 0.08),
                    Rib(P3(xo + finH * sin(c), finTipLE, sec.top - 0.1 * sec.up + finH * cos(c)), finTip, finTip * 0.08),
                ))
                out += low()
            }
        }
        return out
    }

    fun features(): List<String> = when (tail) {
        TailKind.Low -> emptyList()
        TailKind.T -> listOf("T-TAIL")
        is TailKind.Cross -> listOf("CRUCIFORM TAIL")
        is TailKind.V -> listOf(if (tail.angle < 0) "INVERTED V-TAIL" else "V-TAIL")
        TailKind.H, is TailKind.Twin -> listOf("TWIN FINS")
    }
}

private fun propRotor(p: Prop, front: P3, len: Double, mirror: Boolean): Rotor =
    if (p.pusher) Rotor(front + P3(0.0, len + 0.1, 0.0), P3.AFT, p.dia, p.blades, chord = p.dia * 0.08, rev = 2.4,
        spinner = min(0.22 * p.dia, 0.8), mirror = mirror)
    else Rotor(front, P3.FWD, p.dia, p.blades, chord = p.dia * (if (p.blades >= 6) 0.06 else 0.08), rev = 2.4,
        spinner = min(0.22 * p.dia, 0.8), mirror = mirror)

private fun engineWord(style: NacelleStyle, prop: Prop?) = when {
    prop?.piston == true -> "PISTON"
    style == NacelleStyle.PROP || prop != null -> "TURBOPROP"
    else -> "TURBOFAN"
}

private fun wingSide(z: Double) = when {
    z > 0.45 -> "HIGH WING"
    z < -0.3 -> "LOW WING"
    else -> "MID WING"
}

/**
 * Any tube-and-wing aircraft: airliners, freighters, regionals, business jets, turboprops.
 * Body lengths in metres; [nose] and [tailCone] in fuselage heights; [wingAt] = root leading edge
 * as a fraction of L; [wingZ] = root height as a fraction of the half-height (−1 belly … +1 roof).
 */
internal fun jet(
    L: Double, W: Double, H: Double = W, box: Double = 2.0,
    nose: Double = 1.8, tailCone: Double = 2.6, noseTip: Double = -0.3, blunt: Double = 0.5,
    tailZ: Double = 0.45, tailR: Double = 0.12, hump: Hump? = null, decks: Int = 1,
    windowPitch: Double = 0.53, windowSize: Double = 0.26, windows: Boolean = true,
    span: Double, root: Double, tipChord: Double, sweep: Double, dihedral: Double = 5.0,
    wingAt: Double = 0.37, wingZ: Double = -0.6, kink: Double = 0.33, tc: Double = 0.13, tip: Tip = Tip.None,
    engines: List<Eng> = emptyList(),
    finH: Double, finRoot: Double, finTip: Double, finSweep: Double, finEnd: Double = 0.985,
    tail: TailKind = TailKind.Low,
    stabSpan: Double, stabRoot: Double, stabTip: Double, stabSweep: Double, stabDihedral: Double = 5.0,
    dorsal: Boolean = false, fairing: Boolean = true,
    extra: (Body, WingPlan) -> List<Part> = { _, _ -> emptyList() },
    extraFeatures: List<String> = emptyList(),
    /** The type's flight-deck glazing (see [Windshields]). */
    shield: Windshield = Windshields.deck(),
): Design {
    val parts = ArrayList<Part>()
    val features = LinkedHashSet<String>()
    val keel = if (fairing && wingZ < -0.3) Hump(wingAt * L - root * 0.15, wingAt * L + root * 1.25, 0.06 * H, root * 0.4, root * 0.5) else null
    val noseM = nose * H
    val tailM = tailCone * H
    val rows = if (!windows) emptyList() else buildList {
        add(WindowRow(noseM * 1.1, L - tailM * 0.8, if (decks == 2) 5.0 else 16.0, windowPitch, windowSize))
        if (decks == 2) add(WindowRow(noseM * 0.95, L - tailM * 0.95, 48.0, windowPitch, windowSize))
        if (hump != null && hump.h > 0.8) add(WindowRow(hump.start + hump.rampIn * 0.9, hump.end - hump.rampOut * 0.9, 58.0, windowPitch, windowSize))
    }
    val sections = tube(L, W, H, noseM, tailM, noseTip, blunt, tailZ, tailR, hump, keel, shield.cab)
    val cockpit = shield.anchor?.let { Cockpit(crest(sections, it * H, L * 0.5), H, shield) } ?: Cockpit(noseM * 0.38, noseM * 0.5, shield)
    val body = Body(sections, box = box, windows = rows, cockpit = cockpit)
    parts += body

    val zw = wingZ * H / 2
    val wing = WingPlan(span / 2, wingAt * L, root, tipChord, sweep, dihedral, zw,
        x0 = W / 2 * sqrt(max(0.05, 1 - wingZ * wingZ)) * 0.97, kink = kink, tc = tc, tip = tip)
    parts += wing.parts()
    features += wingSide(wingZ)
    features += wing.features()

    var count = 0
    var word = "TURBOFAN"
    var finBase: Pair<Double, Double>? = null
    val placement = LinkedHashSet<String>()
    for (e in engines) when (e) {
        is WingEng -> for (f in e.at) {
            val x = max(f * wing.semi, wing.x0 + e.dia * 0.6)
            val c = wing.chord(x)
            val t = wing.thick(x)
            val lx = wing.le(x)
            val zx = wing.z(x)
            val isProp = e.style == NacelleStyle.PROP
            val cz = when {
                isProp && wingZ > 0.3 -> zx - 0.12 * e.dia
                isProp -> zx + 0.12 * e.dia
                e.above -> zx + t / 2 + (0.5 + e.drop) * e.dia
                else -> zx - t / 2 - (0.5 + e.drop) * e.dia
            }
            val front = lx - e.overhang * e.len
            val xs = if (e.twin) listOf(x - 0.55 * e.dia, x + 0.55 * e.dia) else listOf(x)
            for (xx in xs) {
                parts += Nacelle(P3(xx, front, cz), e.len, e.dia, e.style, chevrons = e.chevrons, flat = e.flat)
                e.prop?.let { parts += propRotor(it, P3(xx, front, cz), e.len, mirror = true) }
            }
            if (!isProp) {
                val nacEdge = if (e.above) cz - e.dia * 0.45 else cz + e.dia * 0.45
                val wingEdge = if (e.above) zx + t * 0.4 else zx - t * 0.4
                parts += Wire(listOf(
                    P3(x, front + 0.22 * e.len, nacEdge), P3(x, front + 0.8 * e.len, nacEdge),
                    P3(x, lx + 0.55 * c, wingEdge), P3(x, lx - 0.02 * c, wingEdge),
                ), mirror = true, closed = true)
            }
            count += xs.size * 2
            word = engineWord(e.style, e.prop)
            placement += when {
                e.above -> "OVER-WING ENGINES"
                e.prop?.pusher == true -> "PUSHER PROPELLERS"
                else -> ""
            }
            if (e.chevrons) features += "CHEVRON NOZZLES"
        }
        is RearEng -> {
            val front = e.at * L
            val sec = body.at(front + e.len * 0.5)
            val cz = sec.zc + e.z * sec.up
            val bx = sec.halfW * sqrt(max(0.1, 1 - e.z * e.z))
            val x = bx + 0.3 * e.dia + e.dia / 2
            for (i in 0 until e.pairs) parts += Nacelle(P3(x + i * e.dia * 1.08, front, cz), e.len, e.dia, e.style, chevrons = e.chevrons)
            parts += Wire(listOf(
                P3(bx * 0.95, front + 0.3 * e.len, cz), P3(x - 0.45 * e.dia, front + 0.25 * e.len, cz),
                P3(x - 0.45 * e.dia, front + 0.75 * e.len, cz), P3(bx * 0.95, front + 0.85 * e.len, cz),
            ), mirror = true, closed = true)
            count += 2 * e.pairs
            placement += "REAR-MOUNTED ENGINES"
        }
        is TailEng -> {
            val front = e.at * L
            if (!e.sDuct) {
                val sec = body.at(front + e.len * 0.5)
                val cz = sec.top + e.dia * 0.42
                parts += Nacelle(P3(0.0, front, cz), e.len, e.dia, NacelleStyle.FAN, mirror = false)
                finBase = (front + 0.3 * e.len) to (cz + e.dia * 0.45)
            } else {
                val secIn = body.at(front)
                val cz = secIn.top + e.dia * 0.3
                val secEnd = body.at(L)
                parts += Drum(P3(0.0, front, cz), P3.FWD, e.dia / 2, depth = e.dia * 0.12, sides = 12)
                parts += Body(listOf(
                    Section(front, e.dia * 0.5, cz, e.dia * 0.5, e.dia * 0.4),
                    Section(front + (L - front) * 0.4, e.dia * 0.45, lerp(cz, secEnd.zc, 0.4), e.dia * 0.45),
                    Section(L * 0.998, e.dia * 0.4, secEnd.zc + e.dia * 0.1, e.dia * 0.4),
                ), sides = 10)
                finBase = (front + e.dia * 0.8) to (cz + e.dia * 0.3)
            }
            count += 1
            placement += "TRIJET"
        }
        is TopEng -> {
            val front = e.at * L
            val sec = body.at(front + e.len * 0.5)
            parts += Nacelle(P3(0.0, front, sec.top + 0.22 * e.dia), e.len, e.dia, NacelleStyle.FAN, mirror = false)
            count += 1
            placement += "SPINE-MOUNTED ENGINE"
        }
    }
    if (count > 0) features += "$count × $word"
    features += placement.filter { it.isNotEmpty() }
    val emp = Empennage(finH, finRoot, finTip, finSweep, stabSpan, stabRoot, stabTip, stabSweep, tail, stabDihedral, finEnd, dorsal)
    parts += emp.parts(body, L, finBase)
    features += emp.features()
    if (hump != null && hump.h > 0.8 && hump.widen <= 1.0) features += "UPPER DECK"
    if (hump != null && hump.widen > 1.0) features += "CARGO LOBE"
    if (decks == 2) features += "FULL DOUBLE DECK"
    parts += extra(body, wing)
    features += extraFeatures
    features += shield.label
    return Design(parts, features.toList(), L, span)
}

/** Fixed landing gear: a wheel under the nose (or tail) and two mains at [track]. */
private fun fixedGear(body: Body, L: Double, H: Double, mainS: Double, track: Double, tailDragger: Boolean, wheelR: Double): List<Part> {
    val out = ArrayList<Part>()
    val drop = H * 0.55
    val mainSec = body.at(mainS)
    val ground = mainSec.bottom - drop
    out += Wire(listOf(P3(mainSec.halfW * 0.6, mainS, mainSec.bottom + 0.1), P3(track / 2, mainS, ground + wheelR)), mirror = true)
    out += Drum(P3(track / 2, mainS, ground + wheelR), P3.RIGHT, wheelR, depth = wheelR * 0.6, sides = 10, mirror = true)
    if (tailDragger) {
        val s = L * 0.96
        val sec = body.at(s)
        out += Wire(listOf(P3(0.0, s, sec.bottom), P3(0.0, s + 0.1, sec.bottom - wheelR * 1.2)))
        out += Drum(P3(0.0, s + 0.1, sec.bottom - wheelR * 1.2 - wheelR * 0.5), P3.RIGHT, wheelR * 0.5, depth = wheelR * 0.3, sides = 8)
    } else {
        val s = L * 0.12
        val sec = body.at(s)
        out += Wire(listOf(P3(0.0, s, sec.bottom), P3(0.0, s, ground + wheelR * 0.85)))
        out += Drum(P3(0.0, s, ground + wheelR * 0.85), P3.RIGHT, wheelR * 0.85, depth = wheelR * 0.5, sides = 10)
    }
    return out
}

private val GA_HIGH = arrayOf(
    doubleArrayOf(0.00, 0.14, -0.05, 0.12, 0.12),
    doubleArrayOf(0.03, 0.55, -0.06, 0.45, 0.50),
    doubleArrayOf(0.10, 0.80, -0.08, 0.55, 0.62),
    doubleArrayOf(0.20, 0.88, -0.05, 0.65, 0.75),
    doubleArrayOf(0.30, 0.95, 0.05, 0.95, 0.90),
    doubleArrayOf(0.45, 0.95, 0.05, 0.95, 0.85),
    doubleArrayOf(0.58, 0.80, 0.10, 0.75, 0.65),
    doubleArrayOf(0.72, 0.50, 0.15, 0.45, 0.40),
    doubleArrayOf(0.87, 0.30, 0.18, 0.28, 0.25),
    doubleArrayOf(1.00, 0.10, 0.20, 0.10, 0.10),
)
private val GA_LOW = arrayOf(
    doubleArrayOf(0.00, 0.14, 0.00, 0.12, 0.12),
    doubleArrayOf(0.03, 0.55, -0.02, 0.45, 0.48),
    doubleArrayOf(0.10, 0.82, 0.00, 0.60, 0.62),
    doubleArrayOf(0.22, 0.90, 0.02, 0.72, 0.68),
    doubleArrayOf(0.32, 0.95, 0.08, 0.92, 0.62),
    doubleArrayOf(0.45, 0.95, 0.08, 0.90, 0.60),
    doubleArrayOf(0.58, 0.78, 0.10, 0.70, 0.50),
    doubleArrayOf(0.72, 0.50, 0.14, 0.45, 0.35),
    doubleArrayOf(0.87, 0.30, 0.18, 0.28, 0.22),
    doubleArrayOf(1.00, 0.12, 0.22, 0.12, 0.10),
)
private val GA_TWIN = arrayOf(
    doubleArrayOf(0.00, 0.00, -0.05, 0.00, 0.00),
    doubleArrayOf(0.04, 0.45, -0.05, 0.40, 0.42),
    doubleArrayOf(0.12, 0.75, -0.02, 0.62, 0.66),
    doubleArrayOf(0.22, 0.92, 0.02, 0.80, 0.80),
    doubleArrayOf(0.32, 0.98, 0.06, 0.94, 0.86),
    doubleArrayOf(0.55, 0.98, 0.06, 0.94, 0.86),
    doubleArrayOf(0.70, 0.70, 0.12, 0.66, 0.58),
    doubleArrayOf(0.85, 0.40, 0.18, 0.38, 0.30),
    doubleArrayOf(1.00, 0.14, 0.24, 0.14, 0.12),
)

/**
 * Light aircraft: singles (nose propeller), light twins (props on the wing), biplanes.
 * [twinAt] puts two engines at that fraction of the half-span instead of one in the nose.
 */
internal fun light(
    L: Double, W: Double = 1.1, H: Double = 1.35, highWing: Boolean,
    span: Double, root: Double, tipChord: Double = root, sweep: Double = 0.0, dihedral: Double = if (highWing) 1.5 else 6.0,
    wingAt: Double = 0.3, strut: Boolean = highWing, tip: Tip = Tip.None,
    prop: Prop, twinAt: Double? = null, nacelleLen: Double = 0.0,
    tail: TailKind = TailKind.Low,
    finH: Double, finRoot: Double, finTip: Double = finRoot * 0.55, finSweep: Double = 35.0,
    stabSpan: Double, stabRoot: Double, stabTip: Double = stabRoot * 0.7, stabSweep: Double = 4.0,
    gear: Gear = Gear.TRICYCLE, box: Double = 2.2, biplane: Double? = null,
    /** Cabin glazing, over the zone from the firewall back to the rear window (null: none). */
    shield: Windshield? = Windshields.GA_LOW,
    /** A tandem canopy pod over the cabin instead (trainers). */
    canopy: Windshield? = null,
): Design {
    val parts = ArrayList<Part>()
    val features = LinkedHashSet<String>()
    val rows = when {
        twinAt != null -> GA_TWIN
        highWing -> GA_HIGH
        else -> GA_LOW
    }
    val body = Body(table(L, W, H, rows), box = box, sides = 12, cockpit = shield?.let { Cockpit(L * 0.19, L * 0.43, it) })
    parts += body
    if (canopy != null) {
        val s0 = L * 0.2
        val len = L * 0.4
        parts += Body(pod(s0, len, W * 0.38, body.at(s0 + len * 0.4).top - H * 0.12), sides = 10, rings = false,
            cockpit = Cockpit(s0, len, canopy))
    }
    val rootLE = wingAt * L
    val sec = body.at(rootLE + root * 0.3)
    val zw = if (highWing) sec.top - 0.04 else sec.bottom + 0.14 * H
    val wing = WingPlan(span / 2, rootLE, root, tipChord, sweep, dihedral, zw,
        x0 = if (highWing) W * 0.3 else W * 0.45, tc = 0.15, tip = tip)
    parts += wing.parts()
    features += if (biplane != null) "BIPLANE" else wingSide(if (highWing) 1.0 else -1.0)
    features += wing.features()
    if (strut) {
        val x = wing.semi * 0.5
        val low = P3(W * 0.42, rootLE + root * 0.35, sec.bottom + 0.12 * H)
        parts += Wire(listOf(low, P3(x, wing.le(x) + wing.chord(x) * 0.3, wing.z(x) - 0.05)), mirror = true)
        features += "STRUT-BRACED WING"
    }
    if (biplane != null) {
        val upper = WingPlan(biplane / 2, rootLE - root * 0.25, root, root, sweep, dihedral, sec.top + H * 0.45, x0 = 0.0, tc = 0.12)
        parts += upper.parts()
        val x = wing.semi * 0.72
        for (f in listOf(0.2, 0.75)) {
            parts += Wire(listOf(P3(x, wing.le(x) + wing.chord(x) * f, wing.z(x)), P3(x, upper.le(x) + upper.chord(x) * f, upper.z(x))), mirror = true)
            parts += Wire(listOf(P3(W * 0.35, rootLE + root * f, sec.top), P3(W * 0.2, upper.le(0.2) + root * f, upper.z(0.2))), mirror = true)
        }
    }
    if (twinAt != null) {
        val x = twinAt * wing.semi
        val dia = H * 0.42
        val front = wing.le(x) - nacelleLen * 0.45
        val cz = wing.z(x) + (if (highWing) -0.15 else 0.15) * dia
        parts += Nacelle(P3(x, front, cz), nacelleLen, dia, NacelleStyle.PROP)
        parts += propRotor(prop, P3(x, front, cz), nacelleLen, mirror = true)
        features += "2 × ${engineWord(NacelleStyle.PROP, prop)}"
    } else {
        val nose = body.sections.first()
        parts += Rotor(P3(0.0, nose.s, nose.zc), P3.FWD, prop.dia, prop.blades, chord = prop.dia * 0.08, rev = 2.8, spinner = 0.32)
        features += "1 × ${engineWord(NacelleStyle.PROP, prop)}"
    }
    features += "${prop.blades}-BLADE PROPELLER"
    val emp = Empennage(finH, finRoot, finTip, finSweep, stabSpan, stabRoot, stabTip, stabSweep, tail, 0.0, 1.0, dorsal = true)
    parts += emp.parts(body, L)
    features += emp.features()
    when (gear) {
        Gear.TRICYCLE, Gear.TAILDRAGGER -> {
            val mainS = if (gear == Gear.TAILDRAGGER) rootLE - root * 0.1 else rootLE + root * 0.55
            parts += fixedGear(body, L, H, mainS, W * 2.2, gear == Gear.TAILDRAGGER, 0.2)
            features += if (gear == Gear.TAILDRAGGER) "TAILWHEEL GEAR" else "FIXED GEAR"
        }
        else -> Unit
    }
    (shield ?: canopy)?.let { features += it.label }
    return Design(parts, features.toList(), L, max(span, biplane ?: 0.0))
}

enum class HeliStyle { POD, UTILITY, TRANSPORT, ATTACK }

sealed interface HeliTail {
    data class Rotor(val dia: Double, val blades: Int) : HeliTail
    data class Fenestron(val dia: Double, val blades: Int = 10) : HeliTail
    data object None : HeliTail
}

private val HELI_POD = arrayOf(
    doubleArrayOf(0.00, 0.25, -0.25, 0.25, 0.25),
    doubleArrayOf(0.04, 0.75, -0.10, 0.65, 0.70),
    doubleArrayOf(0.12, 0.95, 0.00, 0.95, 0.90),
    doubleArrayOf(0.28, 1.00, 0.00, 1.05, 0.95),
    doubleArrayOf(0.40, 0.90, 0.05, 1.00, 0.85),
    doubleArrayOf(0.50, 0.55, 0.20, 0.65, 0.45),
    doubleArrayOf(0.58, 0.22, 0.35, 0.22, 0.20),
    doubleArrayOf(1.00, 0.10, 0.45, 0.12, 0.12),
)
private val HELI_UTILITY = arrayOf(
    doubleArrayOf(0.00, 0.20, -0.30, 0.20, 0.20),
    doubleArrayOf(0.04, 0.70, -0.15, 0.60, 0.65),
    doubleArrayOf(0.12, 0.92, 0.00, 0.90, 0.92),
    doubleArrayOf(0.25, 1.00, 0.00, 1.00, 1.00),
    doubleArrayOf(0.48, 1.00, 0.02, 1.08, 0.98),
    doubleArrayOf(0.58, 0.60, 0.18, 0.70, 0.52),
    doubleArrayOf(0.66, 0.26, 0.32, 0.26, 0.24),
    doubleArrayOf(1.00, 0.14, 0.42, 0.14, 0.14),
)
private val HELI_TRANSPORT = arrayOf(
    doubleArrayOf(0.00, 0.30, -0.25, 0.35, 0.40),
    doubleArrayOf(0.03, 0.75, -0.10, 0.75, 0.80),
    doubleArrayOf(0.10, 0.98, 0.00, 0.98, 0.98),
    doubleArrayOf(0.80, 1.00, 0.00, 1.00, 1.00),
    doubleArrayOf(0.92, 0.95, 0.10, 1.10, 0.80),
    doubleArrayOf(1.00, 0.80, 0.25, 1.00, 0.55),
)
private val HELI_ATTACK = arrayOf(
    doubleArrayOf(0.00, 0.15, -0.20, 0.15, 0.15),
    doubleArrayOf(0.05, 0.60, -0.10, 0.55, 0.60),
    doubleArrayOf(0.16, 0.85, 0.00, 0.95, 0.85),
    doubleArrayOf(0.30, 0.95, 0.05, 1.05, 0.85),
    doubleArrayOf(0.45, 1.00, 0.08, 1.00, 0.85),
    doubleArrayOf(0.56, 0.55, 0.20, 0.55, 0.45),
    doubleArrayOf(1.00, 0.18, 0.40, 0.18, 0.16),
)

/**
 * Helicopters: fuselage [L] × [W] × [H] (without rotors), main rotor [rotor] m with [blades];
 * [tandem] = Chinook, [coaxial] = Kamov; [stubWing] span for gunships.
 */
internal fun heli(
    L: Double, W: Double, H: Double, rotor: Double, blades: Int,
    style: HeliStyle = HeliStyle.POD, tailRotor: HeliTail, gear: Gear = Gear.SKIDS,
    tandem: Boolean = false, coaxial: Boolean = false, stubWing: Double = 0.0, twinFins: Boolean = false,
    /** Nose and cabin glazing, over the zone from the nose back past the doors. */
    shield: Windshield = Windshields.HELI_UTILITY,
): Design {
    val parts = ArrayList<Part>()
    val features = LinkedHashSet<String>()
    val rows = when (style) {
        HeliStyle.POD -> HELI_POD
        HeliStyle.UTILITY -> HELI_UTILITY
        HeliStyle.TRANSPORT -> HELI_TRANSPORT
        HeliStyle.ATTACK -> HELI_ATTACK
    }
    val zone = if (style == HeliStyle.TRANSPORT) L * 0.2 else L * 0.5
    val body = Body(table(L, W, H, rows), box = if (style == HeliStyle.TRANSPORT) 3.0 else 2.3, sides = 12,
        cockpit = Cockpit(0.0, zone, shield))
    parts += body
    val mast = 0.35 * H
    fun mainRotor(s: Double, z: Double, rev: Double, phase: Double = 0.0) = Rotor(
        P3(0.0, s, z), P3.UP, rotor, blades, chord = rotor * 0.045, rev = rev, mast = mast, droop = rotor * 0.02, phase = phase,
    )
    if (tandem) {
        val front = body.at(L * 0.1)
        val rear = body.at(L * 0.9)
        parts += mainRotor(L * 0.1, front.top + mast, 0.9)
        parts += mainRotor(L * 0.9, rear.top + mast * 1.6, -0.9, PI / blades)
        parts += Wire(listOf(P3(0.0, L * 0.84, rear.top), P3(0.0, L * 0.9, rear.top + mast * 1.6), P3(0.0, L * 0.98, rear.top)), coarsest = Lod.NORMAL)
        features += "TANDEM ROTORS"
    } else {
        val hubS = L * (if (style == HeliStyle.POD) 0.3 else 0.34)
        val sec = body.at(hubS)
        parts += mainRotor(hubS, sec.top + mast, 1.0)
        if (coaxial) {
            parts += mainRotor(hubS, sec.top + mast + rotor * 0.07, -1.0, PI / blades)
            features += "COAXIAL ROTORS"
        }
        // Engine cowling hump behind the mast.
        parts += Body(pod(hubS - H * 0.5, H * 1.6, W * 0.28, sec.top - 0.05), sides = 8)
    }
    features += "$blades-BLADE MAIN ROTOR"
    val boomEnd = body.at(L)
    when (tailRotor) {
        is HeliTail.Rotor -> {
            val finH = H * 0.75
            parts += Surface(listOf(
                Rib(P3(0.0, L * 0.9, boomEnd.zc), L * 0.1, 0.05),
                Rib(P3(0.0, L * 0.9 + finH * 0.6, boomEnd.zc + finH), L * 0.06, 0.03),
            ), mirror = false, hinge = false)
            parts += Rotor(P3(boomEnd.halfW + 0.2, L * 0.97, boomEnd.zc + finH * 0.55), P3.RIGHT, tailRotor.dia, tailRotor.blades,
                chord = tailRotor.dia * 0.1, rev = 4.0)
            features += "TAIL ROTOR"
        }
        is HeliTail.Fenestron -> {
            val finH = tailRotor.dia * 1.9
            val cz = boomEnd.zc + finH * 0.45
            parts += Surface(listOf(
                Rib(P3(0.0, L * 0.86, boomEnd.zc - finH * 0.1), L * 0.14, 0.18),
                Rib(P3(0.0, L * 0.86 + finH * 0.5, boomEnd.zc + finH), L * 0.07, 0.06),
            ), mirror = false, hinge = false)
            parts += Rotor(P3(0.0, L * 0.925, cz), P3.RIGHT, tailRotor.dia, tailRotor.blades, chord = tailRotor.dia * 0.12,
                rev = 4.0, duct = 0.28, disc = false)
            features += "FENESTRON TAIL"
        }
        HeliTail.None -> Unit
    }
    if (!tandem) {
        // Small tailplane (with endplates on twin-finned types).
        val s = L * 0.78
        val sec = body.at(s)
        val semi = max(W * 0.9, L * 0.1)
        parts += Surface(listOf(Rib(P3(0.0, s, sec.zc), L * 0.06, 0.05), Rib(P3(semi, s + 0.1, sec.zc), L * 0.045, 0.04)))
        if (twinFins) {
            parts += Surface(listOf(Rib(P3(semi, s - 0.2, sec.zc - H * 0.2), L * 0.08, 0.04), Rib(P3(semi, s + H * 0.4, sec.zc + H * 0.55), L * 0.05, 0.03)))
            features += "TWIN FINS"
        }
    }
    if (stubWing > 0) {
        val s = L * 0.36
        val sec = body.at(s)
        parts += Surface(listOf(Rib(P3(sec.halfW, s, sec.zc), H * 0.45, 0.1), Rib(P3(stubWing / 2, s + 0.15, sec.zc - 0.15), H * 0.35, 0.06)))
        features += "STUB WINGS"
    }
    when (gear) {
        Gear.SKIDS -> {
            val z = body.sections.minOf { it.bottom } - H * 0.28
            val x = W * 0.58
            val s0 = L * 0.1
            val s1 = L * 0.46
            parts += Wire(listOf(P3(x, s0 - 0.35, z + 0.3), P3(x, s0, z), P3(x, s1, z)), mirror = true)
            for (s in listOf(L * 0.18, L * 0.4)) {
                val sec = body.at(s)
                parts += Wire(listOf(P3(x, s, z), P3(sec.halfW * 0.8, s, sec.bottom + 0.15)), mirror = true)
            }
            features += "SKID GEAR"
        }
        Gear.WHEELS -> {
            val z = body.sections.minOf { it.bottom } - H * 0.22
            for ((s, x) in listOf(L * 0.08 to 0.0, L * 0.4 to W * 0.62)) {
                val sec = body.at(s)
                val r = H * 0.12
                parts += Wire(listOf(P3(x * 0.6, s, sec.bottom + 0.1), P3(x, s, z + r)), mirror = x > 0)
                parts += Drum(P3(x, s, z + r), P3.RIGHT, r, depth = r * 0.6, sides = 10, mirror = x > 0)
            }
            features += "WHEELED GEAR"
        }
        else -> Unit
    }
    val overall = if (tandem) L + rotor * 0.6 else max(L, rotor / 2 + L * 0.7)
    features += shield.label
    return Design(parts, features.toList(), overall, rotor, rotorM = rotor)
}

private val FIGHTER = arrayOf(
    doubleArrayOf(0.00, 0.00, -0.05, 0.00, 0.00),
    doubleArrayOf(0.06, 0.22, -0.04, 0.28, 0.28),
    doubleArrayOf(0.16, 0.38, 0.00, 0.48, 0.44),
    doubleArrayOf(0.30, 0.78, -0.04, 0.55, 0.60),
    doubleArrayOf(0.55, 1.00, 0.00, 0.52, 0.55),
    doubleArrayOf(0.80, 0.85, 0.00, 0.46, 0.46),
    doubleArrayOf(0.94, 0.62, 0.00, 0.40, 0.40),
    doubleArrayOf(1.00, 0.50, 0.00, 0.36, 0.36),
)

/** Fast jets and jet trainers: pointed body, canopy, low-aspect wing, one or two nozzles. */
internal fun fighter(
    L: Double, span: Double, W: Double, H: Double,
    root: Double, tipChord: Double, sweep: Double, wingAt: Double = 0.42, wingZ: Double = 0.0, dihedral: Double = -1.0,
    tail: TailKind = TailKind.Low, finH: Double, finRoot: Double, finTip: Double, finSweep: Double = 45.0,
    stabSpan: Double = 0.0, stabRoot: Double = 0.0, stabTip: Double = 0.0, stabSweep: Double = 40.0,
    canard: Double = 0.0, engines: Int = 1, tip: Tip = Tip.None, podEngines: Boolean = false,
    /** Canopy framing (see [Windshields]). */
    shield: Windshield = Windshields.CANOPY_FRAMED,
): Design {
    val parts = ArrayList<Part>()
    val features = LinkedHashSet<String>()
    val body = Body(table(L, W, H, FIGHTER), box = 2.2, sides = 12)
    parts += body
    parts += Body(pod(L * 0.12, L * 0.24, W * 0.2, body.at(L * 0.22).top - 0.05), sides = 10, rings = false,
        cockpit = Cockpit(L * 0.12, L * 0.24, shield))
    val sec = body.at(wingAt * L + root * 0.4)
    val wing = WingPlan(span / 2, wingAt * L, root, tipChord, sweep, dihedral, sec.zc + wingZ * sec.up, x0 = sec.halfW * 0.9, tc = 0.05, tip = tip)
    parts += wing.parts()
    features += wing.features()
    if (canard > 0) {
        val s = L * 0.24
        val cs = body.at(s)
        parts += WingPlan(canard / 2, s, root * 0.3, root * 0.1, 50.0, 3.0, cs.zc + 0.2 * cs.up, x0 = cs.halfW * 0.9, tc = 0.04).parts()
        features += "CANARDS"
    }
    val emp = Empennage(finH, finRoot, finTip, finSweep, stabSpan, stabRoot, stabTip, stabSweep, tail, -3.0, 0.99)
    parts += emp.parts(body, L)
    features += emp.features()
    val end = body.sections.last()
    if (podEngines) {
        // A-10: two pods on the rear fuselage.
        val s = L * 0.55
        val bs = body.at(s)
        parts += Nacelle(P3(bs.halfW + 0.7, s, bs.top + 0.3), L * 0.2, 1.3, NacelleStyle.FAN)
    } else if (engines == 2) {
        parts += Drum(P3(end.halfW * 0.5, L, end.zc), P3.FWD, end.halfW * 0.45, depth = 0.4, mirror = true)
    } else {
        parts += Drum(P3(0.0, L, end.zc), P3.FWD, end.up * 0.85, depth = 0.5)
    }
    features += "$engines × ${if (podEngines) "TURBOFAN" else "JET ENGINE"}"
    if (span < L * 0.8 && sweep > 45) features += "DELTA WING"
    features += shield.label
    return Design(parts, features.toList(), L, span)
}

private val GLIDER = arrayOf(
    doubleArrayOf(0.00, 0.00, 0.00, 0.00, 0.00),
    doubleArrayOf(0.05, 0.60, 0.00, 0.60, 0.60),
    doubleArrayOf(0.15, 0.95, 0.05, 0.95, 0.90),
    doubleArrayOf(0.30, 0.90, 0.10, 0.85, 0.80),
    doubleArrayOf(0.45, 0.55, 0.15, 0.55, 0.50),
    doubleArrayOf(0.70, 0.30, 0.20, 0.30, 0.28),
    doubleArrayOf(1.00, 0.18, 0.25, 0.20, 0.18),
)

internal fun glider(L: Double = 7.0, span: Double = 18.0): Design {
    val W = 0.65
    val H = 0.85
    val body = Body(table(L, W, H, GLIDER), sides = 10)
    val parts = ArrayList<Part>()
    parts += body
    parts += Body(pod(L * 0.06, L * 0.3, W * 0.42, body.at(L * 0.2).top - 0.08), sides = 8, rings = false,
        cockpit = Cockpit(L * 0.06, L * 0.3, Windshields.GLIDER_CANOPY))
    val sec = body.at(L * 0.34)
    parts += WingPlan(span / 2, L * 0.32, 1.0, 0.4, 1.5, 3.0, sec.top - 0.1, x0 = W * 0.4, tc = 0.12,
        tip = Tip.Winglet(0.35, cant = 10.0, blend = 0.0)).parts()
    val emp = Empennage(1.2, 0.95, 0.55, 20.0, 2.8, 0.55, 0.35, 3.0, TailKind.T, 0.0, 1.0)
    parts += emp.parts(body, L)
    return Design(parts, listOf("SAILPLANE", "HIGH-ASPECT WING", "T-TAIL", Windshields.GLIDER_CANOPY.label), L, span)
}

/** Hot-air balloon: envelope of [gores] panels, basket and flying wires. */
internal fun balloon(r: Double = 8.5, gores: Int = 16): Design {
    val parts = ArrayList<Part>()
    // Envelope centre a little above the datum so the whole balloon straddles it.
    val cz = r * 0.35
    // Meridians from crown to mouth, bulbous at the top and tapering to the throat.
    val profile = (0..10).map { i ->
        val t = i / 10.0
        val a = PI * (0.5 - t * 0.85)
        val rr = r * cos(a) * (if (t > 0.6) 1 - (t - 0.6) * 0.9 else 1.0)
        rr to (cz + r * sin(a) * (if (a < 0) 1.35 else 1.0))
    }
    for (g in 0 until gores) {
        val phi = 2 * PI * g / gores
        parts += Wire(profile.map { (rr, z) -> P3(rr * cos(phi), rr * sin(phi), z) }, coarsest = if (g % 2 == 0) Lod.LITE else Lod.NORMAL)
    }
    for ((rr, z) in profile.filterIndexed { i, _ -> i in 1..9 step 2 }) {
        parts += Drum(P3(0.0, 0.0, z), P3.UP, rr, sides = gores * 2)
    }
    val mouth = profile.last()
    val basketTop = mouth.second - r * 0.45
    val b = 0.65
    for (dz in listOf(0.0, -1.1)) parts += Wire(listOf(P3(b, b, basketTop + dz), P3(-b, b, basketTop + dz), P3(-b, -b, basketTop + dz), P3(b, -b, basketTop + dz)), closed = true)
    for ((x, y) in listOf(b to b, -b to b, -b to -b, b to -b)) {
        parts += Wire(listOf(P3(x, y, basketTop), P3(x, y, basketTop - 1.1)))
        parts += Wire(listOf(P3(x, y, basketTop), P3(x / b * mouth.first, y / b * mouth.first, mouth.second)))
    }
    return Design(parts, listOf("HOT-AIR ENVELOPE", "WICKER BASKET"), cz + r - (basketTop - 1.1), r * 2)
}

/** A MALE drone (Reaper-like): long wing, inverted V tail, pusher propeller. */
internal fun drone(L: Double = 11.0, span: Double = 20.0): Design = light(
    L = L, W = 1.0, H = 1.1, highWing = false, span = span, root = 1.1, tipChord = 0.5, dihedral = 2.0, wingAt = 0.5,
    strut = false, prop = Prop(2.9, 3, pusher = true), tail = TailKind.V(-40.0), finH = 0.9, finRoot = 0.9,
    stabSpan = 4.0, stabRoot = 0.8, stabTip = 0.5, stabSweep = 15.0, gear = Gear.NONE, shield = null,
).let { d ->
    // Move the propeller from the nose to the tail.
    val parts = d.parts.map { p ->
        if (p is Rotor && p.axis == P3.FWD) p.copy(hub = P3(0.0, L + 0.1, 0.2), axis = P3.AFT, spinner = 0.25) else p
    }
    Design(parts, listOf("PUSHER PROPELLER", "INVERTED V-TAIL", "SATCOM DOME"), L, span)
}
