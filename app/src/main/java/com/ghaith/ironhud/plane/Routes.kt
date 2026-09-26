package com.ghaith.ironhud.plane

import com.ghaith.ironhud.ai.executeAndUse
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Airport(
    val icao: String?,
    val iata: String?,
    val name: String?,
    val city: String?,
    val country: String?,
    val lat: Double?,
    val lon: Double?,
) {
    /** "AMM" (IATA) when known, else the ICAO code. */
    val code: String get() = iata?.takeIf { it.isNotBlank() } ?: icao?.takeIf { it.isNotBlank() } ?: "???"
}

enum class RouteSource(val display: String) { ADSB_LOL("ADSB.LOL"), ADSBDB("ADSBDB") }

/**
 * Where a flight is scheduled to go, looked up by callsign. This is route *database* data (the
 * callsign's usual route), not a live flight plan, so it can occasionally be wrong.
 */
data class FlightRoute(
    val origin: Airport,
    val destination: Airport,
    val midpoint: Airport?,
    val plausible: Boolean,
    val source: RouteSource,
) {
    val short: String get() = "${origin.code} → ${destination.code}"

    /** Fraction of the great-circle trip flown when the plane is at [lat]/[lon]; null without airport coordinates. */
    fun progress(lat: Double, lon: Double): Double? {
        val oLat = origin.lat ?: return null
        val oLon = origin.lon ?: return null
        val dLat = destination.lat ?: return null
        val dLon = destination.lon ?: return null
        val flown = Geo.greatCircleM(oLat, oLon, lat, lon)
        val left = Geo.greatCircleM(lat, lon, dLat, dLon)
        if (flown + left < 1.0) return null
        return (flown / (flown + left)).coerceIn(0.0, 1.0)
    }
}

/** Haversine distance, added to [Geo] for route maths. */
fun Geo.greatCircleM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val p1 = rad(lat1)
    val p2 = rad(lat2)
    val dp = rad(lat2 - lat1)
    val dl = rad(lon2 - lon1)
    val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

object RouteParsers {
    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonElement?.text(): String? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    private fun JsonElement?.num(): Double? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull

    /** A callsign that looks like an airline flight (ICAO airline + number), the only kind with routes. */
    fun isRoutable(callsign: String?): Boolean = callsign != null && ROUTABLE.matches(normalize(callsign))

    fun normalize(callsign: String): String = callsign.trim().uppercase(Locale.US)

    private val ROUTABLE = Regex("""^[A-Z]{3}[0-9][0-9A-Z]{0,4}$""")

    /**
     * adsb.lol `routeset` response: an array of
     * `{"callsign":…, "_airports":[{iata, icao, name, location, countryiso2, lat, lon}, …], "plausible":…}`.
     * Known-unknown callsigns map to null.
     */
    fun parseRouteset(body: String): Map<String, FlightRoute?> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray ?: return emptyMap()
        val out = HashMap<String, FlightRoute?>()
        for (el in root) {
            val o = el as? JsonObject ?: continue
            val cs = o["callsign"].text()?.let(::normalize) ?: continue
            val airports = (o["_airports"] as? JsonArray).orEmpty().mapNotNull { a ->
                val ao = a as? JsonObject ?: return@mapNotNull null
                Airport(
                    icao = ao["icao"].text(), iata = ao["iata"].text(), name = ao["name"].text(),
                    city = ao["location"].text(), country = ao["countryiso2"].text(),
                    lat = ao["lat"].num(), lon = ao["lon"].num(),
                )
            }
            out[cs] = if (airports.size < 2) null else FlightRoute(
                origin = airports.first(),
                destination = airports.last(),
                midpoint = if (airports.size > 2) airports[1] else null,
                plausible = plausible(o["plausible"]),
                source = RouteSource.ADSB_LOL,
            )
        }
        return out
    }

    private fun plausible(e: JsonElement?): Boolean {
        val p = e as? JsonPrimitive ?: return true
        return p.booleanOrNull ?: p.intOrNull?.let { it != 0 } ?: (p.contentOrNull?.lowercase(Locale.US) != "false")
    }

    /** adsbdb `callsign` response: `{"response":{"flightroute":{"origin":{…},"destination":{…},"midpoint"?}}}`. */
    fun parseAdsbdb(body: String): FlightRoute? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val fr = ((root["response"] as? JsonObject)?.get("flightroute")) as? JsonObject ?: return null
        fun airport(e: JsonElement?): Airport? {
            val o = e as? JsonObject ?: return null
            return Airport(
                icao = o["icao_code"].text(), iata = o["iata_code"].text(), name = o["name"].text(),
                city = o["municipality"].text(), country = o["country_iso_name"].text(),
                lat = o["latitude"].num(), lon = o["longitude"].num(),
            )
        }
        val origin = airport(fr["origin"]) ?: return null
        val destination = airport(fr["destination"]) ?: return null
        return FlightRoute(origin, destination, airport(fr["midpoint"]), plausible = true, source = RouteSource.ADSBDB)
    }
}

