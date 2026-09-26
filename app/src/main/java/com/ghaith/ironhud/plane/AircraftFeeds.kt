package com.ghaith.ironhud.plane

import com.ghaith.ironhud.ai.executeAndUse
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import kotlin.math.cos

/**
 * Free, key-less live ADS-B sources, tried in this order. adsb.lol and adsb.fi serve the
 * ADS-B Exchange v2 ("readsb") format; OpenSky is the anonymous last resort (400 calls/day).
 */
enum class FeedSource(val display: String, val attribution: String, val minIntervalMs: Long) {
    ADSB_LOL("ADSB.LOL", "DATA ADSB.LOL · ODbL", 5_000),
    ADSB_FI("ADSB.FI", "DATA ADSB.FI", 5_000),
    OPENSKY("OPENSKY", "DATA THE OPENSKY NETWORK", 15_000);

    val defaultBase: String
        get() = when (this) {
            ADSB_LOL -> "https://api.adsb.lol"
            ADSB_FI -> "https://opendata.adsb.fi"
            OPENSKY -> "https://opensky-network.org"
        }

    fun url(base: String, lat: Double, lon: Double, radiusNm: Int): String {
        val la = "%.4f".format(Locale.US, lat)
        val lo = "%.4f".format(Locale.US, lon)
        return when (this) {
            ADSB_LOL -> "$base/v2/point/$la/$lo/$radiusNm"
            ADSB_FI -> "$base/api/v3/lat/$la/lon/$lo/dist/$radiusNm"
            OPENSKY -> {
                val dLat = radiusNm * Geo.NM_TO_KM / 111.0
                val dLon = dLat / cos(Geo.rad(lat)).coerceAtLeast(0.2)
                fun f(v: Double) = "%.3f".format(Locale.US, v)
                "$base/api/states/all?lamin=${f(lat - dLat)}&lomin=${f(lon - dLon)}&lamax=${f(lat + dLat)}&lomax=${f(lon + dLon)}"
            }
        }
    }

    fun parse(body: String, nowMs: Long): List<Aircraft> = when (this) {
        OPENSKY -> FeedParsers.parseOpenSky(body, nowMs)
        else -> FeedParsers.parseReadsb(body, this, nowMs)
    }
}

object FeedParsers {
    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonElement?.num(): Double? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull
    private fun JsonElement?.text(): String? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    /** ADS-B Exchange v2 / readsb JSON: `{"ac":[...], "now": ...}` (adsb.fi may say "aircraft"). */
    fun parseReadsb(body: String, source: FeedSource, nowMs: Long): List<Aircraft> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return emptyList()
        val list = (root["ac"] ?: root["aircraft"]) as? JsonArray ?: return emptyList()
        // "now" is epoch millis in the v2 API but seconds in raw readsb output.
        val feedNow = root["now"].num()?.let { if (it > 1e12) it.toLong() else (it * 1000).toLong() } ?: nowMs
        return list.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val hex = o["hex"].text()?.lowercase(Locale.US) ?: return@mapNotNull null
            val lat = o["lat"].num() ?: return@mapNotNull null
            val lon = o["lon"].num() ?: return@mapNotNull null
            if ((o["alt_baro"] as? JsonPrimitive)?.contentOrNull == "ground") return@mapNotNull null
            val altFt = o["alt_geom"].num() ?: o["alt_baro"].num() ?: return@mapNotNull null
            val seenPos = o["seen_pos"].num() ?: o["seen"].num() ?: 0.0
            Aircraft(
                hex = hex,
                callsign = o["flight"].text(),
                registration = o["r"].text(),
                typeCode = o["t"].text()?.uppercase(Locale.US),
                description = o["desc"].text(),
                operator = o["ownOp"].text(),
                category = o["category"].text(),
                lat = lat,
                lon = lon,
                altM = altFt * Geo.FT_TO_M,
                gsKt = o["gs"].num(),
                trackDeg = o["track"].num() ?: o["true_heading"].num() ?: o["mag_heading"].num(),
                vRateFpm = o["baro_rate"].num() ?: o["geom_rate"].num(),
                squawk = o["squawk"].text(),
                posTimeMs = feedNow - (seenPos * 1000).toLong(),
                source = source,
            )
        }
    }

    /**
     * OpenSky `states/all`: each state is an array
     * [icao24, callsign, country, time_position, last_contact, lon, lat, baro_alt_m, on_ground,
     *  velocity_ms, true_track, vertical_rate_ms, sensors, geo_alt_m, squawk, spi, position_source, (category)].
     */
    fun parseOpenSky(body: String, nowMs: Long): List<Aircraft> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return emptyList()
        val states = root["states"] as? JsonArray ?: return emptyList()
        return states.mapNotNull { el ->
            val s = el as? JsonArray ?: return@mapNotNull null
            if (s.size < 15) return@mapNotNull null
            if ((s[8] as? JsonPrimitive)?.booleanOrNull == true) return@mapNotNull null
            val lon = s[5].num() ?: return@mapNotNull null
            val lat = s[6].num() ?: return@mapNotNull null
            val altM = s[13].num() ?: s[7].num() ?: return@mapNotNull null
            val category = s.getOrNull(17).num()?.toInt()?.let { OPENSKY_CATEGORY[it] }
            Aircraft(
                hex = s[0].text()?.lowercase(Locale.US) ?: return@mapNotNull null,
                callsign = s[1].text(),
                registration = null,
                typeCode = null,
                description = null,
                operator = null,
                category = category,
                lat = lat,
                lon = lon,
                altM = altM,
                gsKt = s[9].num()?.let { it / Geo.KT_TO_MS },
                trackDeg = s[10].num(),
                vRateFpm = s[11].num()?.let { it / Geo.FPM_TO_MS },
                squawk = s[14].text(),
                posTimeMs = s[3].num()?.let { (it * 1000).toLong() } ?: nowMs,
                source = FeedSource.OPENSKY,
            )
        }
    }

    /** OpenSky's numeric categories mapped to ADS-B emitter categories. */
    private val OPENSKY_CATEGORY = mapOf(
        2 to "A1", 3 to "A2", 4 to "A3", 5 to "A4", 6 to "A5", 7 to "A6", 8 to "A7",
        9 to "B1", 10 to "B2", 11 to "B3", 12 to "B4", 14 to "B6", 15 to "B7",
    )
}

