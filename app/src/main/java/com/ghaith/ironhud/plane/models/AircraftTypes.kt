package com.ghaith.ironhud.plane.models

import com.ghaith.ironhud.plane.ModelKind
import com.ghaith.ironhud.plane.ModelKind.AIRLINER
import com.ghaith.ironhud.plane.ModelKind.BALLOON
import com.ghaith.ironhud.plane.ModelKind.BIZJET
import com.ghaith.ironhud.plane.ModelKind.DRONE
import com.ghaith.ironhud.plane.ModelKind.FIGHTER
import com.ghaith.ironhud.plane.ModelKind.GLIDER
import com.ghaith.ironhud.plane.ModelKind.HELI
import com.ghaith.ironhud.plane.ModelKind.LIGHT
import com.ghaith.ironhud.plane.ModelKind.MILITARY
import com.ghaith.ironhud.plane.ModelKind.QUAD
import com.ghaith.ironhud.plane.ModelKind.REGIONAL
import com.ghaith.ironhud.plane.ModelKind.TURBOPROP
import com.ghaith.ironhud.plane.ModelKind.WIDEBODY
import com.ghaith.ironhud.plane.models.NacelleStyle.JET
import com.ghaith.ironhud.plane.models.NacelleStyle.PROP
import java.util.Locale

/**
 * Every aircraft type the HUD can draw, keyed by ICAO type designator, with variants that differ in
 * the ways you can see: fuselage stretches, wingtip devices, engine generations, upper decks.
 * Dimensions are the manufacturers' published length and span; the rest is proportioned from
 * three-view drawings.
 */
object AircraftTypes {

    /** Every exact type, in catalogue order (manufacturer families together). */
    val catalog: List<Airframe> by lazy { Catalog().apply { fill() }.list }

    private val byCode: Map<String, Airframe> by lazy { catalog.flatMap { a -> a.codes.map { it to a } }.toMap() }

    fun byCode(code: String?): Airframe? = code?.trim()?.uppercase(Locale.US)?.let { byCode[it] }

    /** The exact type if known, else a stand-in from the feed's description or ADS-B category. */
    fun resolve(typeCode: String?, category: String?, description: String? = null): Airframe {
        byCode(typeCode)?.let { return it }
        description?.uppercase(Locale.US)?.let { d ->
            DESCRIPTION_HINTS.firstOrNull { (words, _) -> words.any { it in d } }?.let { return it.second }
        }
        return when (category?.uppercase(Locale.US)) {
            "A1" -> GENERIC_LIGHT
            "A2" -> GENERIC_BIZ
            "A4" -> GENERIC_B757
            "A5" -> GENERIC_HEAVY
            "A6" -> GENERIC_FAST
            "A7" -> GENERIC_HELI
            "B1" -> GENERIC_GLIDER
            "B2" -> GENERIC_BALLOON
            "B4" -> GENERIC_ULTRALIGHT
            "B6" -> GENERIC_DRONE
            else -> GENERIC_JET
        }
    }

    private val GENERIC_JET = Airframe(listOf("?A3"), "Airliner", AIRLINER, generic = true) { a320(37.57, neo = true) }
    private val GENERIC_B757 = Airframe(listOf("?A4"), "Large jet", AIRLINER, generic = true) { b757(47.32) }
    private val GENERIC_HEAVY = Airframe(listOf("?A5"), "Heavy jet", WIDEBODY, generic = true) { b777(63.73, 60.93, Tip.None, GE90) }
    private val GENERIC_BIZ = Airframe(listOf("?A2"), "Business jet", BIZJET, generic = true) { cl300(21.0, true) }
    private val GENERIC_LIGHT = Airframe(listOf("?A1"), "Light aircraft", LIGHT, generic = true) { c172(8.28, 11.0, Prop(1.93, 2, piston = true)) }
    private val GENERIC_FAST = Airframe(listOf("?A6"), "Fast jet", FIGHTER, generic = true) { f16() }
    private val GENERIC_HELI = Airframe(listOf("?A7"), "Helicopter", HELI, generic = true) {
        heli(10.2, 1.56, 1.6, 10.2, 4, HeliStyle.POD, HeliTail.Fenestron(1.0))
    }
    private val GENERIC_GLIDER = Airframe(listOf("?B1"), "Glider", GLIDER, generic = true) { glider() }
    private val GENERIC_BALLOON = Airframe(listOf("?B2"), "Balloon", BALLOON, generic = true) { balloon() }
    private val GENERIC_ULTRALIGHT = Airframe(listOf("?B4"), "Ultralight", LIGHT, generic = true) {
        c172(6.25, 9.45, Prop(1.7, 3, piston = true))
    }
    private val GENERIC_DRONE = Airframe(listOf("?B6"), "Drone", DRONE, generic = true) { drone() }

    private val DESCRIPTION_HINTS = listOf(
        listOf("HELICOPTER", "ROBINSON", "EUROCOPTER", "SIKORSKY", "AGUSTA", "LEONARDO AW", "MIL MI-", "KAMAN") to GENERIC_HELI,
        listOf("BALLOON", "CAMERON", "LINDSTRAND", "KUBICEK", "ULTRAMAGIC") to GENERIC_BALLOON,
        listOf("GLIDER", "SCHLEICHER", "SCHEMPP", "ROLLADEN", "DG FLUGZEUG", "SAILPLANE") to GENERIC_GLIDER,
        listOf("CESSNA 1", "PIPER", "CIRRUS", "DIAMOND", "MOONEY", "ROBIN", "TECNAM") to GENERIC_LIGHT,
    )
}

private class Catalog {
    val list = ArrayList<Airframe>()

    fun add(codes: String, name: String, kind: ModelKind, power: String?, build: () -> Design) {
        list += Airframe(codes.split(' '), name, kind, power, build = build)
    }

