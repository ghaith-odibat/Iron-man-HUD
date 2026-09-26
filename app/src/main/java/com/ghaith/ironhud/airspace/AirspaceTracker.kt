package com.ghaith.ironhud.airspace

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.ghaith.ironhud.plane.Aircraft
import com.ghaith.ironhud.plane.AircraftFeedClient
import com.ghaith.ironhud.plane.FeedResult
import com.ghaith.ironhud.plane.FeedSource
import com.ghaith.ironhud.plane.Geo
import com.ghaith.ironhud.plane.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

data class AirspaceState(
    val active: Boolean = false,
    val viewer: GeoPoint? = null,
    val viewerAccuracyM: Float? = null,
    /** East-positive magnetic declination at the viewer. */
    val declinationDeg: Double = 0.0,
    /** Nearest first. */
    val aircraft: List<Aircraft> = emptyList(),
    val source: FeedSource? = null,
    val lastUpdateMs: Long = 0,
    val status: String = "PLANE MODE OFF",
)

/**
 * Plane Mode's data side: the tablet's GPS position (no Play Services needed) and a live ADS-B
 * poll of everything airborne within [RADIUS_NM], every [POLL_MS] while running.
 */
class AirspaceTracker(context: Context, http: OkHttpClient, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val client = AircraftFeedClient(http)
    private val _state = MutableStateFlow(AirspaceState())
    val state: StateFlow<AirspaceState> = _state.asStateFlow()
    private var job: Job? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (job?.isActive == true) return
        _state.update { it.copy(active = true, status = if (it.viewer == null) "ACQUIRING GPS" else it.status) }
        job = scope.launch {
            launch { locationUpdates().collect { onLocation(it) } }
            launch { pollLoop() }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.update { it.copy(active = false, status = "PLANE MODE OFF") }
    }

    private fun onLocation(loc: Location) {
        val previous = _state.value.viewer
        // Network fixes often have no altitude: keep the last GPS altitude rather than dropping to sea level.
        val alt = if (loc.hasAltitude()) loc.altitude else previous?.altM ?: 0.0
        val viewer = GeoPoint(loc.latitude, loc.longitude, alt)
        val declination = GeomagneticField(
            loc.latitude.toFloat(), loc.longitude.toFloat(), alt.toFloat(), System.currentTimeMillis(),
        ).declination.toDouble()
        _state.update {
            it.copy(
                viewer = viewer,
                viewerAccuracyM = if (loc.hasAccuracy()) loc.accuracy else null,
                declinationDeg = declination,
                status = if (it.viewer == null) "GPS LOCK · SCANNING AIRSPACE" else it.status,
            )
        }
    }

    private suspend fun pollLoop() {
        while (currentCoroutineContext().isActive) {
            val viewer = _state.value.viewer
            if (viewer == null) {
                delay(1_000)
                continue
            }
            when (val r = client.fetch(viewer.lat, viewer.lon, RADIUS_NM)) {
                is FeedResult.Ok -> _state.update {
                    val sorted = r.aircraft.sortedBy { a -> Geo.enu(viewer, GeoPoint(a.lat, a.lon, a.altM)).horizontal }
                    it.copy(aircraft = sorted, source = r.source, lastUpdateMs = System.currentTimeMillis(), status = "LIVE")
                }
                is FeedResult.Failed -> _state.update {
                    val stale = System.currentTimeMillis() - it.lastUpdateMs > STALE_MS
                    it.copy(aircraft = if (stale) emptyList() else it.aircraft, status = "FEED: ${r.message}")
                }
            }
            delay(POLL_MS)
        }
    }

    @SuppressLint("MissingPermission")
    private fun locationUpdates(): Flow<Location> = callbackFlow {
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!hasPermission()) {
            _state.update { it.copy(status = "LOCATION PERMISSION NEEDED") }
            close()
            return@callbackFlow
        }
        // All four callbacks implemented explicitly: Android 8/9 have no default methods here.
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }

            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) _state.update { it.copy(status = "TURN ON LOCATION") }
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }?.let { trySend(it) }
        for (p in providers) {
            runCatching { lm.requestLocationUpdates(p, 2_000L, 5f, listener, Looper.getMainLooper()) }
        }
        awaitClose { lm.removeUpdates(listener) }
    }

    companion object {
        const val RADIUS_NM = 27 // ≈ 50 km
        const val POLL_MS = 5_000L
        const val STALE_MS = 60_000L
    }
}
