package com.ghaith.ironhud

import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ghaith.ironhud.ai.ApiKeyEntry
import com.ghaith.ironhud.ai.Prompt
import com.ghaith.ironhud.airspace.AirspaceState
import com.ghaith.ironhud.airspace.AirspaceTracker
import com.ghaith.ironhud.plane.CameraOptics
import com.ghaith.ironhud.plane.PlaneHitIndex
import com.ghaith.ironhud.plane.ObserverClient
import com.ghaith.ironhud.plane.PlanePrompt
import com.ghaith.ironhud.plane.RouteParsers
import com.ghaith.ironhud.ai.Brief
import com.ghaith.ironhud.ai.Http
import com.ghaith.ironhud.ai.IdentifyService
import com.ghaith.ironhud.ai.KeyPool
import com.ghaith.ironhud.ai.KeyTestResult
import com.ghaith.ironhud.ai.PoolView
import com.ghaith.ironhud.ai.ProviderId
import com.ghaith.ironhud.ai.ScanEvent
import com.ghaith.ironhud.data.HudSettings
import com.ghaith.ironhud.data.SecureKeyRepository
import com.ghaith.ironhud.data.SettingsRepository
import com.ghaith.ironhud.vision.EdgeWireframe
import com.ghaith.ironhud.vision.ImagePrep
import com.ghaith.ironhud.vision.OnDeviceLabeler
import com.ghaith.ironhud.vision.TrackedObject
import com.ghaith.ironhud.voice.Jarvis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

class HudViewModel(app: Application) : AndroidViewModel(app) {

    private val keyRepo = SecureKeyRepository(app)
    private val settingsRepo = SettingsRepository(app)
    private val pool = KeyPool()
    private val modelOverrides = MutableStateFlow<Map<ProviderId, String>>(emptyMap())
    private val http = Http.newClient()
    private val identifier = IdentifyService(pool, http, modelOverrides = { modelOverrides.value })
    private val airspaceTracker = AirspaceTracker(app, http, viewModelScope)
    private val observerClient = ObserverClient(http)
    val airspace: StateFlow<AirspaceState> = airspaceTracker.state
    /** Screen positions of the planes drawn last frame (written by the plane layer). */
    val planeHits = PlaneHitIndex()
    private val labeler = OnDeviceLabeler()
    private val jarvis = Jarvis(app)

    private val _state = MutableStateFlow(HudState())
    val state: StateFlow<HudState> = _state.asStateFlow()

