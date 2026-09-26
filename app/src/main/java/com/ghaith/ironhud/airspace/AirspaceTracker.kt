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
import com.ghaith.ironhud.plane.FlightRoute
import com.ghaith.ironhud.plane.ObserverPlace
import com.ghaith.ironhud.plane.RouteClient
import com.ghaith.ironhud.plane.effectiveViewer
import com.ghaith.ironhud.plane.AircraftFeedClient
import com.ghaith.ironhud.plane.FeedResult
import com.ghaith.ironhud.plane.FeedSource
import com.ghaith.ironhud.plane.Geo
import com.ghaith.ironhud.plane.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

data class AirspaceState(
    val active: Boolean = false,
    /** Where the sky is watched from: the chosen [observer] place, else the GPS fix. */
    val viewer: GeoPoint? = null,
    /** A chosen place (null = use GPS). */
    val observer: ObserverPlace? = null,
    /** The tablet's own GPS fix, even when watching a chosen place. */
    val gps: GeoPoint? = null,
    val viewerAccuracyM: Float? = null,
    /**
     * East-positive magnetic declination used to align the tablet's compass with true north: the
     * tablet's own (physical) location when known, since that's where its compass is.
     */
    val declinationDeg: Double = 0.0,
    /** Departure/arrival airports by callsign. */
    val routes: Map<String, FlightRoute> = emptyMap(),
    /** Nearest first. */
    val aircraft: List<Aircraft> = emptyList(),
    val source: FeedSource? = null,
    val lastUpdateMs: Long = 0,
    val status: String = "PLANE MODE OFF",
) {
    val isVirtual: Boolean get() = observer != null
}

/**
 * Plane Mode's data side: the tablet's GPS position (no Play Services needed) and a live ADS-B
 * poll of everything airborne within [RADIUS_NM], every [POLL_MS] while running.
 */
class AirspaceTracker(context: Context, http: OkHttpClient, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val client = AircraftFeedClient(http)
    private val routeClient = RouteClient(http)
    private var routeJob: Job? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var deviceDeclination: Double? = null
    private val _state = MutableStateFlow(AirspaceState())
    val state: StateFlow<AirspaceState> = _state.asStateFlow()
    private var job: Job? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Watch from [place], or from the GPS fix when null. Takes effect immediately. */
    fun setObserver(place: ObserverPlace?) {
        if (place == _state.value.observer) return
        _state.update {
            val viewer = effectiveViewer(it.gps, place)
            it.copy(
                observer = place,
                viewer = viewer,
                declinationDeg = declinationFor(viewer),
                aircraft = emptyList(),
                source = null,
                lastUpdateMs = 0,
                status = when {
                    !it.active -> it.status
                    viewer == null -> "ACQUIRING GPS"
                    else -> "SCANNING AIRSPACE"
                },
            )
        }
        wake.trySend(Unit)
    }

    private fun declinationFor(viewer: GeoPoint?): Double = deviceDeclination
        ?: viewer?.let { v ->
            GeomagneticField(v.lat.toFloat(), v.lon.toFloat(), v.altM.toFloat(), System.currentTimeMillis()).declination.toDouble()
        }
        ?: 0.0

    fun start() {
        if (job?.isActive == true) return
        _state.update { it.copy(active = true, status = if (it.viewer == null) "ACQUIRING GPS" else "SCANNING AIRSPACE") }
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
        val previous = _state.value.gps
        // Network fixes often have no altitude: keep the last GPS altitude rather than dropping to sea level.
        val alt = if (loc.hasAltitude()) loc.altitude else previous?.altM ?: 0.0
        val gps = GeoPoint(loc.latitude, loc.longitude, alt)
        deviceDeclination = GeomagneticField(
            loc.latitude.toFloat(), loc.longitude.toFloat(), alt.toFloat(), System.currentTimeMillis(),
        ).declination.toDouble()
        _state.update {
            val viewer = effectiveViewer(gps, it.observer)
            it.copy(
                gps = gps,
                viewer = viewer,
                viewerAccuracyM = if (loc.hasAccuracy()) loc.accuracy else null,
                declinationDeg = declinationFor(viewer),
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
            val observerAtRequest = _state.value.observer
            when (val r = client.fetch(viewer.lat, viewer.lon, RADIUS_NM)) {
                is FeedResult.Ok -> if (_state.value.observer == observerAtRequest) {
                    // (Results for a place the user has since left are dropped; the wake-up below re-polls.)
                    val sorted = r.aircraft.sortedBy { a -> Geo.enu(viewer, GeoPoint(a.lat, a.lon, a.altM)).horizontal }
                    _state.update {
                        it.copy(aircraft = sorted, source = r.source, lastUpdateMs = System.currentTimeMillis(), status = "LIVE")
                    }
                    resolveRoutes(sorted)
                }
                is FeedResult.Failed -> _state.update {
                    val stale = System.currentTimeMillis() - it.lastUpdateMs > STALE_MS
                    it.copy(aircraft = if (stale) emptyList() else it.aircraft, status = "FEED: ${r.message}")
                }
            }
            // Sleep until the next poll, or until the observer location changes.
            withTimeoutOrNull(POLL_MS) { wake.receive() }
        }
    }

    /** Departure/arrival lookups run beside the poll so a slow route source never delays the planes. */
    private fun resolveRoutes(aircraft: List<Aircraft>) {
        if (routeJob?.isActive == true) return
        val parent = job ?: return
        routeJob = scope.launch(parent) {
            val routes = routeClient.resolve(aircraft.take(ROUTE_LOOKUPS))
            _state.update { it.copy(routes = routes) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun locationUpdates(): Flow<Location> = callbackFlow {
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!hasPermission()) {
            // Fine when watching a chosen place; only GPS mode needs the permission.
            if (_state.value.observer == null) _state.update { it.copy(status = "LOCATION PERMISSION NEEDED") }
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
        const val ROUTE_LOOKUPS = 40
    }
}