/**
 * Resolves callsigns to routes: one batched adsb.lol `routeset` call for everything new, then
 * adsbdb one-by-one (a few per round) for anything that call couldn't answer. Results are cached
 * for the session; unknown callsigns are not asked again for 30 minutes.
 */
class RouteClient(
    private val http: OkHttpClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val lolBase: String = "https://api.adsb.lol",
    private val dbBase: String = "https://api.adsbdb.com",
) {
    private val cache = HashMap<String, FlightRoute>()
    private val unknownUntil = HashMap<String, Long>()
    private var lolCoolUntil = 0L
    private var dbCoolUntil = 0L

    @Synchronized
    fun snapshot(): Map<String, FlightRoute> = HashMap(cache)

    suspend fun resolve(planes: List<Aircraft>, maxBatch: Int = 40): Map<String, FlightRoute> {
        val now = clock()
        val pending = planes
            .mapNotNull { a -> a.callsign?.let(RouteParsers::normalize)?.let { it to a } }
            .filter { (cs, _) -> RouteParsers.isRoutable(cs) && synchronized(this) { cs !in cache && (unknownUntil[cs] ?: 0) <= now } }
            .distinctBy { it.first }
            .take(maxBatch)
        if (pending.isEmpty()) return snapshot()

        val unanswered = pending.toMutableList()
        if (now >= lolCoolUntil) {
            try {
                val results = routeset(pending)
                synchronized(this) {
                    for ((cs, route) in results) {
                        if (route != null) cache[cs] = route else unknownUntil[cs] = now + UNKNOWN_TTL_MS
                    }
                }
                unanswered.removeAll { it.first in results }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lolCoolUntil = now + COOLDOWN_MS
            }
        }

        var asked = 0
        for ((cs, _) in unanswered) {
            if (asked >= MAX_ADSBDB_PER_ROUND || clock() < dbCoolUntil) break
            asked++
            try {
                val request = Request.Builder().url("$dbBase/v0/callsign/$cs")
                    .header("User-Agent", USER_AGENT).get().build()
                http.newCall(request).executeAndUse { resp ->
                    val body = resp.body?.string().orEmpty()
                    val route = if (resp.isSuccessful) RouteParsers.parseAdsbdb(body) else null
                    if (route != null) {
                        synchronized(this) { cache[cs] = route }
                    } else if (resp.isSuccessful || resp.code == 404) {
                        synchronized(this) { unknownUntil[cs] = clock() + UNKNOWN_TTL_MS }
                    } else {
                        dbCoolUntil = clock() + COOLDOWN_MS
                    }
                    Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                dbCoolUntil = clock() + COOLDOWN_MS
            }
        }
        return snapshot()
    }

    private suspend fun routeset(pending: List<Pair<String, Aircraft>>): Map<String, FlightRoute?> {
        val body = buildJsonObject {
            putJsonArray("planes") {
                for ((cs, a) in pending) addJsonObject {
                    put("callsign", cs)
                    put("lat", a.lat)
                    put("lng", a.lon)
                }
            }
        }.toString()
        val request = Request.Builder().url("$lolBase/api/0/routeset")
            .header("User-Agent", USER_AGENT)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return http.newCall(request).executeAndUse { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("routeset HTTP ${resp.code}")
            RouteParsers.parseRouteset(text) // an empty/garbled body just leaves everything for adsbdb
        }
    }

    companion object {
        const val UNKNOWN_TTL_MS = 30 * 60_000L
        const val COOLDOWN_MS = 60_000L
        const val MAX_ADSBDB_PER_ROUND = 8
        private const val USER_AGENT = "IronHUD/0.1 (personal, non-commercial)"
    }
}
