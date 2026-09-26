package com.ghaith.ironhud.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import java.io.IOException

data class KeyTestResult(val ok: Boolean, val message: String)

/**
 * Sends a cropped object image to the first healthy key and streams the brief back.
 * When a key is rate-limited or broken it is benched in the [KeyPool] and the same image is
 * retried on the next key / provider straight away, so a limit costs one round-trip, not a scan.
 */
class IdentifyService(
    private val pool: KeyPool,
    private val http: OkHttpClient,
    endpoints: Endpoints = Endpoints(),
    modelOverrides: () -> Map<ProviderId, String> = { emptyMap() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val providers: Map<ProviderId, VisionProvider> = mapOf(
        ProviderId.GEMINI to GeminiProvider(endpoints.gemini),
        ProviderId.GROQ to OpenAiCompatProvider(ProviderId.GROQ, endpoints.groq),
        ProviderId.OPENROUTER to OpenAiCompatProvider(ProviderId.OPENROUTER, endpoints.openRouter),
    )
    val models = ModelResolver(http, endpoints, modelOverrides)

    fun identify(jpeg: ByteArray, hint: String?): Flow<ScanEvent> = channelFlow {
        if (pool.isEmpty()) {
            send(ScanEvent.Failed("NO API KEYS — OPEN THE VAULT", null))
            return@channelFlow
        }
        val tried = mutableSetOf<String>()
        val skipProviders = mutableSetOf<ProviderId>()
        var lastProblem: String? = null
        val userText = Prompt.user(hint)

        repeat(MAX_ATTEMPTS) {
            val key = pool.acquire(tried, skipProviders) ?: run {
                send(ScanEvent.Failed(lastProblem ?: "ALL KEYS COOLING DOWN", pool.nextRecoveryAt()))
                return@channelFlow
            }
            tried += key.id
            val provider = providers.getValue(key.provider)
            val model = models.modelFor(key)
            val (idx, count) = pool.positionOf(key)
            send(ScanEvent.Attempt(key.provider, idx, count, model))

            val started = clock()
            val text = StringBuilder()
            val failure = runAttempt(provider, key.secret, model, jpeg, userText) { delta ->
                text.append(delta)
                send(ScanEvent.Partial(BriefParser.parse(text.toString())))
            }
            val brief = BriefParser.parse(text.toString())
            val complete = brief.isUsable && brief.summary.isNotBlank()

            if (failure == null && brief.isUsable || failure != null && complete) {
                // A stream that died after delivering a full brief still counts as a success.
                pool.reportSuccess(key.id)
                send(ScanEvent.Done(brief, key.provider, model, clock() - started))
                return@channelFlow
            }

            val problem = failure ?: CallFailure.Transient("Empty answer from model")
            pool.reportFailure(key, problem)
            when (problem) {
                is CallFailure.ModelUnavailable -> {
                    models.invalidate(key.provider)
                    skipProviders += key.provider
                }
                is CallFailure.BadRequest -> skipProviders += key.provider
                else -> Unit
            }
            lastProblem = "${key.provider.display}: ${shortReason(problem)}"
            send(ScanEvent.KeySwitched(key.provider, shortReason(problem)))
        }
        send(ScanEvent.Failed(lastProblem ?: "NO RESPONSE", pool.nextRecoveryAt()))
    }

    /** Returns null on a clean stream, otherwise why it failed. */
    private suspend fun runAttempt(
        provider: VisionProvider,
        apiKey: String,
        model: String,
        jpeg: ByteArray,
        userText: String,
        onDelta: suspend (String) -> Unit,
    ): CallFailure? = try {
        http.newCall(provider.streamRequest(apiKey, model, jpeg, userText)).executeAndUse { resp ->
            if (!resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                ErrorClassifier.classify(provider.id, resp.code, { resp.header(it) }, body, clock())
            } else {
                var failure: CallFailure? = null
                val source = resp.body?.source()
                if (source != null) {
                    for (data in source.sseData()) {
                        when (val chunk = provider.parseChunk(data, clock())) {
                            is Chunk.Text -> onDelta(chunk.text)
                            is Chunk.Error -> {
                                failure = chunk.failure
                                break
                            }
                            Chunk.Done -> break
                            Chunk.Skip -> Unit
                        }
                    }
                }
                failure
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        CallFailure.Transient(e.message ?: "Network error")
    } catch (e: SerializationException) {
        CallFailure.Transient("Unreadable response")
    }

    /** Validates a key with a request that costs no generation quota. */
    suspend fun testKey(entry: ApiKeyEntry): KeyTestResult {
        val provider = providers.getValue(entry.provider)
        return try {
            http.newCall(provider.probeRequest(entry.secret)).executeAndUse { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    pool.reset(entry.id)
                    val detail = when (entry.provider) {
                        ProviderId.GEMINI -> ModelResolver.pickGeminiModel(ModelResolver.parseGeminiModels(body))
                            ?.also { models.remember(ProviderId.GEMINI, it) }
                            ?.let { "model $it" } ?: "no Flash-Lite model visible"
                        else -> "key accepted"
                    }
                    KeyTestResult(true, "ONLINE · $detail")
                } else {
                    val failure = ErrorClassifier.classify(entry.provider, resp.code, { resp.header(it) }, body, clock())
                    pool.reportFailure(entry, failure)
                    KeyTestResult(false, shortReason(failure))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            KeyTestResult(false, "Network error: ${e.message ?: "unreachable"}")
        }
    }

    /** Opens TLS connections (and resolves the Gemini model) before the first scan needs them. */
    suspend fun warmUp() {
        for (provider in ProviderId.entries) {
            val key = pool.view.value.rows.firstOrNull {
                it.entry.provider == provider && it.status.health == KeyHealth.READY
            }?.entry ?: continue
            try {
                models.modelFor(key)
                http.newCall(providers.getValue(provider).probeRequest(key.secret)).executeAndUse { it.body?.close() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Warm-up is best effort.
            }
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 6

        fun shortReason(f: CallFailure): String = when (f) {
            is CallFailure.RateLimited -> if (f.daily) "DAILY LIMIT REACHED" else "RATE LIMITED"
            is CallFailure.InvalidKey -> "KEY REJECTED — ${f.message.take(80)}"
            is CallFailure.ModelUnavailable -> "MODEL UNAVAILABLE — ${f.message.take(80)}"
            is CallFailure.BadRequest -> "REQUEST REFUSED — ${f.message.take(80)}"
            is CallFailure.Transient -> "LINK UNSTABLE — ${f.message.take(60)}"
        }
    }
}
