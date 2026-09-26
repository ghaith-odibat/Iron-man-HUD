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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.min

class HudViewModel(app: Application) : AndroidViewModel(app) {

    private val keyRepo = SecureKeyRepository(app)
    private val settingsRepo = SettingsRepository(app)
    private val pool = KeyPool()
    private val modelOverrides = MutableStateFlow<Map<ProviderId, String>>(emptyMap())
    private val identifier = IdentifyService(pool, Http.newClient(), modelOverrides = { modelOverrides.value })
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
            while (isActive) {
                pool.tick()
                delay(1_000)
            }
        }
    }

    // ---- lifecycle -----------------------------------------------------------------------------

    /** While the HUD is on screen, keep TLS connections warm so a lock doesn't pay for a handshake. */
    fun onForeground(foreground: Boolean) {
        keepAliveJob?.cancel()
        if (!foreground) {
            jarvis.stop()
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
        val center = Offset(viewW / 2f, viewH / 2f)
        val hit = _state.value.targets.filter { it.box.contains(center) }.minByOrNull { it.area }
        if (hit != null) lockOn(hit.id, hit.box, hit.category) else lockOn(null, squareAround(center, SCAN_REGION), null)
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

        fun formatSeconds(s: Long): String = when {
            s >= 3600 -> "${s / 3600}H ${(s % 3600) / 60}M"
            s >= 60 -> "${s / 60}M ${s % 60}S"
            else -> "${s}S"
        }
    }
}