    fun fill() {
        // ---- Airbus narrow-bodies -----------------------------------------------------------------
        add("A318", "Airbus A318", AIRLINER, "2 × CFM56-5B / PW6000") { a320(31.44, neo = false) }
        add("A319", "Airbus A319", AIRLINER, "2 × CFM56-5B / IAE V2500") { a320(33.84, neo = false) }
        add("A320", "Airbus A320", AIRLINER, "2 × CFM56-5B / IAE V2500") { a320(37.57, neo = false) }
        add("A321", "Airbus A321", AIRLINER, "2 × CFM56-5B / IAE V2500") { a320(44.51, neo = false) }
        add("A19N", "Airbus A319neo", AIRLINER, "2 × CFM LEAP-1A / PW1100G") { a320(33.84, neo = true) }
        add("A20N", "Airbus A320neo", AIRLINER, "2 × CFM LEAP-1A / PW1100G") { a320(37.57, neo = true) }
        add("A21N", "Airbus A321neo", AIRLINER, "2 × CFM LEAP-1A / PW1100G") { a320(44.51, neo = true) }
        add("BCS1", "Airbus A220-100", AIRLINER, "2 × PW1500G") { a220(35.0) }
        add("BCS3", "Airbus A220-300", AIRLINER, "2 × PW1500G") { a220(38.7) }

        // ---- Airbus wide-bodies -------------------------------------------------------------------
        add("A30B", "Airbus A300B4", WIDEBODY, "2 × GE CF6-50 / PW JT9D") { a300(53.62, 44.84, 19.3, Tip.None) }
        add("A306", "Airbus A300-600", WIDEBODY, "2 × GE CF6-80C2 / PW4158") { a300(54.08, 44.84, 19.5, Tip.Fence(1.5)) }
        add("A310", "Airbus A310", WIDEBODY, "2 × GE CF6-80C2 / PW4000") { a300(46.66, 43.9, 16.5, Tip.Fence(1.5)) }
        add("A3ST", "Airbus A300-600ST Beluga", WIDEBODY, "2 × GE CF6-80C2") {
            a300(56.15, 44.84, 19.5, Tip.Fence(1.5), lobe = Hump(3.5, 46.0, 3.6, 7.0, 9.0, widen = 1.55))
        }
        add("A332", "Airbus A330-200", WIDEBODY, "2 × GE CF6-80E1 / PW4000 / RR Trent 700") { a330(58.82, 60.3, A330_WINGLET, TRENT700) }
        add("A333", "Airbus A330-300", WIDEBODY, "2 × GE CF6-80E1 / PW4000 / RR Trent 700") { a330(63.67, 60.3, A330_WINGLET, TRENT700) }
        add("A337", "Airbus BelugaXL", WIDEBODY, "2 × RR Trent 700") {
            a330(63.1, 60.3, A330_WINGLET, TRENT700, lobe = Hump(4.0, 50.0, 4.0, 7.0, 10.0, widen = 1.5))
        }
        add("A338", "Airbus A330-800neo", WIDEBODY, "2 × RR Trent 7000") { a330(58.82, 64.0, A330NEO_SHARKLET, TRENT7000) }
        add("A339", "Airbus A330-900neo", WIDEBODY, "2 × RR Trent 7000") { a330(63.66, 64.0, A330NEO_SHARKLET, TRENT7000) }
        add("A342", "Airbus A340-200", QUAD, "4 × CFM56-5C") { a330(59.4, 60.3, A330_WINGLET, CFM56_5C) }
        add("A343", "Airbus A340-300", QUAD, "4 × CFM56-5C") { a330(63.69, 60.3, A330_WINGLET, CFM56_5C) }
        add("A345", "Airbus A340-500", QUAD, "4 × RR Trent 500") { a330(67.93, 63.45, A330_WINGLET, TRENT500) }
        add("A346", "Airbus A340-600", QUAD, "4 × RR Trent 500") { a330(75.36, 63.45, A330_WINGLET, TRENT500) }
        add("A359", "Airbus A350-900", WIDEBODY, "2 × RR Trent XWB-84") { a350(66.8, XWB84) }
        add("A35K", "Airbus A350-1000", WIDEBODY, "2 × RR Trent XWB-97") { a350(73.79, XWB97) }
        add("A388", "Airbus A380-800", QUAD, "4 × RR Trent 900 / EA GP7200") { a380() }
        add("A400", "Airbus A400M Atlas", MILITARY, "4 × Europrop TP400-D6") { a400m() }

        // ---- Boeing narrow-bodies -----------------------------------------------------------------
        add("B712", "Boeing 717", AIRLINER, "2 × RR BR715") { dc9(37.81, 28.45, BR715) }
        add("B721", "Boeing 727-100", AIRLINER, "3 × PW JT8D") { b727(40.59) }
        add("B722", "Boeing 727-200", AIRLINER, "3 × PW JT8D") { b727(46.69) }
        add("B732", "Boeing 737-200", AIRLINER, "2 × PW JT8D") { b737(30.53, 28.35, Tip.None, JT8D_737, root = 6.5) }
        add("B733", "Boeing 737-300", AIRLINER, "2 × CFM56-3") { b737(33.4, 28.88, Tip.None, CFM56_3, root = 6.8) }
        add("B734", "Boeing 737-400", AIRLINER, "2 × CFM56-3") { b737(36.4, 28.88, Tip.None, CFM56_3, root = 6.8) }
        add("B735", "Boeing 737-500", AIRLINER, "2 × CFM56-3") { b737(31.0, 28.88, Tip.None, CFM56_3, root = 6.8) }
        add("B736", "Boeing 737-600", AIRLINER, "2 × CFM56-7B") { b737(31.24, 34.32, Tip.None, CFM56_7) }
        add("B737", "Boeing 737-700", AIRLINER, "2 × CFM56-7B") { b737(33.63, 35.79, NG_WINGLET, CFM56_7) }
        add("B738", "Boeing 737-800", AIRLINER, "2 × CFM56-7B") { b737(39.47, 35.79, NG_WINGLET, CFM56_7) }
        add("B739", "Boeing 737-900", AIRLINER, "2 × CFM56-7B") { b737(42.11, 35.79, NG_WINGLET, CFM56_7) }
        add("B37M", "Boeing 737 MAX 7", AIRLINER, "2 × CFM LEAP-1B") { b737(35.56, 35.92, MAX_WINGLET, LEAP_1B, max = true) }
        add("B38M", "Boeing 737 MAX 8", AIRLINER, "2 × CFM LEAP-1B") { b737(39.52, 35.92, MAX_WINGLET, LEAP_1B, max = true) }
        add("B39M", "Boeing 737 MAX 9", AIRLINER, "2 × CFM LEAP-1B") { b737(42.16, 35.92, MAX_WINGLET, LEAP_1B, max = true) }
        add("B3XM", "Boeing 737 MAX 10", AIRLINER, "2 × CFM LEAP-1B") { b737(43.8, 35.92, MAX_WINGLET, LEAP_1B, max = true) }
        add("P8", "Boeing P-8 Poseidon", MILITARY, "2 × CFM56-7B") { b737(39.47, 37.64, Tip.Raked(1.9), CFM56_7, windows = false) }
        add("B752", "Boeing 757-200", AIRLINER, "2 × RR RB211-535 / PW2000") { b757(47.32) }
        add("B753", "Boeing 757-300", AIRLINER, "2 × RR RB211-535 / PW2000") { b757(54.47) }

        // ---- Boeing wide-bodies -------------------------------------------------------------------
        add("B762", "Boeing 767-200", WIDEBODY, "2 × GE CF6-80 / PW JT9D") { b767(48.51, 47.57, Tip.None) }
        add("B763", "Boeing 767-300", WIDEBODY, "2 × GE CF6-80C2 / PW4000 / RB211-524") {
            b767(54.94, 50.88, Tip.Winglet(3.4, cant = 15.0, sweep = 40.0, blend = 0.35))
        }
        add("B764", "Boeing 767-400ER", WIDEBODY, "2 × GE CF6-80C2") { b767(61.37, 51.92, Tip.Raked(2.2)) }
        add("B772", "Boeing 777-200", WIDEBODY, "2 × GE90 / PW4000 / RR Trent 800") { b777(63.73, 60.93, Tip.None, GE90) }
        add("B77L", "Boeing 777-200LR / 777F", WIDEBODY, "2 × GE90-110B / -115B") { b777(63.73, 64.8, Tip.Raked(3.9), GE90_115) }
        add("B773", "Boeing 777-300", WIDEBODY, "2 × PW4098 / RR Trent 892") { b777(73.86, 60.93, Tip.None, GE90) }
        add("B77W", "Boeing 777-300ER", WIDEBODY, "2 × GE90-115B") { b777(73.86, 64.8, Tip.Raked(3.9), GE90_115) }
        add("B778", "Boeing 777-8", WIDEBODY, "2 × GE9X") { b777(70.87, 71.75, Tip.Folding(3.5, 3.5), GE9X) }
        add("B779", "Boeing 777-9", WIDEBODY, "2 × GE9X") { b777(76.72, 71.75, Tip.Folding(3.5, 3.5), GE9X) }
        add("B788", "Boeing 787-8 Dreamliner", WIDEBODY, "2 × GEnx-1B / RR Trent 1000") { b787(56.72) }
        add("B789", "Boeing 787-9 Dreamliner", WIDEBODY, "2 × GEnx-1B / RR Trent 1000") { b787(62.81) }
        add("B78X", "Boeing 787-10 Dreamliner", WIDEBODY, "2 × GEnx-1B / RR Trent 1000") { b787(68.28) }
        add("B741 B742", "Boeing 747-100/200", QUAD, "4 × PW JT9D / GE CF6-50 / RB211-524") { b747(70.6, 59.64, Tip.None, 23.0, JT9D) }
        add("B743", "Boeing 747-300", QUAD, "4 × PW JT9D / GE CF6-50 / RB211-524") { b747(70.6, 59.64, Tip.None, 30.5, JT9D) }
        add("B744", "Boeing 747-400", QUAD, "4 × GE CF6-80C2 / PW4056 / RB211-524") {
            b747(70.66, 64.44, Tip.Winglet(1.83, cant = 29.0, sweep = 60.0, blend = 0.0), 30.5, CF6_747)
        }
        add("B748", "Boeing 747-8", QUAD, "4 × GEnx-2B") { b747(76.25, 68.4, Tip.Raked(4.2), 34.5, GENX_2B) }
        add("B74S", "Boeing 747SP", QUAD, "4 × PW JT9D / RB211-524") { b747(56.31, 59.64, Tip.None, 23.0, JT9D, wingLE = 16.5) }
        add("BLCF", "Boeing 747 Dreamlifter", QUAD, "4 × PW4062") {
            b747(71.68, 64.44, Tip.Winglet(1.83, cant = 29.0, sweep = 60.0, blend = 0.0), 30.5, CF6_747,
                lobe = Hump(12.0, 60.0, 3.6, 9.0, 8.0, widen = 1.35))
        }
        add("B703", "Boeing 707-320", QUAD, "4 × PW JT3D") { b707(46.61, 44.42, JT3D) }
        add("K35R", "Boeing KC-135R Stratotanker", MILITARY, "4 × CFM F108") { kc135() }
        add("E3TF E3CF", "Boeing E-3 Sentry AWACS", MILITARY, "4 × PW TF33") { e3() }

        // ---- McDonnell Douglas & Lockheed ---------------------------------------------------------
        add("DC93", "McDonnell Douglas DC-9-30", AIRLINER, "2 × PW JT8D") { dc9(36.37, 28.47, JT8D_DC9, stabSpan = 11.23) }
        add("MD81 MD82 MD83 MD88", "McDonnell Douglas MD-80", AIRLINER, "2 × PW JT8D-200") { dc9(45.06, 32.87, JT8D_200) }
        add("MD87", "McDonnell Douglas MD-87", AIRLINER, "2 × PW JT8D-200") { dc9(39.75, 32.87, JT8D_200) }
        add("MD90", "McDonnell Douglas MD-90", AIRLINER, "2 × IAE V2500") { dc9(46.51, 32.87, V2500_MD90) }
        add("DC10", "McDonnell Douglas DC-10 / KC-10", WIDEBODY, "3 × GE CF6-50") {
            dc10(55.5, 50.4, Tip.None)
        }
        add("MD11", "McDonnell Douglas MD-11", WIDEBODY, "3 × GE CF6-80C2 / PW4460") {
            dc10(61.6, 51.97, Tip.Winglet(2.1, cant = 15.0, sweep = 45.0, blend = 0.0, lower = 0.9, label = "UPPER + LOWER WINGLETS"))
        }
        add("L101", "Lockheed L-1011 TriStar", WIDEBODY, "3 × RR RB211-22") { tristar() }
        add("C130", "Lockheed C-130 Hercules", MILITARY, "4 × Allison T56") { c130(29.79, 4) }
        add("C30J", "Lockheed C-130J Super Hercules", MILITARY, "4 × RR AE 2100D3") { c130(34.69, 6) }
        add("C5M", "Lockheed C-5M Super Galaxy", MILITARY, "4 × GE F138") { c5() }
        add("P3", "Lockheed P-3 Orion", MILITARY, "4 × Allison T56") { p3() }
        add("C17", "Boeing C-17 Globemaster III", MILITARY, "4 × PW F117") { c17() }
        add("B52", "Boeing B-52 Stratofortress", MILITARY, "8 × PW TF33") { b52() }
        add("V22", "Bell Boeing V-22 Osprey", MILITARY, "2 × RR AE 1107C") { v22() }

        // ---- Embraer ------------------------------------------------------------------------------
        add("E135", "Embraer ERJ 135", REGIONAL, "2 × RR AE 3007") { erj(26.33) }
        add("E145", "Embraer ERJ 145", REGIONAL, "2 × RR AE 3007") { erj(29.87) }
        add("E35L", "Embraer Legacy 600/650", BIZJET, "2 × RR AE 3007") { erj(26.33, 21.17, Tip.Winglet(1.0, cant = 15.0, blend = 0.0)) }
        add("E170", "Embraer E170", REGIONAL, "2 × GE CF34-8E") { ejet(29.9, 26.0, E1_WINGLET, CF34_8E, big = false) }
        add("E75S E75L", "Embraer E175", REGIONAL, "2 × GE CF34-8E") { ejet(31.68, 28.65, E1_WINGLET, CF34_8E, big = false) }
        add("E190", "Embraer E190", REGIONAL, "2 × GE CF34-10E") { ejet(36.24, 28.72, E1_WINGLET, CF34_10E, big = true) }
        add("E195", "Embraer E195", REGIONAL, "2 × GE CF34-10E") { ejet(38.65, 28.72, E1_WINGLET, CF34_10E, big = true) }
        add("E290", "Embraer E190-E2", REGIONAL, "2 × PW1900G") { ejet(36.25, 33.72, Tip.Raked(1.6), PW1900G, big = true) }
        add("E295", "Embraer E195-E2", REGIONAL, "2 × PW1900G") { ejet(41.5, 35.12, Tip.Raked(1.6), PW1900G, big = true) }
        add("E50P", "Embraer Phenom 100", BIZJET, "2 × PW617F") {
            biz(12.82, 1.75, 12.3, 2.3, 1.0, 7.0, RearEng((12.82 - 4.2) / 12.82, 2.2, 0.85), finH = 2.3, stabSpan = 5.1)
        }
        add("E55P", "Embraer Phenom 300", BIZJET, "2 × PW535E") {
            biz(15.9, 1.75, 16.2, 2.7, 1.0, 26.0, RearEng((15.9 - 5.2) / 15.9, 2.7, 1.0), tip = BIZ_WINGLET, finH = 2.6, stabSpan = 5.8)
        }
        add("E545 E550", "Embraer Praetor 500/600", BIZJET, "2 × Honeywell HTF7500E") {
            biz(20.74, 2.3, 20.25, 3.6, 1.1, 25.0, RearEng((20.74 - 6.4) / 20.74, 3.2, 1.3), tip = BIZ_WINGLET, finH = 3.2, stabSpan = 7.4)
        }

        // ---- Bombardier / de Havilland Canada -----------------------------------------------------
        add("CRJ1 CRJ2", "Bombardier CRJ100/200", REGIONAL, "2 × GE CF34-3") { crj(26.77, 21.21, 6.35, big = false) }
        add("CRJ7", "Bombardier CRJ700", REGIONAL, "2 × GE CF34-8C") { crj(32.51, 23.24, 8.5, big = true) }
        add("CRJ9", "Bombardier CRJ900", REGIONAL, "2 × GE CF34-8C") { crj(36.4, 24.85, 8.5, big = true) }
        add("CRJX", "Bombardier CRJ1000", REGIONAL, "2 × GE CF34-8C") { crj(39.1, 26.18, 8.5, big = true) }
        add("CL60", "Bombardier Challenger 600/650", BIZJET, "2 × GE CF34-3") { crj(20.85, 19.61, 6.35, big = false, biz = true) }
        add("CL30", "Bombardier Challenger 300", BIZJET, "2 × Honeywell HTF7000") { cl300(19.46, false) }
        add("CL35", "Bombardier Challenger 350", BIZJET, "2 × Honeywell HTF7350") { cl300(21.0, true) }
        add("GLEX", "Bombardier Global Express / 6000", BIZJET, "2 × RR BR710") { global(30.3, 28.65, BR710_GLOBAL) }
        add("GL5T", "Bombardier Global 5000", BIZJET, "2 × RR BR710") { global(29.5, 28.65, BR710_GLOBAL) }
        add("GL7T", "Bombardier Global 7500", BIZJET, "2 × GE Passport") { global(33.8, 31.7, PASSPORT) }
        add("LJ35", "Learjet 35", BIZJET, "2 × Honeywell TFE731-2") {
            biz(14.83, 1.6, 12.04, 2.6, 1.4, 13.0, RearEng((14.83 - 4.7) / 14.83, 2.7, 0.9), tip = Tip.TipTank(3.9, 0.7), finH = 2.6, stabSpan = 4.5)
        }
        add("LJ45", "Learjet 45", BIZJET, "2 × Honeywell TFE731-20") {
            biz(17.68, 1.7, 14.58, 2.8, 1.1, 13.0, RearEng((17.68 - 5.4) / 17.68, 2.9, 1.0), tip = BIZ_WINGLET, finH = 2.8, stabSpan = 5.0)
        }
        add("LJ60", "Learjet 60", BIZJET, "2 × PW305A") {
            biz(17.88, 1.7, 13.34, 2.8, 1.1, 13.0, RearEng((17.88 - 5.4) / 17.88, 3.0, 1.05), tip = BIZ_WINGLET, finH = 2.8, stabSpan = 4.5)
        }
        add("LJ75", "Learjet 75", BIZJET, "2 × Honeywell TFE731-40BR") {
            biz(17.68, 1.7, 15.51, 2.8, 1.1, 13.0, RearEng((17.68 - 5.4) / 17.68, 2.9, 1.0), tip = BIZ_WINGLET, finH = 2.8, stabSpan = 5.0)
        }
        add("DH8A DH8B", "De Havilland Canada Dash 8-100/200", TURBOPROP, "2 × PW PW120A") { dash8(22.25, 25.89, 4, 3.96, 5.8) }
        add("DH8C", "De Havilland Canada Dash 8-300", TURBOPROP, "2 × PW PW123") { dash8(25.68, 27.43, 4, 3.96, 5.8) }
        add("DH8D", "De Havilland Canada Dash 8-400", TURBOPROP, "2 × PW PW150A") { dash8(32.83, 28.42, 6, 4.11, 7.2) }
        add("DHC6", "De Havilland Canada Twin Otter", TURBOPROP, "2 × PW PT6A-27") {
            light(15.77, 1.6, 1.8, highWing = true, span = 19.81, root = 1.98, wingAt = 0.3, prop = Prop(2.59, 3), twinAt = 0.3,
                nacelleLen = 3.2, finH = 2.6, finRoot = 2.2, stabSpan = 6.3, stabRoot = 1.5, box = 3.0)
        }
        add("DHC2", "De Havilland Canada Beaver", LIGHT, "1 × PW R-985 Wasp Junior") {
            light(9.24, 1.4, 1.7, highWing = true, span = 14.63, root = 1.93, prop = Prop(2.59, 2, piston = true),
                finH = 1.8, finRoot = 1.8, stabSpan = 4.8, stabRoot = 1.2, gear = Gear.TAILDRAGGER, box = 2.6)
        }

        // ---- Other airliners ----------------------------------------------------------------------
        add("F70", "Fokker 70", AIRLINER, "2 × RR Tay 620") { fokker(30.91) }
        add("F100", "Fokker 100", AIRLINER, "2 × RR Tay 650") { fokker(35.53) }
        add("B461 RJ70", "BAe 146-100 / Avro RJ70", REGIONAL, "4 × Honeywell ALF502 / LF507") { bae146(26.2) }
        add("B462 RJ85", "BAe 146-200 / Avro RJ85", REGIONAL, "4 × Honeywell ALF502 / LF507") { bae146(28.6) }
        add("B463 RJ1H", "BAe 146-300 / Avro RJ100", REGIONAL, "4 × Honeywell ALF502 / LF507") { bae146(31.0) }
        add("SU95", "Sukhoi Superjet 100", REGIONAL, "2 × PowerJet SaM146") {
            jet(L = 29.94, W = 3.24, H = 3.24, nose = 1.8, tailCone = 2.7, span = 27.8, root = 5.9, tipChord = 1.3, sweep = 26.0,
                wingAt = 0.38, kink = 0.34, tip = Tip.Winglet(1.3, cant = 12.0, blend = 0.3),
                engines = listOf(WingEng(listOf(0.33), 3.9, 1.9, overhang = 0.6, drop = 0.15)),
                finH = 5.0, finRoot = 4.8, finTip = 2.0, finSweep = 40.0, stabSpan = 10.1, stabRoot = 2.9, stabTip = 1.0, stabSweep = 30.0)
        }
        add("AJ27", "COMAC ARJ21", REGIONAL, "2 × GE CF34-10A") {
            jet(L = 33.46, W = 3.14, H = 3.3, nose = 2.2, tailCone = 3.1, blunt = 0.65, span = 27.29, root = 5.8, tipChord = 1.3, sweep = 27.0,
                wingAt = 0.38, kink = 0.33, tip = Tip.Winglet(1.6, cant = 15.0, blend = 0.2),
                engines = listOf(RearEng((33.46 - 10.0) / 33.46, 4.0, 1.7, z = 0.3)), tail = TailKind.T,
                finH = 4.2, finRoot = 4.8, finTip = 3.0, finSweep = 40.0, stabSpan = 10.9, stabRoot = 2.7, stabTip = 1.2, stabSweep = 30.0, stabDihedral = 0.0)
        }
        add("C919", "COMAC C919", AIRLINER, "2 × CFM LEAP-1C") {
            jet(L = 38.9, W = 3.96, H = 4.17, nose = 1.75, tailCone = 2.7, span = 35.8, root = 7.2, tipChord = 1.4, sweep = 27.0, dihedral = 5.0,
                wingAt = 0.37, kink = 0.35, tip = Tip.Winglet(2.4, cant = 10.0, sweep = 50.0, blend = 0.45, taper = 0.3),
                engines = listOf(WingEng(listOf(0.34), 5.0, 2.45, overhang = 0.7, drop = 0.1)),
                finH = 6.0, finRoot = 6.0, finTip = 2.2, finSweep = 40.0, stabSpan = 12.7, stabRoot = 3.8, stabTip = 1.3, stabSweep = 32.0)
        }
        add("T154", "Tupolev Tu-154", AIRLINER, "3 × Soloviev D-30KU") {
            jet(L = 47.9, W = 3.8, H = 3.8, nose = 2.1, tailCone = 3.0, blunt = 0.65, span = 37.55, root = 8.4, tipChord = 1.9, sweep = 37.0,
                dihedral = -1.0, wingAt = 0.4, kink = 0.3,
                engines = listOf(RearEng((47.9 - 11.0) / 47.9, 5.5, 1.5, z = 0.2, style = JET), TailEng((47.9 - 12.0) / 47.9, 5.5, 1.4, sDuct = true)),
                tail = TailKind.T, finH = 5.6, finRoot = 7.0, finTip = 4.0, finSweep = 48.0, stabSpan = 13.4, stabRoot = 3.8, stabTip = 1.6, stabSweep = 40.0, stabDihedral = 0.0)
        }
        add("T204", "Tupolev Tu-204", AIRLINER, "2 × Aviadvigatel PS-90A") {
            jet(L = 46.1, W = 3.8, H = 4.1, nose = 1.9, tailCone = 2.7, span = 41.8, root = 8.5, tipChord = 1.8, sweep = 30.0,
                wingAt = 0.38, kink = 0.33, tip = Tip.Winglet(1.9, cant = 15.0, blend = 0.0),
                engines = listOf(WingEng(listOf(0.33), 5.3, 2.2, overhang = 0.55, drop = 0.2)),
                finH = 7.2, finRoot = 6.6, finTip = 2.4, finSweep = 40.0, stabSpan = 15.0, stabRoot = 4.2, stabTip = 1.4, stabSweep = 32.0)
        }
        add("IL62", "Ilyushin Il-62", QUAD, "4 × Soloviev D-30KU") {
            jet(L = 53.12, W = 3.75, H = 3.75, nose = 2.2, tailCone = 3.3, blunt = 0.65, span = 43.2, root = 9.0, tipChord = 2.4, sweep = 35.0,
                dihedral = -1.0, wingAt = 0.38, kink = 0.3,
                engines = listOf(RearEng((53.12 - 11.5) / 53.12, 5.5, 1.5, z = 0.2, style = JET, pairs = 2)),
                tail = TailKind.T, finH = 5.6, finRoot = 7.5, finTip = 4.2, finSweep = 45.0, stabSpan = 12.2, stabRoot = 3.8, stabTip = 1.6, stabSweep = 38.0, stabDihedral = 0.0)
        }
        add("IL76", "Ilyushin Il-76", MILITARY, "4 × Soloviev D-30KP") { il76() }
        add("IL96", "Ilyushin Il-96", QUAD, "4 × Aviadvigatel PS-90A") {
            jet(L = 55.35, W = 6.08, H = 6.08, nose = 1.6, tailCone = 2.6, span = 60.11, root = 13.0, tipChord = 2.5, sweep = 32.0,
                wingAt = 0.38, kink = 0.33, tip = Tip.Winglet(2.5, cant = 15.0, blend = 0.0),
                engines = listOf(WingEng(listOf(0.35, 0.62), 5.6, 2.3, overhang = 0.55, drop = 0.25)),
                finH = 8.5, finRoot = 9.0, finTip = 3.0, finSweep = 42.0, stabSpan = 20.5, stabRoot = 5.8, stabTip = 1.8, stabSweep = 35.0)
        }
        add("A124", "Antonov An-124 Ruslan", MILITARY, "4 × Progress D-18T") { an124() }
        add("A148 A158", "Antonov An-148/158", REGIONAL, "2 × Progress D-436-148") {
            jet(L = 30.8, W = 3.35, H = 3.35, nose = 1.6, tailCone = 2.8, span = 28.91, root = 5.1, tipChord = 1.6, sweep = 25.0, dihedral = -2.0,
                wingAt = 0.38, wingZ = 0.9, kink = 0.0, tip = Tip.Winglet(1.2, cant = 10.0, blend = 0.1),
                engines = listOf(WingEng(listOf(0.3), 4.0, 1.8, overhang = 0.55, drop = 0.35)), tail = TailKind.T,
                finH = 4.8, finRoot = 5.0, finTip = 3.2, finSweep = 40.0, stabSpan = 11.2, stabRoot = 2.9, stabTip = 1.3, stabSweep = 30.0, stabDihedral = 0.0,
                fairing = false)
        }
        add("AN24 AN26", "Antonov An-24/26", TURBOPROP, "2 × Ivchenko AI-24") {
            jet(L = 23.8, W = 2.9, H = 2.9, nose = 1.4, tailCone = 3.2, blunt = 0.55, tailZ = 0.75, span = 29.2, root = 3.5, tipChord = 1.3,
                sweep = 7.0, dihedral = 0.0, wingAt = 0.37, wingZ = 0.95, kink = 0.0, tc = 0.15,
                engines = listOf(WingEng(listOf(0.27), 5.2, 1.3, PROP, overhang = 0.45, prop = Prop(3.9, 4))),
                finH = 4.2, finRoot = 4.6, finTip = 1.8, finSweep = 35.0, dorsal = true, stabSpan = 9.97, stabRoot = 2.4, stabTip = 1.1, stabSweep = 10.0,
                fairing = false, windowPitch = 0.75)
        }
        add("AN2", "Antonov An-2", LIGHT, "1 × Shvetsov ASh-62IR") {
            light(12.4, 1.8, 2.3, highWing = false, span = 14.24, root = 2.45, wingAt = 0.2, strut = false, prop = Prop(3.6, 4, piston = true),
                finH = 2.4, finRoot = 2.6, stabSpan = 7.2, stabRoot = 1.8, gear = Gear.TAILDRAGGER, biplane = 18.18, box = 2.6)
        }

        // ---- Regional turboprops ------------------------------------------------------------------
        add("AT43 AT44 AT45", "ATR 42-300/500", TURBOPROP, "2 × PW PW120/127") { atr(22.67, 24.57, 4) }
        add("AT46", "ATR 42-600", TURBOPROP, "2 × PW PW127M") { atr(22.67, 24.57, 6) }
        add("AT72 AT73", "ATR 72-200", TURBOPROP, "2 × PW PW124B") { atr(27.17, 27.05, 4) }
        add("AT75 AT76", "ATR 72-500/600", TURBOPROP, "2 × PW PW127") { atr(27.17, 27.05, 6) }
        add("SF34", "Saab 340", TURBOPROP, "2 × GE CT7-9B") { saab(19.73, 21.44, 4, 3.35) }
        add("SB20", "Saab 2000", TURBOPROP, "2 × RR AE 2100A") { saab(27.28, 24.76, 6, 3.81) }
        add("JS32", "BAe Jetstream 32", TURBOPROP, "2 × Honeywell TPE331-12") {
            twinProp(14.37, 1.98, 15.85, 2.2, 4, 2.69, TailKind.Cross(0.3), finH = 2.8, stabSpan = 6.6)
        }
        add("JS41", "BAe Jetstream 41", TURBOPROP, "2 × Honeywell TPE331-14") {
            twinProp(19.25, 1.98, 18.29, 2.4, 5, 2.9, TailKind.Cross(0.3), finH = 3.1, stabSpan = 7.2)
        }
        add("E120", "Embraer EMB 120 Brasilia", TURBOPROP, "2 × PW PW118") {
            twinProp(20.07, 2.28, 19.78, 2.6, 4, 3.2, TailKind.T, finH = 3.4, stabSpan = 6.9)
        }
        add("B190", "Beechcraft 1900D", TURBOPROP, "2 × PW PT6A-67D") {
            twinProp(17.63, 1.7, 17.67, 2.2, 4, 2.78, TailKind.T, finH = 2.9, stabSpan = 5.6, tip = Tip.Winglet(0.6, cant = 20.0, blend = 0.0))
        }
        add("D328", "Dornier 328", TURBOPROP, "2 × PW PW119") {
            jet(L = 21.11, W = 2.4, H = 2.5, nose = 1.8, tailCone = 3.2, blunt = 0.65, tailZ = 0.7, span = 20.98, root = 2.4, tipChord = 1.3,
                sweep = 3.0, dihedral = 2.0, wingAt = 0.38, wingZ = 0.95, kink = 0.0, tc = 0.15,
                engines = listOf(WingEng(listOf(0.3), 4.5, 1.0, PROP, overhang = 0.45, prop = Prop(3.6, 6))), tail = TailKind.T,
                finH = 3.4, finRoot = 3.8, finTip = 2.4, finSweep = 35.0, stabSpan = 6.4, stabRoot = 1.8, stabTip = 1.0, stabSweep = 12.0, stabDihedral = 0.0,
                fairing = false, windowPitch = 0.75)
        }
        add("F50", "Fokker 50", TURBOPROP, "2 × PW PW125B") {
            jet(L = 25.25, W = 2.7, H = 2.7, nose = 1.6, tailCone = 3.2, blunt = 0.6, tailZ = 0.7, span = 29.0, root = 3.5, tipChord = 1.3,
                sweep = 4.0, dihedral = 2.5, wingAt = 0.37, wingZ = 0.95, kink = 0.0, tc = 0.16,
                engines = listOf(WingEng(listOf(0.3), 5.0, 1.15, PROP, overhang = 0.45, prop = Prop(3.66, 6))), tail = TailKind.Cross(0.15),
                finH = 4.6, finRoot = 4.4, finTip = 1.8, finSweep = 35.0, stabSpan = 9.75, stabRoot = 2.2, stabTip = 1.1, stabSweep = 8.0, stabDihedral = 0.0,
                fairing = false, windowPitch = 0.75)
        }
        add("L410", "Let L-410 Turbolet", TURBOPROP, "2 × GE H80") {
            light(14.42, 1.95, 1.9, highWing = true, span = 19.98, root = 2.0, strut = false, prop = Prop(2.3, 5), twinAt = 0.3,
                nacelleLen = 3.0, finH = 2.6, finRoot = 2.4, stabSpan = 6.3, stabRoot = 1.5, gear = Gear.NONE, box = 2.8)
        }
        add("C27J", "Alenia C-27J Spartan", MILITARY, "2 × RR AE 2100-D2A") {
            jet(L = 22.7, W = 3.3, H = 3.3, box = 2.3, nose = 1.3, tailCone = 2.9, noseTip = -0.2, tailZ = 0.85, span = 28.7, root = 3.0,
                tipChord = 1.6, sweep = 3.0, dihedral = 2.0, wingAt = 0.36, wingZ = 0.95, kink = 0.0, tc = 0.16,
                engines = listOf(WingEng(listOf(0.27), 5.4, 1.3, PROP, overhang = 0.45, prop = Prop(4.15, 6))), tail = TailKind.T,
                finH = 4.5, finRoot = 5.0, finTip = 3.0, finSweep = 30.0, stabSpan = 10.0, stabRoot = 2.5, stabTip = 1.3, stabSweep = 10.0, stabDihedral = 0.0,
                fairing = false, windows = false)
        }
        add("C295 CN35", "Airbus C295 / CN-235", MILITARY, "2 × PW PW127G") {
            jet(L = 24.45, W = 3.0, H = 3.0, box = 2.3, nose = 1.4, tailCone = 3.0, noseTip = -0.2, tailZ = 0.8, span = 25.81, root = 3.0,
                tipChord = 1.3, sweep = 3.0, dihedral = 2.0, wingAt = 0.36, wingZ = 0.95, kink = 0.0, tc = 0.16,
                engines = listOf(WingEng(listOf(0.27), 4.8, 1.2, PROP, overhang = 0.45, prop = Prop(3.9, 6))),
                finH = 4.4, finRoot = 4.8, finTip = 2.0, finSweep = 35.0, dorsal = true, stabSpan = 10.0, stabRoot = 2.4, stabTip = 1.2, stabSweep = 10.0,
                fairing = false, windows = false)
        }

        // ---- Business jets ------------------------------------------------------------------------
        add("GLF4", "Gulfstream IV", BIZJET, "2 × RR Tay 611") { gulfstream(26.92, 2.39, 23.72, 5.3, 4.2, 1.5, 1.5) }
        add("GLF5", "Gulfstream V / G550", BIZJET, "2 × RR BR710") { gulfstream(29.39, 2.39, 28.5, 5.8, 4.6, 1.6, 1.6) }
        add("GLF6", "Gulfstream G650", BIZJET, "2 × RR BR725") { gulfstream(30.41, 2.64, 30.36, 6.3, 4.8, 1.7, 1.9) }
        add("GA5C", "Gulfstream G500", BIZJET, "2 × PW814GA") { gulfstream(27.23, 2.64, 26.34, 5.8, 4.3, 1.5, 1.7) }
        add("GA6C", "Gulfstream G600", BIZJET, "2 × PW815GA") { gulfstream(29.41, 2.64, 28.96, 6.0, 4.5, 1.55, 1.8) }
        add("GA7C", "Gulfstream G700", BIZJET, "2 × RR Pearl 700") { gulfstream(33.48, 2.64, 31.39, 6.5, 5.1, 1.8, 2.0) }
        add("GA8C", "Gulfstream G800", BIZJET, "2 × RR Pearl 700") { gulfstream(30.41, 2.64, 31.39, 6.5, 5.1, 1.8, 2.0) }
        add("G280", "Gulfstream G280", BIZJET, "2 × Honeywell HTF7250G") {
            biz(20.37, 2.3, 19.2, 4.3, 1.2, 30.0, RearEng((20.37 - 6.8) / 20.37, 3.2, 1.25), tip = BIZ_WINGLET, finH = 3.1, stabSpan = 7.6)
        }
        add("C510", "Cessna Citation Mustang", BIZJET, "2 × PW615F") { citationLow(12.37, 1.6, 13.16, 2.3, 2.1, 0.85) }
        add("C525", "Cessna CitationJet CJ1", BIZJET, "2 × Williams FJ44-1") { citationLow(12.98, 1.6, 14.26, 2.4, 2.3, 0.9) }
        add("C25A", "Cessna Citation CJ2", BIZJET, "2 × Williams FJ44-2C") { citationLow(14.53, 1.6, 15.1, 2.4, 2.4, 0.95) }
        add("C25B", "Cessna Citation CJ3", BIZJET, "2 × Williams FJ44-3A") { citationLow(15.59, 1.6, 16.26, 2.5, 2.5, 0.95) }
        add("C25C", "Cessna Citation CJ4", BIZJET, "2 × Williams FJ44-4A") { citationLow(16.26, 1.6, 15.49, 2.6, 2.6, 1.0, sweep = 12.5) }
        add("C550", "Cessna Citation II / Bravo", BIZJET, "2 × PW JT15D / PW530A") { citationLow(14.39, 1.7, 15.9, 2.5, 2.5, 0.95) }
        add("C560", "Cessna Citation V / Ultra / Encore", BIZJET, "2 × PW JT15D-5 / PW535A") { citationLow(14.9, 1.7, 16.48, 2.6, 2.6, 1.0) }
        add("C56X", "Cessna Citation Excel / XLS", BIZJET, "2 × PW545") {
            biz(15.79, 1.85, 17.17, 2.9, 1.2, 3.0, RearEng((15.79 - 5.2) / 15.79, 2.9, 1.1), tail = TailKind.Cross(0.25), finH = 2.8, stabSpan = 6.0)
        }
        add("C650", "Cessna Citation III / VI / VII", BIZJET, "2 × Honeywell TFE731") {
            biz(16.9, 1.85, 16.31, 3.2, 1.1, 25.0, RearEng((16.9 - 5.6) / 16.9, 3.0, 1.1), finH = 2.9, stabSpan = 5.9)
        }
        add("C680", "Cessna Citation Sovereign", BIZJET, "2 × PW306C") {
            biz(19.35, 1.85, 19.24, 3.3, 1.3, 16.0, RearEng((19.35 - 6.3) / 19.35, 3.2, 1.25), tail = TailKind.Cross(0.25), finH = 3.2, stabSpan = 7.4)
        }
        add("C68A", "Cessna Citation Latitude", BIZJET, "2 × PW306D1") {
            biz(18.97, 2.1, 22.05, 3.4, 1.3, 16.0, RearEng((18.97 - 6.3) / 18.97, 3.2, 1.25), finH = 3.2, stabSpan = 7.6)
        }
        add("C700", "Cessna Citation Longitude", BIZJET, "2 × Honeywell HTF7700L") {
            biz(22.3, 2.1, 20.9, 4.0, 1.2, 28.0, RearEng((22.3 - 7.4) / 22.3, 3.4, 1.35), tip = BIZ_WINGLET, finH = 3.4, stabSpan = 8.2)
        }
        add("C750", "Cessna Citation X", BIZJET, "2 × RR AE 3007C") {
            biz(22.04, 1.85, 19.48, 4.2, 1.1, 37.0, RearEng((22.04 - 7.3) / 22.04, 3.9, 1.35), finH = 3.4, stabSpan = 7.3)
        }
        add("FA50", "Dassault Falcon 50", BIZJET, "3 × Honeywell TFE731-3") { falcon(18.52, 2.2, 18.86, 4.0, trijet = true, tip = Tip.None) }
        add("F900", "Dassault Falcon 900", BIZJET, "3 × Honeywell TFE731-60") { falcon(20.21, 2.5, 19.33, 4.2, trijet = true, tip = Tip.None) }
        add("F2TH", "Dassault Falcon 2000", BIZJET, "2 × PW PW308C / CFE738") { falcon(20.23, 2.5, 21.38, 4.2, trijet = false, tip = BIZ_WINGLET) }
        add("FA6X", "Dassault Falcon 6X", BIZJET, "2 × PW PW812D") { falcon(25.68, 2.9, 25.9, 5.0, trijet = false, tip = BIZ_WINGLET) }
        add("FA7X", "Dassault Falcon 7X", BIZJET, "3 × PW PW307A") { falcon(23.19, 2.5, 26.21, 4.6, trijet = true, tip = BIZ_WINGLET) }
        add("FA8X", "Dassault Falcon 8X", BIZJET, "3 × PW PW307D") { falcon(24.46, 2.5, 26.29, 4.6, trijet = true, tip = BIZ_WINGLET) }
        add("H25B", "Hawker 800", BIZJET, "2 × Honeywell TFE731-5") {
            biz(15.6, 1.95, 15.66, 3.0, 1.2, 20.0, RearEng((15.6 - 5.2) / 15.6, 3.0, 1.05), tail = TailKind.Cross(0.2), finH = 2.8, stabSpan = 6.0)
        }
        add("BE40", "Beechjet 400 / Hawker 400", BIZJET, "2 × PW JT15D-5") {
            biz(14.75, 1.8, 13.26, 2.6, 1.0, 20.0, RearEng((14.75 - 4.8) / 14.75, 2.7, 0.95), finH = 2.6, stabSpan = 5.0)
        }
        add("PRM1", "Beechcraft Premier I", BIZJET, "2 × Williams FJ44-2A") {
            biz(14.02, 1.8, 13.56, 2.7, 1.0, 20.0, RearEng((14.02 - 4.8) / 14.02, 2.5, 0.95), finH = 2.6, stabSpan = 5.0)
        }
        add("HDJT", "Honda HA-420 HondaJet", BIZJET, "2 × GE Honda HF120") {
            jet(L = 12.99, W = 1.6, H = 1.65, nose = 2.6, tailCone = 3.2, blunt = 0.85, tailZ = 0.45, span = 12.12, root = 2.2, tipChord = 0.9,
                sweep = 5.0, dihedral = 4.0, wingAt = 0.42, wingZ = -0.65, kink = 0.0, tc = 0.13, tip = Tip.Winglet(0.5, cant = 20.0, blend = 0.3),
                engines = listOf(WingEng(listOf(0.36), 2.4, 0.85, overhang = 0.2, drop = 0.35, above = true)), tail = TailKind.T,
                finH = 2.2, finRoot = 2.2, finTip = 1.1, finSweep = 45.0, stabSpan = 5.0, stabRoot = 1.2, stabTip = 0.6, stabSweep = 25.0, stabDihedral = 0.0,
                windowPitch = 0.9, windowSize = 0.4)
        }
        add("PC24", "Pilatus PC-24", BIZJET, "2 × Williams FJ44-4A") {
            biz(16.85, 1.9, 17.0, 2.8, 1.2, 10.0, RearEng((16.85 - 5.2) / 16.85, 2.6, 1.0), finH = 2.9, stabSpan = 6.4)
        }
        add("SF50", "Cirrus SF50 Vision Jet", BIZJET, "1 × Williams FJ33-5A") {
            jet(L = 9.42, W = 1.55, H = 1.55, nose = 1.6, tailCone = 3.0, blunt = 0.6, tailZ = 0.3, span = 11.79, root = 1.9, tipChord = 0.9,
                sweep = 3.0, dihedral = 5.0, wingAt = 0.33, wingZ = -0.65, kink = 0.0, engines = listOf(TopEng(0.58, 2.4, 0.75)),
                tail = TailKind.V(38.0), finH = 0.0, finRoot = 1.2, finTip = 0.7, finSweep = 30.0,
                stabSpan = 4.2, stabRoot = 1.2, stabTip = 0.7, stabSweep = 20.0, windowPitch = 1.0, windowSize = 0.45)
        }
        add("P180", "Piaggio P.180 Avanti", BIZJET, "2 × PW PT6A-66B") {
            jet(L = 14.41, W = 1.95, H = 1.95, nose = 2.2, tailCone = 2.8, blunt = 0.85, tailZ = 0.4, span = 14.03, root = 1.8, tipChord = 0.8,
                sweep = 1.0, dihedral = 1.0, wingAt = 0.52, wingZ = 0.35, kink = 0.0, tc = 0.13,
                engines = listOf(WingEng(listOf(0.30), 3.4, 0.8, PROP, overhang = 0.25, prop = Prop(2.16, 5, pusher = true))),
                tail = TailKind.T, finH = 2.6, finRoot = 2.5, finTip = 1.3, finSweep = 45.0, stabSpan = 4.25, stabRoot = 1.1, stabTip = 0.6, stabSweep = 25.0,
                stabDihedral = 0.0, fairing = false, windowPitch = 0.9, windowSize = 0.4,
                extra = { body, _ -> WingPlan(1.6, 2.0, 0.75, 0.45, 5.0, 0.0, body.at(2.3).zc + 0.25, x0 = 0.55, tc = 0.1).parts() },
                extraFeatures = listOf("FORWARD CANARD"))
        }

        // ---- Turboprop singles & twins ------------------------------------------------------------
        add("PC12", "Pilatus PC-12", TURBOPROP, "1 × PW PT6A-67P") {
            light(14.4, 1.6, 1.7, highWing = false, span = 16.28, root = 2.4, tipChord = 1.3, dihedral = 5.0, wingAt = 0.36,
                prop = Prop(2.67, 5), tail = TailKind.T, finH = 2.6, finRoot = 2.5, finTip = 1.4, finSweep = 38.0, stabSpan = 5.2, stabRoot = 1.5,
                tip = Tip.Winglet(0.8, cant = 20.0, blend = 0.1), gear = Gear.NONE)
        }
        add("TBM7 TBM8", "Daher TBM 700/850", TURBOPROP, "1 × PW PT6A-64/66D") { tbm(Prop(2.31, 4)) }
        add("TBM9", "Daher TBM 900 series", TURBOPROP, "1 × PW PT6A-66D") { tbm(Prop(2.31, 5)) }
        add("C208", "Cessna 208 Caravan", TURBOPROP, "1 × PW PT6A-114A") {
            light(12.67, 1.7, 1.9, highWing = true, span = 15.88, root = 1.98, tipChord = 1.25, prop = Prop(2.69, 3), finH = 2.3, finRoot = 2.2,
                stabSpan = 6.25, stabRoot = 1.5, box = 2.6)
        }
        add("KODI", "Daher Kodiak 100", TURBOPROP, "1 × PW PT6A-34") {
            light(10.41, 1.5, 1.8, highWing = true, span = 13.72, root = 1.7, prop = Prop(2.44, 4), finH = 2.0, finRoot = 2.0, stabSpan = 5.3, stabRoot = 1.3, box = 2.6)
        }
        add("P46T", "Piper M500 / M600", TURBOPROP, "1 × PW PT6A-42A") {
            light(9.02, 1.3, 1.4, highWing = false, span = 13.11, root = 1.8, tipChord = 1.1, prop = Prop(2.03, 5), finH = 1.7, finRoot = 1.6,
                stabSpan = 4.5, stabRoot = 1.0, gear = Gear.NONE)
        }
        add("BE9L", "Beechcraft King Air 90", TURBOPROP, "2 × PW PT6A-135A") {
            twinProp(10.82, 1.55, 15.32, 2.2, 4, 2.36, TailKind.Low, finH = 2.3, stabSpan = 5.3, nose = 2.4)
        }
        add("BE20", "Beechcraft King Air 200/250", TURBOPROP, "2 × PW PT6A-42/52") {
            twinProp(13.36, 1.6, 16.61, 2.6, 4, 2.5, TailKind.T, finH = 2.4, stabSpan = 5.6, nose = 2.6)
        }
        add("B350", "Beechcraft King Air 350", TURBOPROP, "2 × PW PT6A-60A") {
            twinProp(14.22, 1.6, 17.65, 2.6, 4, 2.67, TailKind.T, finH = 2.5, stabSpan = 5.6, nose = 2.6, tip = Tip.Winglet(0.7, cant = 15.0, blend = 0.1))
        }

        // ---- Light aircraft -----------------------------------------------------------------------
        add("C150", "Cessna 150", LIGHT, "1 × Continental O-200") { c172(7.28, 10.17, Prop(1.75, 2, piston = true)) }
        add("C152", "Cessna 152", LIGHT, "1 × Lycoming O-235") { c172(7.34, 10.11, Prop(1.75, 2, piston = true)) }
        add("C172", "Cessna 172 Skyhawk", LIGHT, "1 × Lycoming IO-360") { c172(8.28, 11.0, Prop(1.93, 2, piston = true)) }
        add("C182", "Cessna 182 Skylane", LIGHT, "1 × Lycoming IO-540") { c172(8.84, 11.0, Prop(2.08, 3, piston = true)) }
        add("C206", "Cessna 206 Stationair", LIGHT, "1 × Lycoming IO-540") { c172(8.61, 10.97, Prop(2.13, 3, piston = true), W = 1.2) }
        add("C177", "Cessna 177 Cardinal", LIGHT, "1 × Lycoming O-360") { c172(8.44, 10.82, Prop(1.93, 2, piston = true), strut = false) }
        add("C210", "Cessna 210 Centurion", LIGHT, "1 × Continental IO-520") {
            c172(8.59, 11.2, Prop(2.03, 3, piston = true), strut = false, gear = Gear.NONE)
        }
        add("P28A", "Piper PA-28 Cherokee / Warrior", LIGHT, "1 × Lycoming O-320") { pa28(7.25, 10.67, Prop(1.88, 2, piston = true)) }
        add("P28B", "Piper PA-28 Dakota", LIGHT, "1 × Lycoming O-540") { pa28(7.54, 10.67, Prop(2.03, 2, piston = true)) }
        add("P28R", "Piper PA-28R Arrow", LIGHT, "1 × Lycoming IO-360") { pa28(7.52, 10.8, Prop(1.93, 2, piston = true), gear = Gear.NONE) }
        add("PA32", "Piper PA-32 Saratoga", LIGHT, "1 × Lycoming IO-540") { pa28(8.44, 11.02, Prop(2.03, 3, piston = true), W = 1.25) }
        add("PA46", "Piper PA-46 Malibu", LIGHT, "1 × Lycoming TIO-540") { pa28(8.81, 13.11, Prop(2.03, 3, piston = true), W = 1.3, gear = Gear.NONE) }
        add("PA18", "Piper PA-18 Super Cub", LIGHT, "1 × Lycoming O-320") {
            light(6.88, 0.8, 1.3, highWing = true, span = 10.73, root = 1.6, prop = Prop(1.88, 2, piston = true), finH = 1.3, finRoot = 1.2,
                stabSpan = 3.2, stabRoot = 0.9, gear = Gear.TAILDRAGGER)
        }
        add("PA34", "Piper PA-34 Seneca", LIGHT, "2 × Continental TSIO-360") {
            light(8.72, 1.2, 1.4, highWing = false, span = 11.86, root = 1.6, tipChord = 1.2, prop = Prop(1.93, 3, piston = true), twinAt = 0.32,
                nacelleLen = 2.4, finH = 1.6, finRoot = 1.5, stabSpan = 4.1, stabRoot = 0.9, gear = Gear.NONE)
        }
        add("PA44", "Piper PA-44 Seminole", LIGHT, "2 × Lycoming O-360") {
            light(8.41, 1.2, 1.35, highWing = false, span = 11.77, root = 1.6, prop = Prop(1.88, 2, piston = true), twinAt = 0.32, nacelleLen = 2.3,
                tail = TailKind.T, finH = 1.6, finRoot = 1.5, stabSpan = 3.9, stabRoot = 0.9, gear = Gear.NONE)
        }
        add("SR20", "Cirrus SR20", LIGHT, "1 × Lycoming IO-390") { cirrus(Prop(1.93, 3, piston = true)) }
        add("SR22 S22T", "Cirrus SR22", LIGHT, "1 × Continental IO-550-N") { cirrus(Prop(1.98, 3, piston = true)) }
        add("DA20", "Diamond DA20 Katana", LIGHT, "1 × Continental IO-240") {
            light(7.16, 1.0, 1.2, highWing = false, span = 10.87, root = 1.3, tipChord = 0.7, prop = Prop(1.75, 2, piston = true),
                tail = TailKind.T, finH = 1.3, finRoot = 1.1, stabSpan = 2.9, stabRoot = 0.7)
        }
        add("DA40", "Diamond DA40 Star", LIGHT, "1 × Lycoming IO-360 / Austro AE300") {
            light(8.06, 1.15, 1.3, highWing = false, span = 11.94, root = 1.35, tipChord = 0.75, prop = Prop(1.88, 3, piston = true),
                tail = TailKind.T, finH = 1.5, finRoot = 1.2, stabSpan = 3.2, stabRoot = 0.75)
        }
        add("DA42", "Diamond DA42 Twin Star", LIGHT, "2 × Austro AE300") {
            light(8.56, 1.2, 1.3, highWing = false, span = 13.42, root = 1.4, tipChord = 0.75, prop = Prop(1.9, 3, piston = true), twinAt = 0.3,
                nacelleLen = 2.2, tail = TailKind.T, finH = 1.6, finRoot = 1.3, stabSpan = 3.4, stabRoot = 0.8, gear = Gear.NONE,
                tip = Tip.Winglet(0.5, cant = 30.0, blend = 0.0))
        }
        add("DA62", "Diamond DA62", LIGHT, "2 × Austro AE330") {
            light(9.19, 1.25, 1.35, highWing = false, span = 14.55, root = 1.45, tipChord = 0.75, prop = Prop(1.9, 3, piston = true), twinAt = 0.3,
                nacelleLen = 2.4, tail = TailKind.T, finH = 1.7, finRoot = 1.4, stabSpan = 3.6, stabRoot = 0.85, gear = Gear.NONE,
                tip = Tip.Winglet(0.5, cant = 30.0, blend = 0.0))
        }
        add("BE36", "Beechcraft Bonanza A36", LIGHT, "1 × Continental IO-550") { pa28(8.38, 10.21, Prop(2.03, 3, piston = true), W = 1.2, gear = Gear.NONE) }
        add("BE35", "Beechcraft Bonanza V35", LIGHT, "1 × Continental IO-520") {
            light(8.05, 1.2, 1.35, highWing = false, span = 10.2, root = 2.1, tipChord = 1.2, prop = Prop(2.03, 3, piston = true),
                tail = TailKind.V(33.0), finH = 0.0, finRoot = 1.2, stabSpan = 3.8, stabRoot = 1.2, gear = Gear.NONE)
        }
        add("BE58", "Beechcraft Baron 58", LIGHT, "2 × Continental IO-550") {
            light(9.09, 1.25, 1.4, highWing = false, span = 11.53, root = 2.1, tipChord = 1.2, prop = Prop(1.98, 3, piston = true), twinAt = 0.32,
                nacelleLen = 2.5, finH = 1.8, finRoot = 1.6, stabSpan = 4.9, stabRoot = 1.0, gear = Gear.NONE)
        }
        add("M20P M20T", "Mooney M20", LIGHT, "1 × Lycoming IO-360 / TIO-540") {
            light(7.52, 1.1, 1.25, highWing = false, span = 11.0, root = 1.6, tipChord = 0.9, prop = Prop(1.88, 3, piston = true),
                finH = 1.3, finRoot = 1.0, finTip = 0.8, finSweep = -8.0, stabSpan = 3.6, stabRoot = 0.9, gear = Gear.NONE)
        }
        add("DR40", "Robin DR400", LIGHT, "1 × Lycoming O-360") { pa28(6.96, 8.72, Prop(1.8, 2, piston = true), root = 1.7) }
        add("C42", "Ikarus C42", LIGHT, "1 × Rotax 912") { c172(6.25, 9.45, Prop(1.7, 3, piston = true)) }
        add("E300", "Extra 300", LIGHT, "1 × Lycoming AEIO-580") {
            light(6.95, 0.9, 1.2, highWing = false, span = 8.0, root = 1.9, tipChord = 1.2, dihedral = 0.0, strut = false,
                prop = Prop(1.98, 3, piston = true), finH = 1.5, finRoot = 1.3, stabSpan = 3.2, stabRoot = 1.0, gear = Gear.TAILDRAGGER)
        }
        add("PTS2", "Pitts S-2 Special", LIGHT, "1 × Lycoming AEIO-540") {
            light(5.71, 0.8, 1.1, highWing = false, span = 6.1, root = 1.1, dihedral = 0.0, strut = false, prop = Prop(1.9, 2, piston = true),
                finH = 1.1, finRoot = 1.0, stabSpan = 2.6, stabRoot = 0.8, gear = Gear.TAILDRAGGER, biplane = 6.1)
        }
        add("DH82", "de Havilland Tiger Moth", LIGHT, "1 × de Havilland Gipsy Major") {
            light(7.29, 0.8, 1.2, highWing = false, span = 8.94, root = 1.3, dihedral = 3.0, strut = false, prop = Prop(1.98, 2, piston = true),
                finH = 1.2, finRoot = 1.0, stabSpan = 3.0, stabRoot = 0.9, gear = Gear.TAILDRAGGER, biplane = 8.94)
        }
        add("GLID", "Glider", GLIDER, null) { glider() }
        add("BALL", "Hot-air balloon", BALLOON, null) { balloon() }

        // ---- Helicopters --------------------------------------------------------------------------
        add("R22", "Robinson R22", HELI, "1 × Lycoming O-360") { heli(6.3, 1.1, 1.25, 7.67, 2, HeliStyle.POD, HeliTail.Rotor(1.07, 2)) }
        add("R44", "Robinson R44", HELI, "1 × Lycoming O-540") { heli(9.07, 1.28, 1.35, 10.06, 2, HeliStyle.POD, HeliTail.Rotor(1.47, 2)) }
        add("R66", "Robinson R66", HELI, "1 × RR RR300") { heli(9.1, 1.3, 1.4, 10.06, 2, HeliStyle.POD, HeliTail.Rotor(1.52, 2)) }
        add("B06", "Bell 206 JetRanger", HELI, "1 × RR 250-C20") { heli(9.5, 1.3, 1.5, 10.16, 2, HeliStyle.POD, HeliTail.Rotor(1.65, 2)) }
        add("B407", "Bell 407", HELI, "1 × RR 250-C47B") { heli(10.6, 1.4, 1.6, 10.67, 4, HeliStyle.POD, HeliTail.Rotor(1.65, 2)) }
        add("B429", "Bell 429", HELI, "2 × PW PW207D1") { heli(11.2, 1.5, 1.7, 10.97, 4, HeliStyle.UTILITY, HeliTail.Rotor(1.73, 4)) }
        add("B505", "Bell 505 Jet Ranger X", HELI, "1 × Safran Arrius 2R") { heli(9.6, 1.3, 1.4, 11.28, 2, HeliStyle.POD, HeliTail.Rotor(1.7, 2)) }
        add("B412", "Bell 412", HELI, "2 × PW PT6T-3") { heli(12.9, 1.9, 1.9, 14.02, 4, HeliStyle.UTILITY, HeliTail.Rotor(2.6, 2)) }
        add("UH1", "Bell UH-1 Iroquois", HELI, "1 × Lycoming T53") { heli(12.8, 1.9, 1.9, 14.63, 2, HeliStyle.UTILITY, HeliTail.Rotor(2.59, 2)) }
        add("AS50", "Airbus H125 / AS350 Écureuil", HELI, "1 × Safran Arriel 2D") { heli(10.93, 1.8, 1.8, 10.69, 3, HeliStyle.POD, HeliTail.Rotor(1.86, 2)) }
        add("AS55", "Airbus AS355 Écureuil 2", HELI, "2 × RR 250-C20F") { heli(10.93, 1.8, 1.8, 10.69, 3, HeliStyle.POD, HeliTail.Rotor(1.86, 2)) }
        add("EC20", "Airbus EC120 Colibri", HELI, "1 × Safran Arrius 2F") { heli(9.6, 1.5, 1.6, 10.0, 3, HeliStyle.POD, HeliTail.Fenestron(0.75, 8)) }
        add("EC30", "Airbus H130", HELI, "1 × Safran Arriel 2D") { heli(10.68, 1.9, 1.8, 10.69, 3, HeliStyle.POD, HeliTail.Fenestron(0.75)) }
        add("EC35", "Airbus H135", HELI, "2 × Safran Arrius 2B2 / PW206B3") { heli(10.2, 1.56, 1.6, 10.2, 4, HeliStyle.POD, HeliTail.Fenestron(1.0)) }
        add("EC45", "Airbus H145", HELI, "2 × Safran Arriel 2E") { heli(11.64, 1.7, 1.8, 11.0, 4, HeliStyle.UTILITY, HeliTail.Fenestron(1.2)) }
        add("BK17", "MBB/Kawasaki BK 117", HELI, "2 × Lycoming LTS101 / Arriel 1E2") { heli(9.98, 1.6, 1.8, 11.0, 4, HeliStyle.UTILITY, HeliTail.Rotor(1.96, 2)) }
        add("EC55", "Airbus H155", HELI, "2 × Safran Arriel 2C2") {
            heli(12.73, 2.0, 2.0, 12.6, 5, HeliStyle.UTILITY, HeliTail.Fenestron(1.1), gear = Gear.NONE)
        }
        add("AS65", "Airbus AS365 Dauphin", HELI, "2 × Safran Arriel 2C") {
            heli(11.63, 2.0, 2.0, 11.94, 4, HeliStyle.UTILITY, HeliTail.Fenestron(1.1, 11), gear = Gear.NONE)
        }
        add("H160", "Airbus H160", HELI, "2 × Safran Arrano 1A") {
            heli(14.6, 2.2, 2.2, 13.4, 5, HeliStyle.UTILITY, HeliTail.Fenestron(1.25), gear = Gear.NONE)
        }
        add("EC75", "Airbus H175", HELI, "2 × PW PT6C-67E") { heli(15.7, 2.4, 2.3, 14.8, 5, HeliStyle.UTILITY, HeliTail.Rotor(3.2, 3), gear = Gear.NONE) }
        add("AS32", "Airbus AS332 Super Puma", HELI, "2 × Safran Makila 1A") { heli(15.5, 2.9, 2.6, 15.6, 4, HeliStyle.UTILITY, HeliTail.Rotor(3.05, 5), gear = Gear.WHEELS) }
        add("EC25", "Airbus H225", HELI, "2 × Safran Makila 2A") { heli(16.8, 2.9, 2.7, 16.2, 5, HeliStyle.UTILITY, HeliTail.Rotor(3.2, 4), gear = Gear.WHEELS) }
        add("NH90", "NHIndustries NH90", HELI, "2 × RR RTM322 / GE T700") { heli(16.1, 2.9, 2.6, 16.3, 4, HeliStyle.UTILITY, HeliTail.Rotor(3.2, 4), gear = Gear.WHEELS) }
        add("A109", "Leonardo AW109", HELI, "2 × PW PW206C") { heli(11.45, 1.6, 1.6, 11.0, 4, HeliStyle.UTILITY, HeliTail.Rotor(2.0, 2), gear = Gear.NONE) }
        add("A119", "Leonardo AW119 Koala", HELI, "1 × PW PT6B-37A") { heli(11.14, 1.8, 1.7, 10.83, 4, HeliStyle.UTILITY, HeliTail.Rotor(1.94, 2)) }
        add("A139", "Leonardo AW139", HELI, "2 × PW PT6C-67C") { heli(13.77, 2.26, 2.1, 13.8, 5, HeliStyle.UTILITY, HeliTail.Rotor(2.7, 4), gear = Gear.WHEELS) }
        add("A169", "Leonardo AW169", HELI, "2 × PW PW210A") { heli(12.3, 2.1, 2.0, 12.12, 5, HeliStyle.UTILITY, HeliTail.Rotor(2.3, 4), gear = Gear.WHEELS) }
        add("A189", "Leonardo AW189", HELI, "2 × GE CT7-2E1") { heli(14.6, 2.4, 2.3, 14.6, 5, HeliStyle.UTILITY, HeliTail.Rotor(2.9, 4), gear = Gear.WHEELS) }
        add("EH10", "Leonardo AW101 Merlin", HELI, "3 × RR RTM322 / GE CT7") { heli(19.53, 2.9, 3.0, 18.59, 5, HeliStyle.UTILITY, HeliTail.Rotor(4.0, 4), gear = Gear.WHEELS) }
        add("S76", "Sikorsky S-76", HELI, "2 × Turbomeca Arriel 2S2") { heli(13.2, 2.1, 2.0, 13.41, 4, HeliStyle.UTILITY, HeliTail.Rotor(2.44, 4), gear = Gear.NONE) }
        add("S92", "Sikorsky S-92", HELI, "2 × GE CT7-8A") { heli(17.1, 2.9, 2.6, 17.17, 4, HeliStyle.UTILITY, HeliTail.Rotor(3.35, 4), gear = Gear.WHEELS) }
        add("H60", "Sikorsky UH-60 Black Hawk", HELI, "2 × GE T700") { heli(15.26, 2.36, 2.4, 16.36, 4, HeliStyle.UTILITY, HeliTail.Rotor(3.35, 4), gear = Gear.WHEELS) }
        add("H64", "Boeing AH-64 Apache", HELI, "2 × GE T700-701") {
            heli(15.06, 1.0, 2.0, 14.63, 4, HeliStyle.ATTACK, HeliTail.Rotor(2.79, 4), gear = Gear.WHEELS, stubWing = 5.2)
        }
        add("H47", "Boeing CH-47 Chinook", HELI, "2 × Honeywell T55") {
            heli(15.9, 3.8, 3.8, 18.29, 3, HeliStyle.TRANSPORT, HeliTail.None, gear = Gear.WHEELS, tandem = true)
        }
        add("MI8", "Mil Mi-8/17", HELI, "2 × Klimov TV3-117") { heli(18.2, 2.5, 2.6, 21.29, 5, HeliStyle.UTILITY, HeliTail.Rotor(3.91, 3), gear = Gear.WHEELS) }
        add("MI24", "Mil Mi-24 Hind", HELI, "2 × Klimov TV3-117") {
            heli(17.5, 1.7, 2.4, 17.3, 5, HeliStyle.ATTACK, HeliTail.Rotor(3.91, 3), gear = Gear.WHEELS, stubWing = 6.6)
        }
        add("KA32", "Kamov Ka-32", HELI, "2 × Klimov TV3-117VMA") {
            heli(11.3, 2.2, 2.3, 15.9, 3, HeliStyle.UTILITY, HeliTail.None, gear = Gear.WHEELS, coaxial = true, twinFins = true)
        }

        // ---- Fast jets & trainers -----------------------------------------------------------------
        add("F16", "General Dynamics F-16 Fighting Falcon", FIGHTER, "1 × GE F110 / PW F100") { f16() }
        add("F15", "McDonnell Douglas F-15 Eagle", FIGHTER, "2 × PW F100") {
            fighter(19.43, 13.05, 3.4, 2.2, 6.7, 1.3, 45.0, tail = TailKind.Twin(0.7, 0.0), finH = 3.1, finRoot = 3.2, finTip = 1.4, finSweep = 36.0,
                stabSpan = 8.61, stabRoot = 2.7, stabTip = 1.0, stabSweep = 50.0, engines = 2)
        }
        add("F18", "Boeing F/A-18 Hornet", FIGHTER, "2 × GE F404") {
            fighter(17.07, 12.31, 2.8, 2.3, 5.0, 1.6, 26.0, tail = TailKind.Twin(0.45, 20.0), finH = 3.0, finRoot = 3.0, finTip = 1.4, finSweep = 38.0,
                stabSpan = 6.6, stabRoot = 2.4, stabTip = 1.0, stabSweep = 42.0, engines = 2)
        }
        add("F35", "Lockheed Martin F-35 Lightning II", FIGHTER, "1 × PW F135") {
            fighter(15.7, 10.7, 3.0, 2.3, 6.0, 1.4, 35.0, tail = TailKind.Twin(0.55, 25.0), finH = 2.4, finRoot = 2.8, finTip = 1.1, finSweep = 40.0,
                stabSpan = 6.9, stabRoot = 2.4, stabTip = 1.0, stabSweep = 38.0, engines = 1)
        }
        add("F22", "Lockheed Martin F-22 Raptor", FIGHTER, "2 × PW F119") {
            fighter(18.92, 13.56, 3.6, 2.3, 8.0, 1.3, 42.0, tail = TailKind.Twin(0.6, 28.0), finH = 2.8, finRoot = 3.4, finTip = 1.4, finSweep = 23.0,
                stabSpan = 8.8, stabRoot = 3.2, stabTip = 1.2, stabSweep = 42.0, engines = 2)
        }
        add("EUFI", "Eurofighter Typhoon", FIGHTER, "2 × Eurojet EJ200") {
            fighter(15.96, 10.95, 2.4, 2.0, 8.0, 1.2, 53.0, wingAt = 0.46, finH = 3.2, finRoot = 4.0, finTip = 1.2, finSweep = 50.0, canard = 4.6, engines = 2)
        }
        add("RFAL", "Dassault Rafale", FIGHTER, "2 × Safran M88") {
            fighter(15.27, 10.9, 2.4, 2.0, 7.5, 1.0, 48.0, wingAt = 0.44, finH = 3.0, finRoot = 3.8, finTip = 1.0, finSweep = 50.0, canard = 5.0, engines = 2)
        }
        add("TOR", "Panavia Tornado", FIGHTER, "2 × Turbo-Union RB199") {
            fighter(16.72, 11.0, 2.6, 2.2, 4.0, 1.2, 45.0, wingZ = 0.5, finH = 3.6, finRoot = 4.5, finTip = 1.4, finSweep = 50.0,
                stabSpan = 6.8, stabRoot = 2.8, stabTip = 1.0, stabSweep = 45.0, engines = 2)
        }
        add("HAWK", "BAE Systems Hawk", FIGHTER, "1 × RR Adour") {
            fighter(11.98, 9.39, 1.6, 1.6, 2.9, 1.1, 26.0, wingAt = 0.45, wingZ = -0.7, dihedral = 2.0, finH = 2.5, finRoot = 2.4, finTip = 0.9,
                stabSpan = 4.4, stabRoot = 1.5, stabTip = 0.7, stabSweep = 30.0, engines = 1)
        }
        add("T38", "Northrop T-38 Talon", FIGHTER, "2 × GE J85") {
            fighter(14.14, 7.7, 1.6, 1.5, 3.0, 0.9, 32.0, wingZ = -0.5, finH = 2.4, finRoot = 2.6, finTip = 0.9, stabSpan = 4.3, stabRoot = 1.5,
                stabTip = 0.6, engines = 2)
        }
        add("A10", "Fairchild A-10 Thunderbolt II", FIGHTER, "2 × GE TF34") {
            fighter(16.26, 17.53, 1.9, 2.0, 3.5, 1.8, 3.0, wingAt = 0.42, wingZ = -0.6, dihedral = 5.0, tail = TailKind.H, finH = 2.9, finRoot = 2.4,
                finTip = 1.4, finSweep = 15.0, stabSpan = 5.7, stabRoot = 1.8, stabTip = 1.6, stabSweep = 5.0, engines = 2, podEngines = true)
        }
        add("L39", "Aero L-39 Albatros", FIGHTER, "1 × Ivchenko AI-25TL") {
            fighter(12.13, 9.46, 1.6, 1.6, 2.8, 1.3, 5.0, wingZ = -0.7, finH = 2.2, finRoot = 2.2, finTip = 0.9, stabSpan = 4.4, stabRoot = 1.3,
                stabTip = 0.7, stabSweep = 15.0, tip = Tip.TipTank(2.2, 0.55), engines = 1)
        }
        add("M346", "Leonardo M-346 Master", FIGHTER, "2 × Honeywell F124") {
            fighter(11.49, 9.72, 2.0, 1.7, 3.8, 1.0, 35.0, finH = 2.3, finRoot = 2.4, finTip = 0.9, stabSpan = 4.9, stabRoot = 1.6, stabTip = 0.7, engines = 2)
        }
        add("PC21", "Pilatus PC-21", LIGHT, "1 × PW PT6A-68B") { trainer(11.23, 9.11, Prop(2.39, 5)) }
        add("TEX2", "Beechcraft T-6 Texan II", LIGHT, "1 × PW PT6A-68") { trainer(10.16, 10.19, Prop(2.44, 4)) }
        add("PC9", "Pilatus PC-9", LIGHT, "1 × PW PT6A-62") { trainer(10.14, 10.19, Prop(2.44, 4)) }
        add("PC7", "Pilatus PC-7", LIGHT, "1 × PW PT6A-25A") { trainer(9.78, 10.4, Prop(2.36, 3)) }
    }
}

