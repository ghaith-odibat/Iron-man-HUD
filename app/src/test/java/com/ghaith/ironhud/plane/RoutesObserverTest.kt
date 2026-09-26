package com.ghaith.ironhud.plane

import com.ghaith.ironhud.ai.Http
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class RoutesObserverTest {
    private val routeset = """[
      {"_airport_codes_iata":"AMM-LHR","_airports":[
        {"alt_feet":2395,"countryiso2":"JO","iata":"AMM","icao":"OJAI","lat":31.7226,"location":"Amman","lon":35.9932,"name":"Queen Alia International Airport"},
        {"alt_feet":83,"countryiso2":"GB","iata":"LHR","icao":"EGLL","lat":51.4706,"location":"London","lon":-0.461941,"name":"London Heathrow Airport"}],
       "airline_code":"RJA","airport_codes":"OJAI-EGLL","callsign":"RJA111","number":"111","plausible":true},
      {"_airport_codes_iata":"unknown","_airports":[],"airport_codes":"unknown","callsign":"XYZ999","plausible":0}]"""

    private val adsbdb = """{"response":{"flightroute":{"callsign":"UAE17","callsign_icao":"UAE17",
      "airline":{"name":"Emirates","icao":"UAE","iata":"EK"},
      "origin":{"country_iso_name":"AE","country_name":"United Arab Emirates","elevation":62,"iata_code":"DXB","icao_code":"OMDB","latitude":25.2528,"longitude":55.3644,"municipality":"Dubai","name":"Dubai International Airport"},
      "destination":{"country_iso_name":"GB","country_name":"United Kingdom","elevation":83,"iata_code":"LHR","icao_code":"EGLL","latitude":51.4706,"longitude":-0.461941,"municipality":"London","name":"London Heathrow Airport"}}}}"""

    @Test fun parsesRoutesetIncludingUnknowns() {
        val m = RouteParsers.parseRouteset(routeset)
        val r = m.getValue("RJA111")!!
        assertEquals("AMM → LHR", r.short)
        assertEquals("Amman", r.origin.city)
        assertEquals(RouteSource.ADSB_LOL, r.source)
        assertTrue(r.plausible)
        assertTrue(m.containsKey("XYZ999"))
        assertNull(m["XYZ999"])
        assertTrue(RouteParsers.parseRouteset("").isEmpty())
    }

    @Test fun parsesAdsbdbAndRejectsUnknown() {
        val r = RouteParsers.parseAdsbdb(adsbdb)!!
        assertEquals("DXB → LHR", r.short)
        assertEquals("GB", r.destination.country)
        assertNull(RouteParsers.parseAdsbdb("""{"response":"unknown callsign"}"""))
    }

    @Test fun progressAlongGreatCircle() {
        val r = RouteParsers.parseRouteset(routeset).getValue("RJA111")!!
        assertEquals(0.0, r.progress(31.7226, 35.9932)!!, 1e-6)
        assertEquals(1.0, r.progress(51.4706, -0.461941)!!, 1e-6)
        val mid = r.progress(41.5, 17.5)!!
        assertTrue("mid-trip, got $mid", mid in 0.4..0.6)
        // Amman–London is about 3,680 km.
        assertEquals(3_680_000.0, Geo.greatCircleM(31.7226, 35.9932, 51.4706, -0.461941), 40_000.0)
    }

    @Test fun routableCallsigns() {
        assertTrue(RouteParsers.isRoutable("RJA111"))
        assertTrue(RouteParsers.isRoutable(" uae17 "))
        assertFalse(RouteParsers.isRoutable("JYRNA"))
        assertFalse(RouteParsers.isRoutable("N123AB"))
        assertFalse(RouteParsers.isRoutable(null))
    }

    @Test fun observerParsingAndViewer() {
        val photon = """{"features":[{"type":"Feature","properties":{"osm_value":"aerodrome","name":"London Heathrow Airport","city":"London","country":"United Kingdom"}}]}"""
        assertEquals("London Heathrow Airport, London", ObserverParsers.parsePhotonReverse(photon))
        val nameless = """{"features":[{"properties":{"city":"Amman","country":"Jordan"}}]}"""
        assertEquals("Amman, Jordan", ObserverParsers.parsePhotonReverse(nameless))
        assertNull(ObserverParsers.parsePhotonReverse("""{"features":[]}"""))
        assertEquals(38.0, ObserverParsers.parseElevation("""{"elevation":[38.0]}""")!!, 1e-9)
        assertEquals("51.4700°N 0.4543°W", ObserverParsers.coordLabel(51.47, -0.4543))

        val gps = GeoPoint(31.95, 35.93, 900.0)
        val heathrow = ObserverPlace("Heathrow", 51.47, -0.45, 25.0)
        assertEquals(gps, effectiveViewer(gps, null))
        assertEquals(26.7, effectiveViewer(gps, heathrow)!!.altM, 1e-9)
        assertNull(effectiveViewer(null, null))
    }

    // ---- network behaviour -------------------------------------------------------------------

    private val server = MockWebServer()
    private val paths = Collections.synchronizedList(mutableListOf<String>())
    @After fun tearDown() = server.shutdown()

    private fun plane(cs: String) = Aircraft(
        "hex$cs", cs, null, null, null, null, "A3", 31.9, 35.9, 10_000.0, 400.0, 90.0, 0.0, null, 0, FeedSource.ADSB_LOL,
    )

    @Test fun routesetThenAdsbdbFallbackWithCaching() = runBlocking {
        var routesetBody = routeset
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                paths += request.method + " " + request.path
                return when {
                    request.path == "/lol/api/0/routeset" -> MockResponse().setBody(routesetBody)
                    request.path == "/db/v0/callsign/UAE17" -> MockResponse().setBody(adsbdb)
                    else -> MockResponse().setResponseCode(404).setBody("""{"response":"unknown callsign"}""")
                }
            }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        var now = 1_000L
        val client = RouteClient(Http.newClient(), { now }, lolBase = "$base/lol", dbBase = "$base/db")

        val routes = client.resolve(listOf(plane("RJA111"), plane("UAE17"), plane("XYZ999"), plane("JYRNA")))
        assertEquals("AMM → LHR", routes["RJA111"]?.short)
        assertEquals("DXB → LHR", routes["UAE17"]?.short)   // not in routeset → adsbdb
        assertNull(routes["XYZ999"])
        // One batch call, one adsbdb call; the known-unknown and the non-airline callsign aren't queried.
        assertEquals(listOf("POST /lol/api/0/routeset", "GET /db/v0/callsign/UAE17"), paths.toList())

        // Everything cached: no more traffic.
        paths.clear()
        client.resolve(listOf(plane("RJA111"), plane("UAE17"), plane("XYZ999")))
        assertTrue(paths.isEmpty())

        // An empty routeset body leaves new callsigns to adsbdb.
        routesetBody = ""
        now += 1_000
        client.resolve(listOf(plane("ABC123")))
        assertEquals(listOf("POST /lol/api/0/routeset", "GET /db/v0/callsign/ABC123"), paths.toList())
    }
}