sealed interface FeedResult {
    data class Ok(val aircraft: List<Aircraft>, val source: FeedSource) : FeedResult
    data class Failed(val message: String, val retryAtMs: Long?) : FeedResult
}

/**
 * Fetches aircraft around a point from the first feed that isn't cooling down, falling through to
 * the next one when a feed errors or rate-limits (same idea as the API-key pool).
 */
class AircraftFeedClient(
    private val http: OkHttpClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val bases: Map<FeedSource, String> = FeedSource.entries.associateWith { it.defaultBase },
) {
    private val coolUntil = HashMap<FeedSource, Long>()
    private val lastCall = HashMap<FeedSource, Long>()

    suspend fun fetch(lat: Double, lon: Double, radiusNm: Int): FeedResult {
        var lastError = "NO FEED AVAILABLE"
        for (source in FeedSource.entries) {
            val now = clock()
            if ((coolUntil[source] ?: 0) > now) continue
            // Honour each feed's own polling budget (OpenSky's is small); skip rather than wait.
            if (now - (lastCall[source] ?: Long.MIN_VALUE / 2) < source.minIntervalMs - 500) continue
            lastCall[source] = now
            val request = Request.Builder()
                .url(source.url(bases.getValue(source), lat, lon, radiusNm))
                .header("User-Agent", "IronHUD/0.1 (personal, non-commercial)")
                .build()
            try {
                val result = http.newCall(request).executeAndUse { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        FeedResult.Ok(source.parse(body, clock()), source)
                    } else {
                        val wait = resp.header("retry-after")?.trim()?.toDoubleOrNull()?.let { (it * 1000).toLong() }
                        coolUntil[source] = clock() + (wait ?: FAIL_COOLDOWN_MS).coerceIn(5_000, 10 * 60_000)
                        FeedResult.Failed("${source.display} HTTP ${resp.code}", null)
                    }
                }
                if (result is FeedResult.Ok) return result
                lastError = (result as FeedResult.Failed).message
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                coolUntil[source] = clock() + FAIL_COOLDOWN_MS
                lastError = "${source.display} OFFLINE"
            }
        }
        return FeedResult.Failed(lastError, coolUntil.values.minOrNull())
    }

    companion object {
        const val FAIL_COOLDOWN_MS = 60_000L
    }
}