// ---- Engines ------------------------------------------------------------------------------------

private val CFM56_5A = WingEng(listOf(0.34), 4.4, 2.1, overhang = 0.62, drop = 0.18)
private val LEAP_1A = WingEng(listOf(0.34), 4.9, 2.45, overhang = 0.7, drop = 0.1)
private val PW1500G = WingEng(listOf(0.33), 4.4, 2.3, overhang = 0.65, drop = 0.1)
private val JT8D_737 = WingEng(listOf(0.27), 4.4, 1.3, JET, overhang = 0.6, drop = -0.1)
private val CFM56_3 = WingEng(listOf(0.28), 4.0, 2.0, overhang = 0.72, drop = 0.0, flat = true)
private val CFM56_7 = WingEng(listOf(0.28), 4.3, 2.05, overhang = 0.75, drop = 0.0, flat = true)
private val LEAP_1B = WingEng(listOf(0.28), 4.9, 2.2, overhang = 0.85, drop = -0.05, chevrons = true)
private val RB211_535 = WingEng(listOf(0.30), 5.5, 2.5, overhang = 0.6, drop = 0.2)
private val CF6_767 = WingEng(listOf(0.33), 7.0, 2.9, overhang = 0.55, drop = 0.18)
private val GE90 = WingEng(listOf(0.31), 7.3, 3.8, overhang = 0.55, drop = 0.15)
private val GE90_115 = WingEng(listOf(0.31), 7.6, 4.1, overhang = 0.55, drop = 0.12)
private val GE9X = WingEng(listOf(0.31), 8.0, 4.4, overhang = 0.6, drop = 0.1)
private val GENX_1B = WingEng(listOf(0.32), 6.5, 3.4, overhang = 0.55, drop = 0.12, chevrons = true)
private val JT9D = WingEng(listOf(0.40, 0.70), 6.6, 2.8, overhang = 0.5, drop = 0.3)
private val CF6_747 = WingEng(listOf(0.40, 0.70), 6.8, 2.9, overhang = 0.5, drop = 0.3)
private val GENX_2B = WingEng(listOf(0.40, 0.70), 6.9, 3.2, overhang = 0.5, drop = 0.25, chevrons = true)
private val JT3D = WingEng(listOf(0.39, 0.70), 4.6, 1.5, JET, overhang = 0.6, drop = 0.45)
private val CF6_A300 = WingEng(listOf(0.36), 6.8, 2.9, overhang = 0.55, drop = 0.2)
private val TRENT700 = WingEng(listOf(0.32), 6.6, 3.1, overhang = 0.55, drop = 0.18)
private val TRENT7000 = WingEng(listOf(0.32), 7.2, 3.6, overhang = 0.6, drop = 0.1)
private val CFM56_5C = WingEng(listOf(0.33, 0.63), 5.0, 2.3, overhang = 0.6, drop = 0.25)
private val TRENT500 = WingEng(listOf(0.34, 0.64), 6.2, 2.9, overhang = 0.55, drop = 0.2)
private val XWB84 = WingEng(listOf(0.32), 7.0, 3.55, overhang = 0.58, drop = 0.1)
private val XWB97 = WingEng(listOf(0.32), 7.1, 3.65, overhang = 0.58, drop = 0.1)
private val CF34_8E = WingEng(listOf(0.32), 3.6, 1.6, overhang = 0.55, drop = 0.2)
private val CF34_10E = WingEng(listOf(0.32), 4.0, 1.85, overhang = 0.55, drop = 0.18)
private val PW1900G = WingEng(listOf(0.32), 4.4, 2.25, overhang = 0.65, drop = 0.1)
private val BR715 = RearEng(0.0, 4.6, 1.8, z = 0.35)
private val JT8D_DC9 = RearEng(0.0, 5.5, 1.3, z = 0.35, style = JET)
private val JT8D_200 = RearEng(0.0, 6.1, 1.5, z = 0.35, style = JET)
private val V2500_MD90 = RearEng(0.0, 5.2, 1.9, z = 0.35)
private val BR710_GLOBAL = RearEng(0.0, 4.6, 1.6, z = 0.25)
private val PASSPORT = RearEng(0.0, 4.9, 1.75, z = 0.25)

