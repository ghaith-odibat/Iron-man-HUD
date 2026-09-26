package com.ghaith.ironhud.plane

import com.ghaith.ironhud.plane.models.AircraftTypes
import com.ghaith.ironhud.plane.models.Airframe
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

/**
 * A wireframe in model space: x = right wing, y = nose, z = up, scaled so the larger of length and
 * span is one unit and centred on the airframe. [edges] are the structure, [faint] the fine detail
 * drawn dimmer (propeller discs, windows, hinge lines), and [spins] the vertex runs that turn
 * (propellers, rotors, radar domes).
 */
class WireMesh(
    val vertices: FloatArray,
    val edges: IntArray,
    val faint: IntArray = IntArray(0),
    val spins: List<Spin> = emptyList(),
    /** Metres per model unit (the aircraft's larger dimension). */
    val unitM: Double = 1.0,
) {
    val vertexCount: Int get() = vertices.size / 3
}

/** Vertices [first] until [end] turn [revPerSec] times a second about [axis] through [origin] (model space). */
class Spin(val first: Int, val end: Int, val origin: FloatArray, val axis: FloatArray, val revPerSec: Float)

enum class ModelKind(val label: String) {
    AIRLINER("NARROW-BODY JET"),
    WIDEBODY("WIDE-BODY JET"),
    QUAD("FOUR-ENGINE HEAVY"),
    REGIONAL("REGIONAL JET"),
    TURBOPROP("TURBOPROP"),
    BIZJET("BUSINESS JET"),
    LIGHT("LIGHT AIRCRAFT"),
    HELI("ROTORCRAFT"),
    MILITARY("MILITARY TRANSPORT"),
    FIGHTER("FAST JET"),
    GLIDER("GLIDER"),
    BALLOON("BALLOON"),
    DRONE("UNCREWED AIRCRAFT"),
}

/** How much of a model to draw: everything (close inspection), the AR default, or the far-away outline. */
enum class Lod { DETAIL, NORMAL, LITE }

object AircraftModels {

    fun airframeFor(a: Aircraft): Airframe = AircraftTypes.resolve(a.typeCode, a.category, a.description)

    fun kindFor(a: Aircraft): ModelKind = airframeFor(a).kind

    private val cache = ConcurrentHashMap<String, WireMesh>()

    fun mesh(airframe: Airframe, lod: Lod = Lod.NORMAL): WireMesh =
        cache.getOrPut("${airframe.id}/$lod") { airframe.mesh(lod) }

    /**
     * Model-to-camera transform for a live aircraft: heading [trackDeg] (clockwise from north in the
     * same frame as [center]), climb [pitchDeg], [scale] metres per model unit, placed at [center].
     * Returns origin + model x/y/z axes in camera coordinates (right, up, forward), see [WireProjection].
     */
    fun cameraBasis(trackDeg: Double, pitchDeg: Double, scale: Double, center: Enu, rot: FloatArray, displayRotation: Int): DoubleArray {
        val h = Geo.rad(trackDeg)
        val p = Geo.rad(pitchDeg)
        val sh = sin(h)
        val ch = cos(h)
        val sp = sin(p)
        val cp = cos(p)
        // Model axes in ENU.
        val rx = Enu(ch, -sh, 0.0) * scale
        val fy = Enu(sh * cp, ch * cp, sp) * scale
        val uz = Enu(-sh * sp, -ch * sp, cp) * scale
        val o = Projector.toCamera(center, rot, displayRotation)
        val x = Projector.toCamera(rx, rot, displayRotation)
        val y = Projector.toCamera(fy, rot, displayRotation)
        val z = Projector.toCamera(uz, rot, displayRotation)
        return doubleArrayOf(
            o.right, o.up, o.forward,
            x.right, x.up, x.forward,
            y.right, y.up, y.forward,
            z.right, z.up, z.forward,
        )
    }

    /**
     * A display-case view: the model turned by [yawDeg], seen from [elevationDeg] above, [distance]
     * model units from the camera.
     */
    fun viewBasis(yawDeg: Double, elevationDeg: Double, distance: Double = 4.0): DoubleArray {
        val y = Geo.rad(yawDeg)
        val e = Geo.rad(elevationDeg)
        val cy = cos(y)
        val sy = sin(y)
        val ce = cos(e)
        val se = sin(e)
        return doubleArrayOf(
            0.0, 0.0, distance,
            cy, sy * se, sy * ce,
            -sy, cy * se, cy * ce,
            0.0, ce, -se,
        )
    }
}