    val settings: StateFlow<HudSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, HudSettings())
    val poolView: StateFlow<PoolView> = pool.view

    /** Grabs the current preview frame in view pixels; set by the camera layer, main thread only. */
    var frameSource: (() -> Bitmap?)? = null
    private var viewW = 0
    private var viewH = 0

    private var scanJob: Job? = null
    private var planeBriefJob: Job? = null
    private var foreground = false
    private val planeBriefCache = HashMap<String, PanelUi>()
    private var keepAliveJob: Job? = null
    private var dwellId: Int? = null
    /** A target the user just dismissed: don't auto-lock it again until it leaves the reticle. */
    private var suppressedId: Int? = null
    private var dwellStart = 0L
    private var lastAutoScan = 0L
    private val cache = object : LinkedHashMap<Int, PanelUi>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, PanelUi>?) = size > 40
    }

    init {
        viewModelScope.launch {
            combine(keyRepo.keys, settingsRepo.settings) { keys, s -> keys to s }.collect { (keys, s) ->
                modelOverrides.value = s.models
                pool.configure(keys, s.providerOrder, s.keyStatus)
            }
        }
        viewModelScope.launch {
            // Persist cool-downs so a restart doesn't hammer a key that's still limited.
            pool.view.collectLatest { view ->
                _state.update { it.copy(uplink = it.uplink.copy(readyKeys = view.readyCount, totalKeys = view.rows.size)) }
                delay(1_500)
                settingsRepo.saveKeyStatus(pool.exportStatus())
            }
        }
        viewModelScope.launch {
            // The saved Plane Mode observer (a chosen place, or GPS) follows the settings.
            settingsRepo.settings.map { it.planeObserver }.distinctUntilChanged().collect { airspaceTracker.setObserver(it) }
        }
        viewModelScope.launch {
            while (isActive) {
                pool.tick()
                delay(1_000)
            }
        }
    }

    // ---- lifecycle -----------------------------------------------------------------------------

    /** While the HUD is on screen, keep TLS connections warm so a lock doesn't pay for a handshake. */
    fun onForeground(foreground: Boolean) {
        this.foreground = foreground
        keepAliveJob?.cancel()
        if (_state.value.planeMode) {
            if (foreground) airspaceTracker.start() else airspaceTracker.stop()
        }
        if (!foreground) {
            jarvis.stop()
            // The camera restarts at 1× when the app comes back, so forget any pending zoom.
            _state.update { it.copy(zoomTarget = null) }
            return
        }
        keepAliveJob = viewModelScope.launch {
            delay(400)
            while (isActive) {
                identifier.warmUp()
                delay(KEEP_ALIVE_MS)
            }
        }
    }

    fun onViewSize(w: Int, h: Int) {
        viewW = w
        viewH = h
    }

    // ---- targeting ---------------------------------------------------------------------------------

    fun onObjects(objects: List<TrackedObject>) {
        if (_state.value.planeMode) return
        val now = SystemClock.uptimeMillis()
        val current = _state.value
        val lock = current.lock?.let { l ->
            val moved = l.targetId?.let { id -> objects.firstOrNull { it.id == id } }
            if (moved != null) l.copy(box = moved.box) else l
        }

        var dwellProgress = 0f
        var charging: TrackedObject? = null
        if (settings.value.autoLock && viewW > 0) {
            val center = Offset(viewW / 2f, viewH / 2f)
            val candidate = objects.filter { it.id != null && it.box.contains(center) }.minByOrNull { it.area }
            if (candidate?.id != suppressedId) suppressedId = null
            if (candidate == null || candidate.id == lock?.targetId || candidate.id == suppressedId) {
                dwellId = null
            } else {
                if (candidate.id != dwellId) {
                    dwellId = candidate.id
                    dwellStart = now
                }
                dwellProgress = ((now - dwellStart) / DWELL_MS.toFloat()).coerceIn(0f, 1f)
                charging = candidate
            }
        }
        _state.update {
            it.copy(targets = objects, lock = lock, dwellId = charging?.id, dwellProgress = dwellProgress)
        }

        if (charging != null && dwellProgress >= 1f && now - lastAutoScan >= AUTO_SCAN_GAP_MS) {
            lastAutoScan = now
            dwellId = null
            lockOn(charging.id, charging.box, charging.category)
        }
    }

    fun onTap(point: Offset) {
        if (_state.value.planeMode) {
            val hex = planeHits.nearest(point.x, point.y, PLANE_TAP_RADIUS_PX)
            if (hex != null) selectPlane(hex) else if (_state.value.selectedHex != null) deselectPlane()
            return
        }
        val hit = _state.value.targets.filter { it.box.contains(point) }.minByOrNull { it.area }
        if (hit != null) {
            lockOn(hit.id, hit.box, hit.category)
        } else {
            // Nothing tracked there (landmarks, screens, big scenes): scan a square around the tap.
            lockOn(null, squareAround(point, SCAN_REGION), null)
        }
    }

    /** SCAN button: whatever is under the reticle, tracked or not. */
    fun scanCenter() {
        if (viewW == 0) return
        if (_state.value.planeMode) {
            planeHits.nearest(viewW / 2f, viewH / 2f, min(viewW, viewH) / 3f)?.let(::selectPlane)
                ?: toast("NO AIRCRAFT IN THE RETICLE")
            return
        }
        val center = Offset(viewW / 2f, viewH / 2f)
        val hit = _state.value.targets.filter { it.box.contains(center) }.minByOrNull { it.area }
        if (hit != null) lockOn(hit.id, hit.box, hit.category) else lockOn(null, squareAround(center, SCAN_REGION), null)
    }

    // ---- plane mode --------------------------------------------------------------------------------

    fun hasLocationPermission() = airspaceTracker.hasPermission()

    fun setPlaneMode(on: Boolean) {
        if (on == _state.value.planeMode) return
        if (on) {
            scanJob?.cancel()
            jarvis.stop()
            _state.update {
                it.copy(planeMode = true, targets = emptyList(), lock = null, panel = null, dwellId = null, dwellProgress = 0f)
            }
            if (foreground) airspaceTracker.start()
            toast("PLANE MODE · SCANNING AIRSPACE")
        } else {
            airspaceTracker.stop()
            planeBriefJob?.cancel()
            jarvis.stop()
            planeHits.update(emptyList())
            _state.update { it.copy(planeMode = false, selectedHex = null, planePanel = null) }
        }
    }

    // ---- observer location ---------------------------------------------------------------------------

    fun openLocationPicker() = _state.update { it.copy(locationPicker = true) }

    fun closeLocationPicker() = _state.update { it.copy(locationPicker = false, pickerBusy = false) }

    /** Watch the sky from a point picked on the map: name it and find its ground height, then switch. */
    fun chooseLocation(lat: Double, lon: Double) {
        _state.update { it.copy(pickerBusy = true) }
        viewModelScope.launch {
            val place = observerClient.describe(lat, lon)
            settingsRepo.setPlaneObserver(place)
            deselectPlane()
            _state.update { it.copy(pickerBusy = false, locationPicker = false) }
            toast("OBSERVER · ${place.name.uppercase(Locale.US).take(40)}")
        }
    }

    /** Back to the tablet's own GPS position (the UI makes sure location permission is granted first). */
    fun useMyLocation() {
        viewModelScope.launch {
            settingsRepo.setPlaneObserver(null)
            deselectPlane()
            _state.update { it.copy(locationPicker = false) }
            toast("OBSERVER · MY LOCATION")
        }
    }

    fun setSkyView(on: Boolean) = viewModelScope.launch { settingsRepo.setSkyView(on) }

    fun deselectPlane() {
        planeBriefJob?.cancel()
        jarvis.stop()
        _state.update { it.copy(selectedHex = null, planePanel = null) }
    }

    /** Select a plane and have J.A.R.V.I.S. brief it (text-only request, cached per type + operator + flight). */
    fun selectPlane(hex: String) {
        val aircraft = airspace.value.aircraft.firstOrNull { it.hex == hex } ?: return
        planeBriefJob?.cancel()
        jarvis.stop()
        val route = aircraft.callsign?.let { airspace.value.routes[RouteParsers.normalize(it)] }
        val key = PlanePrompt.cacheKey(aircraft, route)
        planeBriefCache[key]?.let { cached ->
            _state.update { it.copy(selectedHex = hex, planePanel = cached.copy(fromCache = true)) }
            if (settings.value.voice) jarvis.speak(cached.brief)
            return
        }
        _state.update { it.copy(selectedHex = hex, planePanel = PanelUi(status = "QUERYING J.A.R.V.I.S.")) }
        planeBriefJob = viewModelScope.launch {
            identifier.describe(Prompt.AIRCRAFT_SYSTEM, PlanePrompt.user(aircraft, airspace.value.viewer, route)).collect { event ->
                when (event) {
                    is ScanEvent.Attempt -> updatePlanePanel {
                        it.copy(status = "UPLINK ${event.provider.display} · KEY ${event.keyIndex}/${event.keyCount}")
                    }
                    is ScanEvent.Partial -> updatePlanePanel { it.copy(brief = event.brief, status = "RECEIVING") }
                    is ScanEvent.KeySwitched -> updatePlanePanel {
                        it.copy(status = "${event.provider.display} ${event.reason.substringBefore(" —")} · SWITCHING KEY")
                    }
                    is ScanEvent.Done -> {
                        val panel = (_state.value.planePanel ?: PanelUi()).copy(
                            brief = event.brief, streaming = false, status = "BRIEF COMPLETE",
                            source = "${event.provider.display} · ${event.model} · ${event.latencyMs} MS", error = null,
                        )
                        planeBriefCache[key] = panel
                        _state.update { it.copy(planePanel = panel) }
                        if (settings.value.voice) jarvis.speak(event.brief)
                    }
                    is ScanEvent.Failed -> updatePlanePanel {
                        it.copy(streaming = false, status = "NO AI BRIEF", error = event.reason)
                    }
                }
            }
        }
    }

    private inline fun updatePlanePanel(crossinline f: (PanelUi) -> PanelUi) {
        _state.update { s -> if (s.selectedHex == null) s else s.copy(planePanel = f(s.planePanel ?: PanelUi())) }
    }

    fun onOptics(optics: CameraOptics) = _state.update { it.copy(optics = optics) }

    // ---- zoom ------------------------------------------------------------------------------------

    fun onZoomState(ratio: Float, min: Float, max: Float) =
        _state.update { it.copy(zoom = ratio, zoomMin = min, zoomMax = max) }

    /** Pinch: multiply the current zoom by [factor]. Builds on the pending target so fast pinches don't lag. */
    fun zoomBy(factor: Float) = setZoom((_state.value.zoomTarget ?: _state.value.zoom) * factor)

    fun zoomStep(zoomIn: Boolean) = zoomBy(if (zoomIn) ZOOM_STEP else 1f / ZOOM_STEP)

    fun resetZoom() = setZoom(1f)

    private fun setZoom(ratio: Float) = _state.update {
        if (it.zoomMax <= it.zoomMin) it else it.copy(zoomTarget = ratio.coerceIn(it.zoomMin, it.zoomMax))
    }

    // ---- torch brightness ------------------------------------------------------------------------

    fun onTorchLevel(max: Int, level: Int) =
        _state.update { it.copy(torchMaxLevel = max.coerceAtLeast(1), torchLevel = level.coerceIn(1, max.coerceAtLeast(1))) }

    fun setTorchFraction(fraction: Float) {
        val max = _state.value.torchMaxLevel
        setTorchLevel(1 + (fraction.coerceIn(0f, 1f) * (max - 1)).roundToInt())
    }

    fun stepTorch(up: Boolean) {
        val s = _state.value
        val step = (s.torchMaxLevel / 10).coerceAtLeast(1)
        setTorchLevel((s.torchLevelTarget ?: s.torchLevel) + if (up) step else -step)
    }

    private fun setTorchLevel(level: Int) {
        val max = _state.value.torchMaxLevel
        if (max <= 1) {
            toast("TORCH BRIGHTNESS IS FIXED ON THIS DEVICE")
            return
        }
        val l = level.coerceIn(1, max)
        _state.update { it.copy(torchLevelTarget = l, torchLevel = l) }
    }

    // ---- focus -----------------------------------------------------------------------------------

    private var focusSeq = 0

    /** Long-press: focus (and expose) on that spot and hold it there. */
    fun focusAt(point: Offset) {
        focusSeq++
        val id = focusSeq
        _state.update {
            it.copy(
                manualFocus = null,
                focusRequest = FocusRequest(id, point.x, point.y),
                focusMarker = FocusMarker(id, point.x, point.y, FocusStatus.FOCUSING),
            )
        }
    }

    fun onFocusResult(id: Int, success: Boolean) {
        if (id != focusSeq) return
        _state.update { s ->
            s.focusMarker?.takeIf { it.id == id }?.let { s.copy(focusMarker = it.copy(status = if (success) FocusStatus.LOCKED else FocusStatus.FAILED)) } ?: s
        }
        if (!success) viewModelScope.launch {
            delay(1_800)
            _state.update { s -> if (s.focusMarker?.id == id) s.copy(focusMarker = null) else s }
        }
    }

    val manualFocusSupported: Boolean get() = (_state.value.optics.minFocusDiopters ?: 0f) > 0f

    fun toggleFocusStrip() {
        val open = !_state.value.focusStrip
        if (open && !manualFocusSupported) toast("MANUAL FOCUS NOT AVAILABLE · LONG-PRESS TO FOCUS")
        _state.update { it.copy(focusStrip = open && manualFocusSupported) }
        if (open && manualFocusSupported) toast("LONG-PRESS ANYWHERE TO FOCUS & LOCK")
    }

    /** Manual focus from the strip: 0 = nearest the lens allows, 1 = infinity. */
    fun setManualFocus(fraction: Float) {
        if (!manualFocusSupported) return
        _state.update { it.copy(manualFocus = fraction.coerceIn(0f, 1f), focusRequest = null, focusMarker = null) }
    }

    fun stepFocus(farther: Boolean) = setManualFocus((_state.value.manualFocus ?: 1f) + if (farther) 0.05f else -0.05f)

    /** Back to continuous autofocus (releases a long-press lock and any manual distance). */
    fun resetFocus() = _state.update {
        it.copy(manualFocus = null, focusRequest = null, focusMarker = null, focusResetSeq = it.focusResetSeq + 1)
    }

    /** "0.35 M", "∞" or "AUTO" for the focus strip. */
    fun focusLabel(s: HudState): String {
        val t = s.manualFocus ?: return if (s.focusMarker?.status == FocusStatus.LOCKED) "AF-L" else "AUTO"
        val minD = s.optics.minFocusDiopters ?: return "AUTO"
        val d = minD * (1 - t) * (1 - t)
        return if (d < 0.02f) "∞" else String.format(Locale.US, "%.2f M", 1 / d)
    }

    // ---- flashlight -------------------------------------------------------------------------------

    fun toggleTorch() = _state.update { it.copy(torch = !it.torch) }

    /** The camera reports what the torch is really doing (it switches off when the app is backgrounded). */
    fun onTorchState(on: Boolean) = _state.update { if (it.torch == on) it else it.copy(torch = on) }

    fun onTorchUnavailable() {
        _state.update { it.copy(torch = false) }
        toast("NO FLASHLIGHT ON THIS CAMERA")
    }

    fun dismiss() {
        suppressedId = _state.value.lock?.targetId
        dwellId = null
        scanJob?.cancel()
        jarvis.stop()
        _state.update { it.copy(lock = null, panel = null) }
    }

    fun rescan() {
        val lock = _state.value.lock ?: return
        lock.targetId?.let { cache.remove(it) }
        lockOn(lock.targetId, lock.box, null)
    }

    private fun squareAround(p: Offset, fraction: Float): Rect {
        val half = min(viewW, viewH) * fraction / 2f
        return Rect(p.x - half, p.y - half, p.x + half, p.y + half)
    }

    private fun lockOn(targetId: Int?, box: Rect, category: String?) {
        scanJob?.cancel()
        jarvis.stop()

        targetId?.let { cache[it] }?.let { cached ->
            _state.update { it.copy(lock = LockUi(targetId, box, LockPhase.LOCKED), panel = cached.copy(fromCache = true)) }
            if (settings.value.voice) jarvis.speak(cached.brief)
            return
        }

        val frame = frameSource?.invoke()
        if (frame == null || viewW == 0) {
            toast("CAMERA NOT READY")
            return
        }
        _state.update { it.copy(lock = LockUi(targetId, box, LockPhase.ANALYZING), panel = PanelUi()) }

        scanJob = viewModelScope.launch {
            try {
                val prepared = withContext(Dispatchers.Default) { ImagePrep.prepare(frame, box, viewW, viewH) }
                var quickName: String? = category
                updatePanel { it.copy(status = "ANALYZING", prelim = category?.uppercase(Locale.US)) }

                // The upload starts right away; the offline guess and the wireframe fill in alongside it.
                launch {
                    val wire = withContext(Dispatchers.Default) { EdgeWireframe.render(prepared.crop) }
                    updatePanel { it.copy(wireframe = wire.asImageBitmap()) }
                }
                launch {
                    labeler.label(prepared.crop)?.let { q ->
                        quickName = q.text
                        updatePanel { it.copy(prelim = "${q.text.uppercase(Locale.US)} · ${(q.confidence * 100).toInt()}%") }
                    }
                }
                identifier.identify(prepared.jpeg, category).collect { event ->
                    handle(event, targetId, quickName)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updatePanel { it.copy(streaming = false, error = "SCAN FAULT: ${e.message ?: e.javaClass.simpleName}") }
                _state.update { s -> s.copy(lock = s.lock?.copy(phase = LockPhase.OFFLINE)) }
            }
        }
    }

    private fun handle(event: ScanEvent, targetId: Int?, fallbackName: String?) {
        when (event) {
            is ScanEvent.Attempt -> {
                _state.update {
                    it.copy(uplink = it.uplink.copy(provider = event.provider, keyIndex = event.keyIndex, keyCount = event.keyCount))
                }
                updatePanel { it.copy(status = "UPLINK ${event.provider.display} · KEY ${event.keyIndex}/${event.keyCount}") }
            }
            is ScanEvent.Partial -> updatePanel { it.copy(brief = event.brief, status = "RECEIVING") }
            is ScanEvent.KeySwitched -> updatePanel {
                it.copy(status = "${event.provider.display} ${event.reason.substringBefore(" —")} · SWITCHING KEY")
            }
            is ScanEvent.Done -> {
                val panel = (_state.value.panel ?: PanelUi()).copy(
                    brief = event.brief,
                    streaming = false,
                    status = "TARGET IDENTIFIED",
                    source = "${event.provider.display} · ${event.model} · ${event.latencyMs} MS",
                    error = null,
                )
                targetId?.let { cache[it] = panel }
                _state.update { s -> s.copy(panel = panel, lock = s.lock?.copy(phase = LockPhase.LOCKED)) }
                if (settings.value.voice) jarvis.speak(event.brief)
            }
            is ScanEvent.Failed -> {
                val retry = event.retryAtMs?.let { at ->
                    val secs = ((at - System.currentTimeMillis()) / 1000).coerceAtLeast(1)
                    " · RETRY IN ${formatSeconds(secs)}"
                }.orEmpty()
                updatePanel {
                    it.copy(
                        brief = if (it.brief.isUsable) it.brief else Brief(
                            name = fallbackName?.replaceFirstChar { c -> c.titlecase(Locale.US) } ?: "Unknown object",
                            type = "ON-DEVICE ESTIMATE",
                            summary = "Cloud analysis unavailable; showing the on-device classification only.",
                        ),
                        streaming = false,
                        status = "UPLINK LOST",
                        error = event.reason + retry,
                    )
                }
                _state.update { s -> s.copy(lock = s.lock?.copy(phase = LockPhase.OFFLINE)) }
            }
        }
    }

    private inline fun updatePanel(crossinline f: (PanelUi) -> PanelUi) {
        _state.update { s -> s.copy(panel = f(s.panel ?: PanelUi())) }
    }

    fun toast(message: String) {
        _state.update { it.copy(toast = message) }
        viewModelScope.launch {
            delay(2_200)
            _state.update { if (it.toast == message) it.copy(toast = null) else it }
        }
    }

    // ---- vault & settings -------------------------------------------------------------------------

    fun addKey(provider: ProviderId, secret: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val added = keyRepo.add(provider, secret)
            onResult(if (added) "KEY STORED" else if (secret.isBlank()) "PASTE A KEY FIRST" else "KEY ALREADY IN VAULT")
        }
    }

    fun removeKey(id: String) {
        viewModelScope.launch { keyRepo.remove(id) }
    }

    fun resetKey(id: String) = pool.reset(id)

    fun testKey(entry: ApiKeyEntry, onResult: (KeyTestResult) -> Unit) {
        viewModelScope.launch { onResult(identifier.testKey(entry)) }
    }

    fun setVoice(on: Boolean) {
        if (!on) jarvis.stop()
        viewModelScope.launch { settingsRepo.setVoice(on) }
    }

    fun setTint(on: Boolean) = viewModelScope.launch { settingsRepo.setTint(on) }
    fun setAutoLock(on: Boolean) = viewModelScope.launch { settingsRepo.setAutoLock(on) }

    fun setModel(provider: ProviderId, model: String) {
        identifier.models.invalidate(provider)
        viewModelScope.launch { settingsRepo.setModel(provider, model) }
    }

    fun moveProviderUp(provider: ProviderId) {
        val order = settings.value.providerOrder.toMutableList()
        val i = order.indexOf(provider)
        if (i > 0) {
            order.removeAt(i)
            order.add(i - 1, provider)
            viewModelScope.launch { settingsRepo.setOrder(order) }
        }
    }

    fun activeModel(provider: ProviderId): String? = identifier.models.cached(provider)

    override fun onCleared() {
        jarvis.shutdown()
        labeler.close()
        super.onCleared()
    }

    companion object {
        const val DWELL_MS = 800L
        const val AUTO_SCAN_GAP_MS = 2_500L
        const val KEEP_ALIVE_MS = 50_000L
        const val SCAN_REGION = 0.42f
        const val ZOOM_STEP = 1.5f
        const val PLANE_TAP_RADIUS_PX = 90f

        fun formatSeconds(s: Long): String = when {
            s >= 3600 -> "${s / 3600}H ${(s % 3600) / 60}M"
            s >= 60 -> "${s / 60}M ${s % 60}S"
            else -> "${s}S"
        }
    }
}