// ---- Wingtips -----------------------------------------------------------------------------------

private val SHARKLET = Tip.Winglet(2.43, cant = 8.0, sweep = 52.0, blend = 0.45, taper = 0.3, label = "SHARKLETS")
private val A330_WINGLET = Tip.Winglet(2.74, cant = 12.0, sweep = 50.0, blend = 0.15, taper = 0.35)
private val A330NEO_SHARKLET = Tip.Winglet(3.7, cant = 8.0, sweep = 55.0, blend = 0.45, taper = 0.3, label = "SHARKLETS")
private val NG_WINGLET = Tip.Winglet(2.5, cant = 8.0, sweep = 45.0, blend = 0.35, label = "BLENDED WINGLETS")
private val MAX_WINGLET = Tip.Winglet(2.2, cant = 10.0, sweep = 50.0, blend = 0.3, lower = 1.1, label = "AT SPLIT WINGLETS")
private val E1_WINGLET = Tip.Winglet(1.4, cant = 12.0, sweep = 45.0, blend = 0.25)
private val BIZ_WINGLET = Tip.Winglet(1.2, cant = 18.0, sweep = 50.0, blend = 0.3)

// ---- Families -----------------------------------------------------------------------------------

internal fun a320(L: Double, neo: Boolean) = jet(
    L = L, W = 3.95, H = 4.14, nose = 1.7, tailCone = 2.65, noseTip = -0.28, tailZ = 0.5,
    span = if (neo) 35.8 else 34.1, root = 7.0, tipChord = 1.5, sweep = 27.0, dihedral = 5.1,
    wingAt = (13.5 + (L - 37.57) * 0.6) / L, kink = 0.36, tip = if (neo) SHARKLET else Tip.Fence(1.44),
    engines = listOf(if (neo) LEAP_1A else CFM56_5A),
    finH = 5.9, finRoot = 6.0, finTip = 2.1, finSweep = 40.0,
    stabSpan = 12.45, stabRoot = 3.8, stabTip = 1.3, stabSweep = 32.0, stabDihedral = 6.0,
)

