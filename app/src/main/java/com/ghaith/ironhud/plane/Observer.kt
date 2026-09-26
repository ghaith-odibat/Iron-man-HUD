package com.ghaith.ironhud.plane

import com.ghaith.ironhud.ai.executeAndUse
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import kotlin.math.abs

/** A chosen place to watch the sky from, instead of the tablet's GPS position. */
@Serializable
data class ObserverPlace(
    val name: String,
    val lat: Double,
    val lon: Double,
    /** Ground elevation (m); eye height is added on top. */
    val elevationM: Double = 0.0,
) {
    fun toGeo(): GeoPoint = GeoPoint(lat, lon, elevationM + EYE_HEIGHT_M)

    companion object {
        const val EYE_HEIGHT_M = 1.7
    }
}

/** The fixed place wins over GPS; null when neither is known yet. */
fun effectiveViewer(gps: GeoPoint?, fixed: ObserverPlace?): GeoPoint? = fixed?.toGeo() ?: gps

object ObserverParsers {
    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonElement?.text(): String? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    /** "51.4700°N 0.4543°W" */
    fun coordLabel(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.4f°%s %.4f°%s", abs(lat), if (lat >= 0) "N" else "S", abs(lon), if (lon >= 0) "E" else "W")

    /** Photon reverse geocoding (GeoJSON) → "Heathrow Airport, London" style name. */
    fun parsePhotonReverse(body: String): String? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val props = ((root["features"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("properties") as? JsonObject
            ?: return null
        val name = props["name"].text()
        val place = props["city"].text() ?: props["town"].text() ?: props["village"].text()
            ?: props["county"].text() ?: props["state"].text()
        val country = props["country"].text()
        return listOfNotNull(name, place.takeIf { it != name }, country.takeIf { name == null || place == null })
            .distinct().joinToString(", ").takeIf { it.isNotBlank() }
    }

    /** Open-Meteo elevation: `{"elevation":[38.0]}`. */
    fun parseElevation(body: String): Double? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        return ((root["elevation"] as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.doubleOrNull
    }
}

/** Names a tapped map point (Photon, OpenStreetMap) and finds its ground elevation (Open-Meteo). */
class ObserverClient(
    private val http: OkHttpClient,
    private val photonBase: String = "https://photon.komoot.io",
    private val meteoBase: String = "https://api.open-meteo.com",
) {
    suspend fun describe(lat: Double, lon: Double): ObserverPlace {
        val la = String.format(Locale.US, "%.5f", lat)
        val lo = String.format(Locale.US, "%.5f", lon)
        val name = fetch("$photonBase/reverse?lat=$la&lon=$lo&lang=en")?.let(ObserverParsers::parsePhotonReverse)
            ?: ObserverParsers.coordLabel(lat, lon)
        val elevation = fetch("$meteoBase/v1/elevation?latitude=$la&longitude=$lo")?.let(ObserverParsers::parseElevation)
            ?: 0.0
        return ObserverPlace(name, lat, lon, elevation.coerceAtLeast(-500.0))
    }

    private suspend fun fetch(url: String): String? = try {
        http.newCall(
            Request.Builder().url(url).header("User-Agent", "IronHUD/0.1 (personal, non-commercial)").get().build()
        ).executeAndUse { resp -> if (resp.isSuccessful) resp.body?.string() else null }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        null
    }
}
