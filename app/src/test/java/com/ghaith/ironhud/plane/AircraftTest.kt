package com.ghaith.ironhud.plane

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AircraftTest {
    private fun plane(
        type: String? = "A20N", category: String? = "A3", callsign: String? = "RJA123", operator: String? = null,
    ) = Aircraft(
        hex = "740abc", callsign = callsign, registration = "JY-RNA", typeCode = type, description = null,
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
        assertEquals(ModelKind.LIGHT, AircraftModels.kindFor(plane(type = "C172")))
        assertEquals(ModelKind.WIDEBODY, AircraftModels.kindFor(plane(type = null, category = "A5")))
        assertEquals(ModelKind.AIRLINER, AircraftModels.kindFor(plane(type = null, category = null)))
    }

    @Test fun meshesAreWellFormed() {
        for (kind in ModelKind.entries) {
            val m = AircraftModels.mesh(kind)
            assertTrue("$kind has edges", m.edges.size >= 40)
            assertTrue("$kind even edge list", m.edges.size % 2 == 0)
            assertTrue("$kind edge indices in range", m.edges.all { it in 0 until m.vertexCount })
        }
    }

    @Test fun worldVerticesFollowTrack() {
        val mesh = AircraftModels.mesh(ModelKind.AIRLINER)
        // Vertex 0 is the nose (y = +0.5). Heading east → nose points east.
        val v = AircraftModels.worldVertices(mesh, trackDeg = 90.0, pitchDeg = 0.0, scale = 40.0, center = Enu(0.0, 0.0, 0.0))
        assertEquals(20.0, v[0].e, 1e-3)
        assertEquals(0.0, v[0].n, 1e-3)
        val climbing = AircraftModels.worldVertices(mesh, trackDeg = 0.0, pitchDeg = 10.0, scale = 40.0, center = Enu(0.0, 0.0, 0.0))
        assertTrue(climbing[0].u > 0)
    }

    @Test fun names() {
        assertEquals("Airbus A320neo", AircraftInfo.typeName(plane()))
        assertEquals("Royal Jordanian", AircraftInfo.airline(plane()))
        assertEquals("Royal Jordanian", AircraftInfo.airline(plane(callsign = "JYRNA", operator = "ROYAL JORDANIAN")))
        assertEquals("ROTORCRAFT", AircraftInfo.categoryName("a7"))
        assertEquals("RJA123", plane().label)
        assertEquals("JY-RNA", plane(callsign = " ").label)
    }
}