private fun a220(L: Double) = jet(
    L = L, W = 3.7, H = 3.9, nose = 1.75, tailCone = 2.7, noseTip = -0.25,
    span = 35.1, root = 6.6, tipChord = 1.3, sweep = 27.0, wingAt = (13.2 + (L - 35.0) * 0.6) / L, kink = 0.34,
    engines = listOf(PW1500G), finH = 6.0, finRoot = 5.6, finTip = 2.4, finSweep = 40.0,
    stabSpan = 11.6, stabRoot = 3.5, stabTip = 1.2, stabSweep = 31.0,
)

private fun a300(L: Double, span: Double, wingLE: Double, tip: Tip, lobe: Hump? = null) = jet(
    L = L, W = 5.64, H = 5.64, nose = 1.6, tailCone = 2.6, noseTip = if (lobe != null) -0.5 else -0.3, hump = lobe,
    windows = lobe == null, span = span, root = 11.0, tipChord = 2.5, sweep = 30.0, dihedral = 5.5, wingAt = wingLE / L, kink = 0.33,
    tip = tip, engines = listOf(CF6_A300), finH = 8.5, finRoot = 8.0, finTip = 2.6, finSweep = 40.0,
    stabSpan = 16.3, stabRoot = 4.8, stabTip = 1.6, stabSweep = 34.0, stabDihedral = 6.0,
)

