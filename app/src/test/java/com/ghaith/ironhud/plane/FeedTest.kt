package com.ghaith.ironhud.plane

import com.ghaith.ironhud.ai.Http
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedTest {
    private val readsb = """{"ac":[
        {"hex":"740ABC","type":"adsb_icao","flight":"RJA123  ","r":"JY-RNA","t":"A20N","desc":"AIRBUS A-320neo",
         "ownOp":"ROYAL JORDANIAN","alt_baro":35000,"alt_geom":35550,"gs":452.3,"track":271.5,"baro_rate":-64,
         "squawk":"4321","category":"A3","lat":31.9,"lon":35.9,"seen_pos":2.5,"seen":0.1},
        {"hex":"740def","alt_baro":"ground","lat":31.72,"lon":35.99,"gs":5},
        {"hex":"abcdef","alt_baro":12000}],
        "msg":"No error","now":1790000000000,"total":3}"""

    private val openSky = """{"time":1790000000,"states":[
        ["4b1805","SWR123  ","Switzerland",1789999995,1789999999,8.55,47.45,10972.8,false,231.5,95.2,-2.1,null,11277.6,"1000",false,0],
        ["4b1806","","Switzerland",1789999995,1789999999,8.55,47.45,null,true,0,0,0,null,null,null,false,0]]}"""

    @Test fun parsesReadsbAndDropsGroundAndPositionless() {
        val list = FeedParsers.parseReadsb(readsb, FeedSource.ADSB_LOL, nowMs = 0)
        assertEquals(1, list.size)
        val a = list.single()
        assertEquals("740abc", a.hex)
        assertEquals("RJA123", a.callsign)
        assertEquals("A20N", a.typeCode)
        assertEquals(35550 * Geo.FT_TO_M, a.altM, 0.01)
        assertEquals(1_790_000_000_000 - 2_500, a.posTimeMs)
        assertEquals(271.5, a.trackDeg!!, 1e-9)
        // adsb.fi may name the array "aircraft" and send "now" in seconds.
        val fi = FeedParsers.parseReadsb(readsb.replace("\"ac\"", "\"aircraft\"").replace("1790000000000", "1790000000"), FeedSource.ADSB_FI, 0)
        assertEquals(1_790_000_000_000 - 2_500, fi.single().posTimeMs)
    }

    @Test fun parsesOpenSkyWithUnitConversion() {
        val a = FeedParsers.parseOpenSky(openSky, 0).single()
        assertEquals("SWR123", a.callsign)
        assertEquals(11277.6, a.altM, 0.01)
        assertEquals(450.0, a.gsKt!!, 0.5)
        assertEquals(-413.4, a.vRateFpm!!, 0.5)
        assertEquals(1_789_999_995_000, a.posTimeMs)
    }

    @Test fun urls() {
        assertEquals("https://api.adsb.lol/v2/point/31.9500/35.9300/27",
            FeedSource.ADSB_LOL.url(FeedSource.ADSB_LOL.defaultBase, 31.95, 35.93, 27))
        assertEquals("https://opendata.adsb.fi/api/v3/lat/31.9500/lon/35.9300/dist/27",
            FeedSource.ADSB_FI.url(FeedSource.ADSB_FI.defaultBase, 31.95, 35.93, 27))
        assertTrue(FeedSource.OPENSKY.url(FeedSource.OPENSKY.defaultBase, 31.95, 35.93, 27).contains("lamin=31.500"))
    }

    private val server = MockWebServer()
    private var lolCalls = 0

    @After fun tearDown() = server.shutdown()

    @Test fun failsOverAndCoolsDownRateLimitedFeed() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path!!.startsWith("/lol/") -> {
                    lolCalls++
                    if (lolCalls == 1) MockResponse().setResponseCode(429).setHeader("Retry-After", "30")
                    else MockResponse().setBody(readsb)
                }
                request.path!!.startsWith("/fi/") -> MockResponse().setBody(readsb)
                else -> MockResponse().setBody(openSky)
            }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        var now = 1_000_000L
        val client = AircraftFeedClient(
            Http.newClient(), clock = { now },
            bases = mapOf(FeedSource.ADSB_LOL to "$base/lol", FeedSource.ADSB_FI to "$base/fi", FeedSource.OPENSKY to "$base/os"),
        )
        val first = client.fetch(31.95, 35.93, 27) as FeedResult.Ok
        assertEquals(FeedSource.ADSB_FI, first.source)

        now += 6_000 // adsb.lol still cooling down (30 s), adsb.fi due again
        assertEquals(FeedSource.ADSB_FI, (client.fetch(31.95, 35.93, 27) as FeedResult.Ok).source)

        now += 31_000 // cool-down over: back to the primary feed
        assertEquals(FeedSource.ADSB_LOL, (client.fetch(31.95, 35.93, 27) as FeedResult.Ok).source)
        assertEquals(2, lolCalls)
    }
}
