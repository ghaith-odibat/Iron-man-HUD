package com.ghaith.ironhud.plane

import com.ghaith.ironhud.plane.models.AircraftTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class AircraftTest {
    private fun plane(
        type: String? = "A20N", category: String? = "A3", callsign: String? = "RJA123", operator: String? = null,
        description: String? = null,
    ) = Aircraft(
        hex = "740abc", callsign = callsign, registration = "JY-RNA", typeCode = type, description = description,
        operator = operator, category = category, lat = 31.95, lon = 35.93, altM = 10_000.0, gsKt = 360.0,
        trackDeg = 90.0, vRateFpm = 1_000.0, squawk = "4321", posTimeMs = 0, source = FeedSource.ADSB_LOL,
    )

    @Test fun deadReckoningMovesAlongTrackAndIsCapped() {
        val a = plane()
        val start = GeoPoint(a.lat, a.lon, a.altM)
        val after10 = Geo.enu(start, a.positionAt(10_000))
        assertEquals(1_852.0, after10.e, 5.0)          // 360 kt = 0.1 NM/s
        assertEquals(0.0, after10.n, 5.0)
        assertEquals(50.8, after10.u + after10.horizontal * after10.horizontal / (2 * Geo.EARTH_RADIUS_M), 0.5)
        val after5min = Geo.enu(start, a.positionAt(300_000))
        assertEquals(1_852.0 * 6, after5min.e, 20.0)    // capped at 60 s
        assertEquals(a.lat, a.positionAt(-5_000).lat, 1e-9)
        assertTrue(a.pitchDeg > 0)
    }

    @Test fun modelSelection() {
        assertEquals(ModelKind.QUAD, AircraftModels.kindFor(plane(type = "A388")))
        assertEquals(ModelKind.WIDEBODY, AircraftModels.kindFor(plane(type = "B789")))
        assertEquals(ModelKind.BIZJET, AircraftModels.kindFor(plane(type = "GLF6")))
        assertEquals(ModelKind.HELI, AircraftModels.kindFor(plane(type = null, category = "A7")))
        assertEquals(ModelKind.LIGHT, AircraftModels.kindFor(plane(type = "c172")))
        assertEquals(ModelKind.WIDEBODY, AircraftModels.kindFor(plane(type = null, category = "A5")))
        assertEquals(ModelKind.AIRLINER, AircraftModels.kindFor(plane(type = null, category = null)))
        assertEquals(ModelKind.HELI, AircraftModels.kindFor(plane(type = "ZZZZ", category = "A3", description = "ROBINSON R-44")))

        // Variants of one family resolve to different models.
        val ceo = AircraftModels.airframeFor(plane(type = "A320"))
        val neo = AircraftModels.airframeFor(plane(type = "A20N"))
        assertNotEquals(ceo, neo)
        assertTrue("SHARKLETS" in neo.features)
        assertTrue("WINGTIP FENCES" in ceo.features)
        assertTrue("AT SPLIT WINGLETS" in AircraftModels.airframeFor(plane(type = "B38M")).features)
        assertTrue("RAKED WINGTIPS" in AircraftModels.airframeFor(plane(type = "B77W")).features)
        assertTrue("UPPER DECK" in AircraftModels.airframeFor(plane(type = "B744")).features)
        assertTrue("T-TAIL" in AircraftModels.airframeFor(plane(type = "CRJ9")).features)
        assertTrue("FENESTRON TAIL" in AircraftModels.airframeFor(plane(type = "EC35")).features)
        assertTrue("4 × TURBOFAN" in AircraftModels.airframeFor(plane(type = "A388")).features)
        assertTrue("3 × TURBOFAN" in AircraftModels.airframeFor(plane(type = "MD11")).features)
        assertEquals(64.8, AircraftModels.airframeFor(plane(type = "B77W")).spanM, 1e-6)
        assertFalse(AircraftModels.airframeFor(plane(type = "B738")).generic)
        assertTrue(AircraftModels.airframeFor(plane(type = null, category = "A1")).generic)
    }

    @Test fun everyModelHasItsOwnWindshield() {
        val glazing = listOf("PANE", "WINDSHIELD", "CANOPY", "GREENHOUSE", "COCKPITS", "GLAZED")
        for (a in AircraftTypes.catalog) {
            if (a.kind == ModelKind.BALLOON) continue
            assertTrue("${a.id} has a windshield", a.features.any { f -> glazing.any { it in f } })
        }
        fun f(code: String) = AircraftTypes.byCode(code)!!.features
        assertTrue(f("B733").any { "EYEBROW" in it })              // 737 Classic kept its eyebrow windows…
        assertFalse(f("B738").any { "EYEBROW" in it })             // …the NG and MAX dropped them
        assertTrue(f("MD82").any { "EYEBROW" in it })
        assertFalse(f("B77W").any { "EYEBROW" in it })
        assertTrue("4-PANE CURVED WINDSHIELD · MASK" in f("A359"))
        assertTrue("4-PANE FLIGHT DECK" in f("B788"))
        assertTrue("6-PANE FLIGHT DECK" in f("A320"))
        assertTrue("ONE-PIECE WINDSHIELD" in f("C172"))
        assertTrue("BUBBLE CANOPY" in f("R44"))
        assertTrue("FRAMELESS BUBBLE CANOPY" in f("F16"))
        assertTrue("TANDEM CANOPY" in f("PC21"))
        assertTrue(f("IL76").any { "GLAZED NAVIGATOR NOSE" in it })
    }

    @Test fun windshieldsShowOnLiveModelsButNotFarAway() {
        val a = AircraftTypes.byCode("B789")!!
        val detail = AircraftModels.mesh(a, Lod.DETAIL).accent.size
        val normal = AircraftModels.mesh(a, Lod.NORMAL).accent.size
        assertTrue("full glazing close up", detail > normal)
        assertTrue("essential panes on live models", normal > 0)
        assertEquals("nothing on distant planes", 0, AircraftModels.mesh(a, Lod.LITE).accent.size)
    }

    @Test fun catalogueCodesAreUnique() {
        val codes = AircraftTypes.catalog.flatMap { it.codes }
        assertEquals(codes.groupBy { it }.filterValues { it.size > 1 }.keys.toString(), codes.size, codes.toSet().size)
        assertTrue("catalogue has ${AircraftTypes.catalog.size} types", AircraftTypes.catalog.size >= 250)
    }

    @Test fun meshesAreWellFormed() {
        for (a in AircraftTypes.catalog) {
            val detail = AircraftModels.mesh(a, Lod.DETAIL)
            val normal = AircraftModels.mesh(a, Lod.NORMAL)
            val lite = AircraftModels.mesh(a, Lod.LITE)
            for ((lod, m) in listOf("detail" to detail, "normal" to normal, "lite" to lite)) {
                val tag = "${a.id} $lod"
                assertTrue("$tag has edges", m.edges.size >= 60)
                assertTrue("$tag even edge list", m.edges.size % 2 == 0 && m.faint.size % 2 == 0)
                assertTrue("$tag edge indices in range", (m.edges + m.faint + m.accent).all { it in 0 until m.vertexCount })
                assertTrue("$tag finite", m.vertices.all { it.isFinite() })
                for (i in 0 until m.vertexCount) {
                    assertTrue("$tag x within the unit box", abs(m.vertices[i * 3]) <= 0.5001f)
                    assertTrue("$tag y within the unit box", abs(m.vertices[i * 3 + 1]) <= 0.5001f)
                }
                for (s in m.spins) assertTrue("$tag spin range", s.first in 0..s.end && s.end <= m.vertexCount)
            }
            assertTrue("${a.id} detail ≥ normal ≥ lite", detail.edges.size >= normal.edges.size && normal.edges.size > lite.edges.size)
            assertEquals("${a.id} real size", maxOf(a.lengthM, a.spanM), detail.unitM, maxOf(a.lengthM, a.spanM) * 0.25)
        }
    }

    @Test fun noseLeadsAndRotorsSpin() {
        val m = AircraftModels.mesh(AircraftTypes.byCode("A320")!!, Lod.NORMAL)
        val ys = (0 until m.vertexCount).map { m.vertices[it * 3 + 1] }
        val nose = ys.indices.maxBy { ys[it] }
        assertEquals(0.0f, m.vertices[nose * 3], 0.01f)       // nose on the centreline…
        assertTrue(ys[nose] > 0.45f)                          // …at the front of the unit box.

        val heli = AircraftModels.mesh(AircraftTypes.byCode("EC35")!!, Lod.NORMAL)
        assertTrue(heli.spins.size >= 2)                      // main rotor + fenestron
        val p = WireProjection()
        val basis = AircraftModels.viewBasis(30.0, 20.0)
        p.project(heli, basis, 500.0, 0f, 0f, 0.0)
        val a = p.near.points.copyOf(p.near.size)
        p.project(heli, basis, 500.0, 0f, 0f, 0.1)
        val b = p.near.points.copyOf(p.near.size)
        assertFalse(a.contentEquals(b))
        assertTrue(p.near.size + p.far.size > 400)
    }

    @Test fun cameraBasisFollowsTrackAndPitch() {
        // Device flat, screen up: camera right = east, up = north, forward = down.
        val rot = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        val b = AircraftModels.cameraBasis(trackDeg = 90.0, pitchDeg = 0.0, scale = 40.0, center = Enu(0.0, 0.0, -1000.0), rot = rot, displayRotation = 0)
        assertEquals(1000.0, b[2], 1e-6)                        // origin 1 km in front of the lens
        assertEquals(40.0, b[6], 1e-6)                          // nose (model y) points east = screen right
        assertEquals(-40.0, b[4], 1e-6)                         // right wing points south = screen down
        val climbing = AircraftModels.cameraBasis(0.0, 10.0, 40.0, Enu(0.0, 0.0, -1000.0), rot, 0)
        assertEquals(-40.0 * sin(Geo.rad(10.0)), climbing[8], 1e-6)  // nose rises towards the camera
    }

    @Test fun names() {
        assertEquals("Airbus A320neo", AircraftInfo.typeName(plane()))
        assertEquals("Boeing 737 MAX 8", AircraftInfo.typeName(plane(type = "B38M")))
        assertEquals("Royal Jordanian", AircraftInfo.airline(plane()))
        assertEquals("Royal Jordanian", AircraftInfo.airline(plane(callsign = "JYRNA", operator = "ROYAL JORDANIAN")))
        assertEquals("ROTORCRAFT", AircraftInfo.categoryName("a7"))
        assertEquals("RJA123", plane().label)
        assertEquals("JY-RNA", plane(callsign = " ").label)
        assertNull(AircraftTypes.byCode("?A3"))
    }
}