private fun a330(L: Double, span: Double, tip: Tip, eng: WingEng, lobe: Hump? = null) = jet(
    L = L, W = 5.64, H = 5.64, nose = 1.6, tailCone = 2.7, noseTip = if (lobe != null) -0.5 else -0.3, tailZ = 0.5, hump = lobe,
    windows = lobe == null, span = span, root = 12.8, tipChord = 2.2, sweep = 32.0, dihedral = 5.5,
    wingAt = (22.0 + (L - 63.67) * 0.55) / L, kink = 0.33, tip = tip, engines = listOf(eng),
    finH = 8.3, finRoot = 8.6, finTip = 3.0, finSweep = 43.0,
    stabSpan = 19.4, stabRoot = 5.8, stabTip = 1.8, stabSweep = 32.0, stabDihedral = 6.0,
)

private fun a350(L: Double, eng: WingEng) = jet(
    L = L, W = 5.96, H = 6.09, nose = 1.6, tailCone = 2.7, noseTip = -0.2, blunt = 0.55, tailZ = 0.5, tailR = 0.07,
    span = 64.75, root = 14.5, tipChord = 2.0, sweep = 34.0, dihedral = 6.0, wingAt = (24.0 + (L - 66.8) * 0.55) / L, kink = 0.33,
    tip = Tip.Winglet(3.4, cant = 20.0, sweep = 55.0, blend = 0.65, taper = 0.3, label = "CURVED SHARKLETS"),
    engines = listOf(eng), finH = 9.2, finRoot = 9.0, finTip = 3.2, finSweep = 42.0,
    stabSpan = 18.9, stabRoot = 5.9, stabTip = 1.8, stabSweep = 34.0, stabDihedral = 6.0, windowPitch = 0.6, windowSize = 0.32,
)

private fun a380() = jet(
    L = 72.72, W = 7.14, H = 8.41, box = 2.25, nose = 1.2, tailCone = 2.2, noseTip = -0.35, decks = 2,
    span = 79.75, root = 19.0, tipChord = 4.0, sweep = 36.0, dihedral = 5.6, wingAt = 0.36, kink = 0.30, tip = Tip.Fence(3.5),
    engines = listOf(WingEng(listOf(0.36, 0.64), 7.3, 3.4, overhang = 0.5, drop = 0.18)),
    finH = 12.2, finRoot = 13.5, finTip = 4.5, finSweep = 45.0,
    stabSpan = 30.37, stabRoot = 9.0, stabTip = 2.8, stabSweep = 36.0,
)

private fun a400m() = jet(
    L = 45.1, W = 5.64, H = 5.64, box = 2.3, nose = 1.2, tailCone = 2.6, noseTip = -0.2, tailZ = 0.85, windows = false,
    span = 42.4, root = 6.9, tipChord = 2.7, sweep = 15.0, dihedral = -1.0, wingAt = 0.38, wingZ = 0.92, kink = 0.0, tc = 0.15,
    engines = listOf(WingEng(listOf(0.28, 0.58), 7.0, 1.8, PROP, overhang = 0.45, prop = Prop(5.33, 8))), tail = TailKind.T,
    finH = 8.0, finRoot = 8.5, finTip = 4.0, finSweep = 35.0, stabSpan = 19.7, stabRoot = 4.5, stabTip = 2.3, stabSweep = 25.0,
    stabDihedral = 0.0, fairing = false, extra = sponsons(5.64),
)

private fun b737(L: Double, span: Double, tip: Tip, eng: WingEng, max: Boolean = false, root: Double = 7.3, windows: Boolean = true) = jet(
    L = L, W = 3.76, H = 4.01, nose = 2.0, tailCone = if (max) 2.9 else 2.6, noseTip = -0.35, blunt = 0.6, tailZ = 0.5,
    tailR = if (max) 0.07 else 0.12, windows = windows,
    span = span, root = root, tipChord = 1.25, sweep = 28.0, dihedral = 6.0, wingAt = (14.5 + (L - 39.5) * 0.55) / L, kink = 0.31,
    tip = tip, engines = listOf(eng), finH = 7.0, finRoot = 6.2, finTip = 2.1, finSweep = 35.0, dorsal = true,
    stabSpan = 14.35, stabRoot = 3.8, stabTip = 1.2, stabSweep = 30.0, stabDihedral = 7.0,
)

internal fun b757(L: Double) = jet(
    L = L, W = 3.76, H = 4.1, nose = 2.0, tailCone = 2.7, noseTip = -0.3, blunt = 0.55,
    span = 41.1, root = 8.6, tipChord = 1.7, sweep = 27.5, wingAt = (16.8 + (L - 47.32) * 0.55) / L, kink = 0.32,
    tip = Tip.Winglet(2.3, cant = 10.0, sweep = 42.0, blend = 0.35), engines = listOf(RB211_535),
    finH = 7.8, finRoot = 6.8, finTip = 2.4, finSweep = 38.0, dorsal = true,
    stabSpan = 15.2, stabRoot = 4.2, stabTip = 1.4, stabSweep = 32.0, stabDihedral = 7.0,
)

private fun b767(L: Double, span: Double, tip: Tip) = jet(
    L = L, W = 5.03, H = 5.41, nose = 1.75, tailCone = 2.6, noseTip = -0.3,
    span = span, root = 11.5, tipChord = 2.3, sweep = 34.0, dihedral = 6.0, wingAt = (19.5 + (L - 54.94) * 0.55) / L, kink = 0.3,
    tip = tip, engines = listOf(CF6_767), finH = 8.7, finRoot = 8.5, finTip = 2.8, finSweep = 38.0,
    stabSpan = 18.6, stabRoot = 5.2, stabTip = 1.6, stabSweep = 32.0, stabDihedral = 7.0,
)

internal fun b777(L: Double, span: Double, tip: Tip, eng: WingEng) = jet(
    L = L, W = 6.2, H = 6.2, nose = 1.6, tailCone = 2.7, noseTip = -0.25, tailZ = 0.55, tailR = 0.08,
    span = span, root = 15.0, tipChord = 2.6, sweep = 33.5, dihedral = 6.0, wingAt = (23.5 + (L - 63.73) * 0.55) / L, kink = 0.32,
    tip = tip, engines = listOf(eng), finH = 9.3, finRoot = 10.0, finTip = 3.3, finSweep = 40.0,
    stabSpan = 21.5, stabRoot = 6.4, stabTip = 2.2, stabSweep = 33.0, stabDihedral = 6.0,
)

