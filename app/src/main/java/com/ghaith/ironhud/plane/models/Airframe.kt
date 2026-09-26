package com.ghaith.ironhud.plane.models

import com.ghaith.ironhud.plane.Lod
import com.ghaith.ironhud.plane.ModelKind
import com.ghaith.ironhud.plane.WireMesh

/**
 * One aircraft type (or a generic stand-in when the type is unknown): its ICAO designators, name,
 * class, engines, and the geometry the hologram is built from.
 */
class Airframe(
    val codes: List<String>,
    val name: String,
    val kind: ModelKind,
    val power: String? = null,
    /** True for stand-ins picked from the ADS-B category rather than the exact type. */
    val generic: Boolean = false,
    private val build: () -> Design,
) {
    val id: String get() = codes.first()
    val design: Design by lazy(build)
    val lengthM: Double get() = design.lengthM
    val spanM: Double get() = design.spanM
    val rotorM: Double? get() = design.rotorM
    val features: List<String> get() = design.features

    fun mesh(lod: Lod): WireMesh = Mesher(lod).build(design.parts)

    override fun toString() = "Airframe($id, $name)"
}
