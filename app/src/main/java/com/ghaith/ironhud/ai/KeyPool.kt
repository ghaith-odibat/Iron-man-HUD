package com.ghaith.ironhud.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

enum class KeyHealth { READY, COOLING, EXHAUSTED, INVALID }

@Serializable
data class KeyStatus(
    val health: KeyHealth = KeyHealth.READY,
    /** When COOLING/EXHAUSTED ends (epoch millis). */
    val until: Long = 0,
    val note: String? = null,
    val successes: Int = 0,
    val failures: Int = 0,
)

data class KeyRow(val entry: ApiKeyEntry, val status: KeyStatus, val indexInProvider: Int, val providerCount: Int)

data class PoolView(val rows: List<KeyRow> = emptyList(), val lastUsed: ApiKeyEntry? = null) {
    val readyCount: Int get() = rows.count { it.status.health == KeyHealth.READY }
}

/**
 * All API keys across providers, with rate-limit bookkeeping.
 *
 * - [acquire] walks providers in the user's order and round-robins between that provider's healthy
 *   keys, which spreads per-minute limits across keys.
 * - [reportFailure] benches a key that hit its limit (until the provider says it recovers) or is
 *   invalid, so the next [acquire] switches to another key automatically.
 */
class KeyPool(private val clock: () -> Long = System::currentTimeMillis) {

    private var entries: List<ApiKeyEntry> = emptyList()
    private var order: List<ProviderId> = ProviderId.entries
    private val status = HashMap<String, KeyStatus>()
    private val cursor = HashMap<ProviderId, Int>()
    private var lastUsed: ApiKeyEntry? = null

    private val _view = MutableStateFlow(PoolView())
    val view: StateFlow<PoolView> = _view.asStateFlow()

    @Synchronized
    fun configure(
        keys: List<ApiKeyEntry>,
        providerOrder: List<ProviderId> = ProviderId.entries,
        persisted: Map<String, KeyStatus> = emptyMap(),
    ) {
        entries = keys
        order = (providerOrder + ProviderId.entries).distinct()
        val ids = keys.map { it.id }.toSet()
        status.keys.retainAll(ids)
        for (k in keys) if (k.id !in status) status[k.id] = persisted[k.id] ?: KeyStatus()
        if (lastUsed?.id !in ids) lastUsed = null
        publish()
    }

    @Synchronized
    fun isEmpty(): Boolean = entries.isEmpty()

    @Synchronized
    fun statusOf(id: String): KeyStatus = refreshed(status[id] ?: KeyStatus())

    /** The next usable key, or null when every key is benched / excluded. */
    @Synchronized
    fun acquire(
        excludeIds: Set<String> = emptySet(),
        excludeProviders: Set<ProviderId> = emptySet(),
    ): ApiKeyEntry? {
        for (provider in order) {
            if (provider in excludeProviders) continue
            val ofProvider = entries.filter { it.provider == provider }
            if (ofProvider.isEmpty()) continue
            val start = cursor[provider] ?: 0
            for (step in ofProvider.indices) {
                val candidate = ofProvider[(start + step) % ofProvider.size]
                if (candidate.id in excludeIds) continue
                if (statusOf(candidate.id).health != KeyHealth.READY) continue
                cursor[provider] = (start + step + 1) % ofProvider.size
                lastUsed = candidate
                publish()
                return candidate
            }
        }
        return null
    }

    @Synchronized
    fun reportSuccess(id: String) {
        val s = statusOf(id)
        status[id] = s.copy(health = KeyHealth.READY, until = 0, note = null, successes = s.successes + 1)
        publish()
    }

    @Synchronized
    fun reportFailure(entry: ApiKeyEntry, failure: CallFailure) {
        val now = clock()
        val s = statusOf(entry.id)
        status[entry.id] = when (failure) {
            is CallFailure.RateLimited -> if (failure.daily) {
                s.copy(health = KeyHealth.EXHAUSTED, until = dailyResetAt(entry.provider, failure.retryAfterMs, now),
                    note = failure.message, failures = s.failures + 1)
            } else {
                val wait = (failure.retryAfterMs ?: DEFAULT_COOLDOWN_MS).coerceIn(MIN_COOLDOWN_MS, MAX_COOLDOWN_MS)
                s.copy(health = KeyHealth.COOLING, until = now + wait, note = failure.message, failures = s.failures + 1)
            }
            is CallFailure.InvalidKey ->
                s.copy(health = KeyHealth.INVALID, until = 0, note = failure.message, failures = s.failures + 1)
            else -> s.copy(failures = s.failures + 1, note = failure.message)
        }
        publish()
    }

    /** Puts a key back in rotation (Vault "reset" or a successful key test). */
    @Synchronized
    fun reset(id: String) {
        val s = status[id] ?: return
        status[id] = s.copy(health = KeyHealth.READY, until = 0, note = null)
        publish()
    }

    /** Earliest moment a benched key becomes usable again, or null if none will. */
    @Synchronized
    fun nextRecoveryAt(): Long? = entries.mapNotNull { e ->
        statusOf(e.id).takeIf { it.health == KeyHealth.COOLING || it.health == KeyHealth.EXHAUSTED }?.until
    }.minOrNull()

    @Synchronized
    fun exportStatus(): Map<String, KeyStatus> = entries.associate { it.id to statusOf(it.id) }

    @Synchronized
    fun positionOf(entry: ApiKeyEntry): Pair<Int, Int> {
        val same = entries.filter { it.provider == entry.provider }
        return (same.indexOfFirst { it.id == entry.id } + 1) to same.size
    }

    /** Refreshes derived state (cool-downs that expired) for observers. */
    @Synchronized
    fun tick() = publish()

    private fun refreshed(s: KeyStatus): KeyStatus =
        if ((s.health == KeyHealth.COOLING || s.health == KeyHealth.EXHAUSTED) && clock() >= s.until) {
            s.copy(health = KeyHealth.READY, until = 0, note = null)
        } else {
            s
        }

    private fun publish() {
        _view.value = PoolView(
            rows = entries.map { e ->
                val (idx, count) = positionOf(e)
                KeyRow(e, statusOf(e.id), idx, count)
            },
            lastUsed = lastUsed,
        )
    }

    private fun dailyResetAt(provider: ProviderId, retryAfterMs: Long?, now: Long): Long = when (provider) {
        // Gemini's daily free quota resets at midnight Pacific time.
        ProviderId.GEMINI -> ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), PACIFIC)
            .toLocalDate().plusDays(1).atStartOfDay(PACIFIC).toInstant().toEpochMilli()
        // Groq / OpenRouter use rolling windows and usually say when to come back.
        else -> now + (retryAfterMs ?: DEFAULT_DAILY_COOLDOWN_MS).coerceIn(MIN_COOLDOWN_MS, DAY_MS)
    }

    companion object {
        private val PACIFIC: ZoneId = ZoneId.of("America/Los_Angeles")
        const val DEFAULT_COOLDOWN_MS = 60_000L
        const val MIN_COOLDOWN_MS = 5_000L
        const val MAX_COOLDOWN_MS = 60 * 60_000L
        const val DEFAULT_DAILY_COOLDOWN_MS = 60 * 60_000L
        const val DAY_MS = 24 * 60 * 60_000L
    }
}