private fun b787(L: Double) = jet(
    L = L, W = 5.77, H = 5.97, nose = 1.6, tailCone = 2.8, noseTip = -0.15, blunt = 0.55, tailZ = 0.5, tailR = 0.07,
    span = 60.12, root = 13.5, tipChord = 1.6, sweep = 35.0, dihedral = 7.0, wingAt = (20.5 + (L - 56.72) * 0.55) / L, kink = 0.33,
    tip = Tip.Raked(4.3), engines = listOf(GENX_1B), finH = 9.5, finRoot = 8.7, finTip = 3.0, finSweep = 42.0,
    stabSpan = 19.8, stabRoot = 5.8, stabTip = 1.6, stabSweep = 36.0, stabDihedral = 7.0, windowPitch = 0.6, windowSize = 0.36,
)

private fun b747(L: Double, span: Double, tip: Tip, deckEnd: Double, eng: WingEng, wingLE: Double = 22.5 + (L - 70.66) * 0.55, lobe: Hump? = null) = jet(
    L = L, W = 6.5, H = 6.5, nose = 1.7, tailCone = 2.6, noseTip = -0.35, windows = lobe == null,
    hump = lobe ?: Hump(start = 2.0, end = deckEnd, h = 1.45, rampIn = 7.5, rampOut = 4.0),
    span = span, root = 16.6, tipChord = 4.0, sweep = 40.0, dihedral = 7.0, wingAt = wingLE / L, kink = 0.33,
    tip = tip, engines = listOf(eng), finH = 10.0, finRoot = 10.7, finTip = 3.9, finSweep = 45.0,
    stabSpan = 22.2, stabRoot = 7.4, stabTip = 2.3, stabSweep = 37.0, stabDihedral = 7.0,
)

private fun b707(L: Double, span: Double, eng: WingEng, windows: Boolean = true, extra: (Body, WingPlan) -> List<Part> = { _, _ -> emptyList() },
                 features: List<String> = emptyList()) = jet(
    L = L, W = 3.76, H = 4.2, nose = 1.9, tailCone = 2.7, noseTip = -0.3, blunt = 0.6, windows = windows,
    span = span, root = 10.0, tipChord = 2.5, sweep = 37.0, dihedral = 7.0, wingAt = 0.36, kink = 0.32,
    engines = listOf(eng), finH = 7.9, finRoot = 7.0, finTip = 2.2, finSweep = 40.0, dorsal = true,
    stabSpan = 13.95, stabRoot = 4.2, stabTip = 1.4, stabSweep = 35.0, stabDihedral = 7.0,
    extra = extra, extraFeatures = features,
)

private fun kc135() = b707(41.53, 39.88, WingEng(listOf(0.39, 0.70), 4.8, 2.1, overhang = 0.6, drop = 0.35), windows = false,
    extra = { body, _ ->
        val s = 41.53 - 4.5
        val z = body.at(s).bottom
        val end = P3(0.0, 41.53 + 6.0, z - 5.5)
        listOf(
            Wire(listOf(P3(0.0, s, z), end)),
            Wire(listOf(end + P3(0.0, -2.0, 1.8), end + P3(1.4, -1.0, 2.6), end + P3(0.0, -2.6, 2.2)), mirror = true),
        )
    }, features = listOf("FLYING REFUELLING BOOM"))

private fun e3() = b707(46.61, 44.42, JT3D, windows = false,
    extra = { body, _ ->
        val s = 46.61 * 0.62
        val top = body.at(s).top
        val dome = top + 3.4
        listOf(
            Drum(P3(0.0, s, dome), P3.UP, 4.57, depth = 1.83, sides = 24, rev = 0.1, spokes = 6),
            Wire(listOf(P3(0.9, s - 1.8, top), P3(0.4, s - 0.6, dome - 0.9), P3(0.9, s + 1.2, top)), mirror = true),
        )
    }, features = listOf("ROTATING RADAR DOME"))

private fun dc9(L: Double, span: Double, eng: RearEng, stabSpan: Double = 12.24) = jet(
    L = L, W = 3.34, H = 3.61, nose = 2.4, tailCone = 3.2, noseTip = -0.3, blunt = 0.7, tailZ = 0.5,
    span = span, root = 6.3, tipChord = 1.2, sweep = 27.0, dihedral = 3.0, wingAt = (17.0 + (L - 45.06) * 0.55) / L, kink = 0.3,
    engines = listOf(eng.copy(at = (L - 11.5) / L)), tail = TailKind.T,
    finH = 4.3, finRoot = 5.8, finTip = 3.3, finSweep = 45.0, stabSpan = stabSpan, stabRoot = 3.2, stabTip = 1.5, stabSweep = 30.0, stabDihedral = 0.0,
)

private fun b727(L: Double) = jet(
    L = L, W = 3.76, H = 4.1, nose = 2.0, tailCone = 3.0, noseTip = -0.35, blunt = 0.6, tailZ = 0.55,
    span = 32.92, root = 8.0, tipChord = 1.7, sweep = 34.0, dihedral = 3.0, wingAt = (16.5 + (L - 46.69) * 0.55) / L, kink = 0.3,
    engines = listOf(RearEng((L - 10.5) / L, 5.0, 1.5, z = 0.2, style = JET), TailEng((L - 11.5) / L, 5.0, 1.4, sDuct = true)),
    tail = TailKind.T, finH = 5.2, finRoot = 6.8, finTip = 4.0, finSweep = 50.0,
    stabSpan = 10.9, stabRoot = 3.4, stabTip = 1.6, stabSweep = 36.0, stabDihedral = 0.0,
)

private fun dc10(L: Double, span: Double, tip: Tip) = jet(
    L = L, W = 6.02, H = 6.02, nose = 1.6, tailCone = 2.5, noseTip = -0.3, tailZ = 0.5,
    span = span, root = 12.0, tipChord = 2.9, sweep = 37.0, dihedral = 5.0, wingAt = (21.5 + (L - 55.5) * 0.55) / L, kink = 0.3, tip = tip,
    engines = listOf(WingEng(listOf(0.33), 7.1, 2.95, overhang = 0.55, drop = 0.25), TailEng((L - 13.0) / L, 7.0, 2.8, sDuct = false)),
    finH = 6.5, finRoot = 7.0, finTip = 3.2, finSweep = 45.0,
    stabSpan = 18.0, stabRoot = 5.6, stabTip = 1.8, stabSweep = 35.0, stabDihedral = 6.0,
)

private fun tristar() = jet(
    L = 54.17, W = 5.97, H = 5.97, nose = 1.6, tailCone = 2.8, noseTip = -0.3, tailZ = 0.5,
    span = 47.35, root = 11.5, tipChord = 2.6, sweep = 37.0, dihedral = 5.5, wingAt = 0.37, kink = 0.3,
    engines = listOf(WingEng(listOf(0.34), 6.0, 2.8, overhang = 0.55, drop = 0.25), TailEng((54.17 - 12.5) / 54.17, 6.0, 2.0, sDuct = true)),
    finH = 8.0, finRoot = 9.0, finTip = 3.4, finSweep = 45.0,
    stabSpan = 19.0, stabRoot = 5.8, stabTip = 1.9, stabSweep = 35.0, stabDihedral = 6.0,
)

private fun sponsons(W: Double): (Body, WingPlan) -> List<Part> = { body, wing ->
    val s = wing.rootLE - 1.5
    listOf(Body(pod(s, wing.root + 4.0, W * 0.2, body.at(s + 2.0).bottom + W * 0.18), x = W / 2 * 0.82, mirror = true, sides = 8))
}

private fun c130(L: Double, blades: Int) = jet(
    L = L, W = 4.3, H = 4.5, box = 2.6, nose = 1.2, tailCone = 3.0, noseTip = -0.2, blunt = 0.45, tailZ = 0.9, tailR = 0.1, windows = false,
    span = 40.41, root = 4.9, tipChord = 2.7, sweep = 4.0, dihedral = 2.5, wingAt = (11.0 + (L - 29.79) * 0.5) / L, wingZ = 0.95, kink = 0.0, tc = 0.16,
    engines = listOf(WingEng(listOf(0.29, 0.56), 6.0, 1.4, PROP, overhang = 0.42, prop = Prop(4.11, blades))),
    finH = 6.2, finRoot = 6.2, finTip = 2.4, finSweep = 25.0, dorsal = true,
    stabSpan = 15.7, stabRoot = 3.8, stabTip = 2.0, stabSweep = 7.0, stabDihedral = 0.0, fairing = false, extra = sponsons(4.3),
)

private fun c17() = jet(
    L = 53.04, W = 6.85, H = 6.85, box = 2.3, nose = 1.3, tailCone = 2.4, noseTip = -0.2, tailZ = 0.8, windows = false,
    span = 51.75, root = 12.0, tipChord = 3.5, sweep = 28.0, dihedral = -3.0, wingAt = 0.37, wingZ = 0.92, kink = 0.3,
    tip = Tip.Winglet(2.9, cant = 25.0, sweep = 45.0, blend = 0.0),
    engines = listOf(WingEng(listOf(0.30, 0.55), 6.5, 2.6, overhang = 0.5, drop = 0.4)), tail = TailKind.T,
    finH = 8.5, finRoot = 9.0, finTip = 5.4, finSweep = 40.0,
    stabSpan = 19.8, stabRoot = 5.3, stabTip = 2.7, stabSweep = 30.0, stabDihedral = -3.0, fairing = false, extra = sponsons(6.85),
)

private fun c5() = jet(
    L = 75.31, W = 7.0, H = 7.0, box = 2.3, nose = 1.3, tailCone = 2.6, noseTip = -0.2, tailZ = 0.8, windows = false,
    span = 67.89, root = 13.7, tipChord = 4.8, sweep = 28.0, dihedral = -5.0, wingAt = 0.38, wingZ = 0.92, kink = 0.0,
    engines = listOf(WingEng(listOf(0.34, 0.61), 7.3, 2.9, overhang = 0.5, drop = 0.4)), tail = TailKind.T,
    finH = 12.0, finRoot = 12.0, finTip = 6.0, finSweep = 40.0,
    stabSpan = 20.6, stabRoot = 6.0, stabTip = 2.8, stabSweep = 30.0, stabDihedral = 0.0, fairing = false, extra = sponsons(7.0),
)

private fun p3() = jet(
    L = 35.61, W = 3.4, H = 3.6, nose = 1.6, tailCone = 3.4, noseTip = -0.2, tailZ = 0.4, tailR = 0.05, windows = false,
    span = 30.37, root = 5.8, tipChord = 2.4, sweep = 5.0, dihedral = 6.0, wingAt = 0.34, wingZ = -0.6, kink = 0.0, tc = 0.15,
    engines = listOf(WingEng(listOf(0.3, 0.62), 5.0, 1.3, PROP, overhang = 0.4, prop = Prop(4.1, 4))),
    finH = 6.0, finRoot = 6.2, finTip = 2.0, finSweep = 30.0, dorsal = true,
    stabSpan = 13.1, stabRoot = 3.4, stabTip = 1.6, stabSweep = 10.0, stabDihedral = 6.0,
)

private fun b52() = jet(
    L = 48.5, W = 3.0, H = 3.6, nose = 1.6, tailCone = 2.5, noseTip = -0.3, windows = false,
    span = 56.39, root = 15.0, tipChord = 4.2, sweep = 37.0, dihedral = -2.0, wingAt = 0.36, wingZ = 0.9, kink = 0.3,
    engines = listOf(WingEng(listOf(0.32, 0.62), 5.0, 1.35, JET, overhang = 0.55, drop = 0.7, twin = true)),
    finH = 8.0, finRoot = 9.0, finTip = 2.6, finSweep = 35.0,
    stabSpan = 15.4, stabRoot = 4.3, stabTip = 1.6, stabSweep = 35.0, fairing = false,
)

private fun v22() = jet(
    L = 17.48, W = 2.5, H = 2.7, box = 2.4, nose = 1.5, tailCone = 2.0, noseTip = -0.2, tailZ = 0.6, windows = false,
    span = 14.0, root = 2.6, tipChord = 2.4, sweep = -6.0, dihedral = 3.0, wingAt = 0.4, wingZ = 0.95, kink = 0.0, tc = 0.2,
    engines = listOf(WingEng(listOf(1.0), 5.0, 1.3, PROP, overhang = 0.3, prop = Prop(11.61, 3))), tail = TailKind.H,
    finH = 3.0, finRoot = 2.4, finTip = 1.6, finSweep = 25.0, stabSpan = 5.6, stabRoot = 1.8, stabTip = 1.5, stabSweep = 5.0, stabDihedral = 0.0,
    fairing = false, extraFeatures = listOf("TILTROTOR"),
).let { Design(it.parts, it.features, it.lengthM, 25.78) }

private fun il76() = jet(
    L = 46.59, W = 4.8, H = 4.8, box = 2.2, nose = 1.3, tailCone = 2.6, noseTip = -0.25, tailZ = 0.8, windows = false,
    span = 50.5, root = 9.5, tipChord = 3.3, sweep = 27.0, dihedral = -3.0, wingAt = 0.38, wingZ = 0.9, kink = 0.3,
    engines = listOf(WingEng(listOf(0.30, 0.56), 5.5, 1.6, JET, overhang = 0.55, drop = 0.4)), tail = TailKind.T,
    finH = 7.5, finRoot = 8.0, finTip = 4.8, finSweep = 40.0,
    stabSpan = 17.4, stabRoot = 4.5, stabTip = 2.2, stabSweep = 33.0, stabDihedral = 0.0, fairing = false, extra = sponsons(4.8),
)

private fun an124() = jet(
    L = 69.1, W = 7.3, H = 7.3, box = 2.3, nose = 1.3, tailCone = 2.4, noseTip = -0.2, tailZ = 0.85, windows = false,
    span = 73.3, root = 15.0, tipChord = 4.0, sweep = 35.0, dihedral = -4.0, wingAt = 0.36, wingZ = 0.88, kink = 0.3,
    engines = listOf(WingEng(listOf(0.34, 0.60), 7.0, 2.9, overhang = 0.5, drop = 0.35)),
    finH = 11.0, finRoot = 11.0, finTip = 4.0, finSweep = 40.0,
    stabSpan = 26.0, stabRoot = 7.0, stabTip = 2.5, stabSweep = 35.0, fairing = false, extra = sponsons(7.3),
)

private fun ejet(L: Double, span: Double, tip: Tip, eng: WingEng, big: Boolean) = jet(
    L = L, W = 3.01, H = 3.35, nose = 1.8, tailCone = 2.7, noseTip = -0.3,
    span = span, root = if (big) 6.2 else 5.4, tipChord = 1.2, sweep = 25.0, wingAt = (12.2 + (L - 36.24) * 0.55) / L, kink = 0.35,
    tip = tip, engines = listOf(eng), finH = if (big) 5.4 else 5.0, finRoot = 4.6, finTip = 2.0, finSweep = 40.0,
    stabSpan = if (big) 12.1 else 10.0, stabRoot = 2.8, stabTip = 1.0, stabSweep = 30.0, windowPitch = 0.51,
)

private fun erj(L: Double, span: Double = 20.04, tip: Tip = Tip.None) = jet(
    L = L, W = 2.28, H = 2.28, nose = 3.0, tailCone = 3.4, noseTip = -0.3, blunt = 0.75, tailZ = 0.5,
    span = span, root = 3.9, tipChord = 1.05, sweep = 23.0, wingAt = (10.5 + (L - 29.87) * 0.5) / L, kink = 0.0,
    tip = tip, engines = listOf(RearEng((L - 8.2) / L, 3.4, 1.3, z = 0.3)), tail = TailKind.T,
    finH = 3.3, finRoot = 3.4, finTip = 2.0, finSweep = 40.0, stabSpan = 7.55, stabRoot = 2.1, stabTip = 1.0, stabSweep = 25.0,
    stabDihedral = 0.0, windowPitch = 0.55,
)

private fun crj(L: Double, span: Double, stabSpan: Double, big: Boolean, biz: Boolean = false) = jet(
    L = L, W = 2.69, H = 2.69, nose = 2.3, tailCone = 3.0, noseTip = -0.3, blunt = 0.65, tailZ = 0.45,
    span = span, root = if (big) 4.6 else 4.0, tipChord = 1.1, sweep = 28.0, dihedral = 3.5,
    wingAt = (if (big) 13.5 + (L - 32.51) * 0.5 else 10.5 + (L - 26.77) * 0.5) / L, kink = 0.3,
    tip = Tip.Winglet(if (big) 1.2 else 0.9, cant = 15.0, sweep = 45.0, blend = 0.0),
    engines = listOf(RearEng((L - 9.0) / L, 3.4, if (big) 1.45 else 1.3, z = 0.3)), tail = TailKind.T,
    finH = if (big) 4.0 else 3.8, finRoot = 4.0, finTip = 2.4, finSweep = 40.0,
    stabSpan = stabSpan, stabRoot = 2.2, stabTip = 1.0, stabSweep = 30.0, stabDihedral = 0.0,
    windowPitch = if (biz) 0.9 else 0.51, windowSize = if (biz) 0.4 else 0.26,
)

private fun fokker(L: Double) = jet(
    L = L, W = 3.3, H = 3.3, nose = 2.1, tailCone = 3.0, noseTip = -0.3, blunt = 0.6,
    span = 28.08, root = 5.9, tipChord = 1.3, sweep = 19.0, dihedral = 2.5, wingAt = (12.0 + (L - 35.53) * 0.5) / L, kink = 0.33,
    engines = listOf(RearEng((L - 9.8) / L, 4.4, 1.55, z = 0.3)), tail = TailKind.T,
    finH = 4.4, finRoot = 4.4, finTip = 2.8, finSweep = 38.0, stabSpan = 10.04, stabRoot = 2.6, stabTip = 1.3, stabSweep = 26.0, stabDihedral = 0.0,
)

private fun bae146(L: Double) = jet(
    L = L, W = 3.56, H = 3.56, nose = 1.7, tailCone = 3.0, noseTip = -0.3, blunt = 0.55, tailZ = 0.5,
    span = 26.21, root = 5.5, tipChord = 1.3, sweep = 17.0, dihedral = -3.0, wingAt = (10.5 + (L - 28.6) * 0.5) / L, wingZ = 0.9, kink = 0.0,
    engines = listOf(WingEng(listOf(0.27, 0.48), 3.0, 1.3, overhang = 0.5, drop = 0.35)), tail = TailKind.T,
    finH = 4.9, finRoot = 4.8, finTip = 2.6, finSweep = 35.0, stabSpan = 11.1, stabRoot = 2.8, stabTip = 1.3, stabSweep = 25.0, stabDihedral = 0.0,
    fairing = false,
)

private fun atr(L: Double, span: Double, blades: Int) = jet(
    L = L, W = 2.87, H = 2.9, nose = 1.6, tailCone = 3.4, noseTip = -0.3, blunt = 0.65, tailZ = 0.75, tailR = 0.1,
    span = span, root = 2.6, tipChord = 1.4, sweep = 3.0, dihedral = 2.0, wingAt = (11.2 + (L - 27.17) * 0.45) / L, wingZ = 0.95, kink = 0.0, tc = 0.16,
    engines = listOf(WingEng(listOf(0.3), 5.6, 1.15, PROP, overhang = 0.45, prop = Prop(3.93, blades))),
    tail = TailKind.T, finH = 3.4, finRoot = 4.2, finTip = 2.4, finSweep = 32.0,
    stabSpan = 7.31, stabRoot = 1.9, stabTip = 1.0, stabSweep = 10.0, stabDihedral = 0.0, fairing = false,
    windowPitch = 0.76, windowSize = 0.3, extra = sponsons(2.87),
)

private fun dash8(L: Double, span: Double, blades: Int, propDia: Double, nacLen: Double) = jet(
    L = L, W = 2.69, H = 2.9, nose = 1.7, tailCone = 3.3, noseTip = -0.3, blunt = 0.6, tailZ = 0.7,
    span = span, root = 3.1, tipChord = 1.5, sweep = 3.0, dihedral = 2.5, wingAt = 0.4, wingZ = 0.95, kink = 0.0, tc = 0.16,
    engines = listOf(WingEng(listOf(0.29), nacLen, 1.4, PROP, overhang = 0.4, prop = Prop(propDia, blades))),
    tail = TailKind.T, finH = 4.6, finRoot = 5.0, finTip = 3.3, finSweep = 30.0,
    stabSpan = 8.0, stabRoot = 2.2, stabTip = 1.5, stabSweep = 10.0, stabDihedral = 0.0, fairing = false, windowPitch = 0.76, windowSize = 0.3,
)

private fun saab(L: Double, span: Double, blades: Int, dia: Double) = jet(
    L = L, W = 2.3, H = 2.5, nose = 1.9, tailCone = 3.0, noseTip = -0.3, blunt = 0.65, tailZ = 0.55,
    span = span, root = 2.9, tipChord = 1.2, sweep = 5.0, dihedral = 7.0, wingAt = 0.36, wingZ = -0.6, kink = 0.0, tc = 0.16,
    engines = listOf(WingEng(listOf(0.30), 4.6, 1.05, PROP, overhang = 0.45, prop = Prop(dia, blades))),
    finH = 3.6, finRoot = 3.5, finTip = 1.8, finSweep = 32.0, dorsal = true,
    stabSpan = 9.24, stabRoot = 2.0, stabTip = 1.0, stabSweep = 10.0, stabDihedral = 7.0, windowPitch = 0.7, windowSize = 0.3,
)

/** Low-wing twin turboprops (King Air, Jetstream, Brasilia, 1900). */
private fun twinProp(
    L: Double, W: Double, span: Double, root: Double, blades: Int, propDia: Double, tail: TailKind,
    finH: Double, stabSpan: Double, nose: Double = 2.0, tip: Tip = Tip.None,
) = jet(
    L = L, W = W, H = W * 1.05, nose = nose, tailCone = 3.0, noseTip = -0.25, blunt = 0.8, tailZ = 0.45,
    span = span, root = root, tipChord = root * 0.5, sweep = 3.0, dihedral = 6.0, wingAt = 0.37, wingZ = -0.55, kink = 0.0, tc = 0.16, tip = tip,
    engines = listOf(WingEng(listOf(0.3), L * 0.28, W * 0.55, PROP, overhang = 0.5, prop = Prop(propDia, blades))),
    tail = tail, finH = finH, finRoot = finH * 1.0, finTip = finH * 0.55, finSweep = 38.0,
    stabSpan = stabSpan, stabRoot = stabSpan * 0.27, stabTip = stabSpan * 0.16, stabSweep = 12.0,
    stabDihedral = if (tail == TailKind.Low) 7.0 else 0.0, fairing = false, windowPitch = 0.9, windowSize = 0.4,
)

/** Rear-engined business jets. */
private fun biz(
    L: Double, W: Double, span: Double, root: Double, tipChord: Double, sweep: Double, eng: RearEng,
    tail: TailKind = TailKind.T, tip: Tip = Tip.None, finH: Double, stabSpan: Double, eng2: Eng? = null,
) = jet(
    L = L, W = W, H = W * 1.02, nose = 2.4, tailCone = 3.2, noseTip = -0.25, blunt = 0.8, tailZ = 0.45,
    span = span, root = root, tipChord = tipChord, sweep = sweep, dihedral = 3.0, wingAt = 0.42, wingZ = -0.65, kink = 0.0, tc = 0.12, tip = tip,
    engines = listOfNotNull(eng, eng2), tail = tail, finH = finH, finRoot = finH * 1.15, finTip = finH * 0.7, finSweep = 45.0,
    stabSpan = stabSpan, stabRoot = stabSpan * 0.3, stabTip = stabSpan * 0.14, stabSweep = 30.0,
    stabDihedral = if (tail == TailKind.T) 0.0 else 4.0, windowPitch = 1.0, windowSize = 0.45,
)

/** Citation Mustang / CJ / II / V: straight wing, tailplane low with strong dihedral. */
private fun citationLow(L: Double, W: Double, span: Double, root: Double, engLen: Double, engDia: Double, sweep: Double = 3.0) = jet(
    L = L, W = W, H = W * 1.02, nose = 2.3, tailCone = 3.2, noseTip = -0.25, blunt = 0.8, tailZ = 0.45,
    span = span, root = root, tipChord = root * 0.45, sweep = sweep, dihedral = 4.0, wingAt = 0.4, wingZ = -0.65, kink = 0.0, tc = 0.14,
    engines = listOf(RearEng((L - L * 0.3) / L, engLen, engDia, z = 0.3)),
    finH = 2.3, finRoot = 2.4, finTip = 1.1, finSweep = 40.0, dorsal = true,
    stabSpan = span * 0.4, stabRoot = 1.2, stabTip = 0.6, stabSweep = 10.0, stabDihedral = 9.0, windowPitch = 0.9, windowSize = 0.4,
)

private fun gulfstream(L: Double, W: Double, span: Double, root: Double, engLen: Double, engDia: Double, winglet: Double) = jet(
    L = L, W = W, H = W * 1.02, nose = 2.3, tailCone = 3.3, noseTip = -0.25, blunt = 0.8, tailZ = 0.45,
    span = span, root = root, tipChord = 1.3, sweep = 33.0, dihedral = 3.0, wingAt = 0.41, wingZ = -0.65, kink = 0.25, tc = 0.11,
    tip = Tip.Winglet(winglet, cant = 12.0, sweep = 50.0, blend = 0.3),
    engines = listOf(RearEng((L - L * 0.3) / L, engLen, engDia, z = 0.25)), tail = TailKind.T,
    finH = 3.6, finRoot = 4.0, finTip = 2.6, finSweep = 45.0,
    stabSpan = span * 0.34, stabRoot = 2.4, stabTip = 1.0, stabSweep = 35.0, stabDihedral = 0.0, windowPitch = 1.05, windowSize = 0.5,
)

private fun global(L: Double, span: Double, eng: RearEng) = jet(
    L = L, W = 2.69, H = 2.75, nose = 2.3, tailCone = 3.3, noseTip = -0.25, blunt = 0.8, tailZ = 0.45,
    span = span, root = 6.0, tipChord = 1.3, sweep = 35.0, dihedral = 3.0, wingAt = 0.41, wingZ = -0.65, kink = 0.25, tc = 0.11,
    tip = Tip.Winglet(1.9, cant = 15.0, sweep = 45.0, blend = 0.3),
    engines = listOf(eng.copy(at = (L - L * 0.3) / L)), tail = TailKind.T,
    finH = 3.8, finRoot = 4.2, finTip = 2.6, finSweep = 45.0,
    stabSpan = span * 0.35, stabRoot = 2.5, stabTip = 1.0, stabSweep = 35.0, stabDihedral = 0.0, windowPitch = 1.0, windowSize = 0.45,
)

private fun cl300(span: Double, canted: Boolean) = biz(
    20.92, 2.36, span, 4.5, 1.2, 29.0, RearEng((20.92 - 6.6) / 20.92, 3.3, 1.3),
    tip = Tip.Winglet(1.2, cant = 20.0, sweep = 45.0, blend = if (canted) 0.35 else 0.1), finH = 3.3, stabSpan = 7.3,
)

private fun falcon(L: Double, W: Double, span: Double, root: Double, trijet: Boolean, tip: Tip) = jet(
    L = L, W = W, H = W * 1.02, nose = 2.4, tailCone = 3.2, noseTip = -0.25, blunt = 0.8, tailZ = 0.45,
    span = span, root = root, tipChord = 1.2, sweep = 32.0, dihedral = 3.0, wingAt = 0.41, wingZ = -0.65, kink = 0.25, tc = 0.11, tip = tip,
    engines = listOfNotNull(
        RearEng((L - L * 0.3) / L, 3.2, 1.25, z = 0.3),
        if (trijet) TailEng((L - L * 0.32) / L, 3.0, 1.1, sDuct = true) else null,
    ),
    tail = TailKind.Cross(0.35), finH = 3.6, finRoot = 4.0, finTip = 2.2, finSweep = 45.0,
    stabSpan = span * 0.33, stabRoot = 2.3, stabTip = 1.0, stabSweep = 32.0, stabDihedral = 3.0, windowPitch = 1.0, windowSize = 0.45,
)

private fun c172(L: Double, span: Double, prop: Prop, W: Double = 1.1, strut: Boolean = true, gear: Gear = Gear.TRICYCLE) = light(
    L = L, W = W, H = 1.45, highWing = true, span = span, root = 1.63, tipChord = 1.13, dihedral = 1.7, wingAt = 0.28, strut = strut,
    prop = prop, finH = 1.5, finRoot = 1.6, finTip = 0.8, finSweep = 35.0, stabSpan = span * 0.31, stabRoot = 1.2, stabTip = 0.8, gear = gear,
)

private fun pa28(L: Double, span: Double, prop: Prop, W: Double = 1.1, gear: Gear = Gear.TRICYCLE, root: Double = 1.6) = light(
    L = L, W = W, H = 1.3, highWing = false, span = span, root = root, tipChord = root * 0.65, dihedral = 7.0, wingAt = 0.3,
    prop = prop, finH = 1.3, finRoot = 1.3, finTip = 0.7, finSweep = 40.0, stabSpan = span * 0.37, stabRoot = 0.8, stabTip = 0.8, gear = gear,
)

private fun cirrus(prop: Prop) = light(
    L = 7.92, W = 1.3, H = 1.35, highWing = false, span = 11.68, root = 1.45, tipChord = 0.8, dihedral = 4.5, wingAt = 0.36,
    prop = prop, finH = 1.5, finRoot = 1.5, finTip = 0.8, finSweep = 30.0, stabSpan = 3.95, stabRoot = 0.9, stabTip = 0.6,
)

private fun tbm(prop: Prop) = light(
    L = 10.74, W = 1.3, H = 1.5, highWing = false, span = 12.83, root = 2.0, tipChord = 1.1, wingAt = 0.36, prop = prop,
    finH = 1.9, finRoot = 1.9, stabSpan = 4.9, stabRoot = 1.2, gear = Gear.NONE,
)

private fun trainer(L: Double, span: Double, prop: Prop) = light(
    L = L, W = 1.1, H = 1.45, highWing = false, span = span, root = 2.1, tipChord = 1.1, dihedral = 6.0, wingAt = 0.38,
    prop = prop, finH = 1.9, finRoot = 1.8, stabSpan = 4.0, stabRoot = 1.3, gear = Gear.NONE,
)

internal fun f16() = fighter(
    15.06, 9.96, 1.9, 1.8, 5.0, 1.0, 40.0, wingAt = 0.45, finH = 3.0, finRoot = 3.6, finTip = 1.2, finSweep = 47.0,
    stabSpan = 5.58, stabRoot = 2.2, stabTip = 0.8, stabSweep = 40.0, engines = 1,
)
