package com.ghaith.ironhud.ai

import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Decides which model each provider uses. Gemini defaults to "auto": the key's model list is
 * queried once and the newest stable Flash-Lite (the fastest free vision tier) is picked, so the
 * app keeps working when Google retires a model.
 */
class ModelResolver(
    private val http: OkHttpClient,
    private val endpoints: Endpoints,
    private val overrides: () -> Map<ProviderId, String>,
) {
    private val cache = ConcurrentHashMap<ProviderId, String>()

    suspend fun modelFor(key: ApiKeyEntry): String {
        overrides()[key.provider]?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals(AUTO, ignoreCase = true) }
            ?.let { return it }
        cache[key.provider]?.let { return it }
        val model = when (key.provider) {
            ProviderId.GEMINI -> discoverGemini(key.secret) ?: GEMINI_FALLBACK
            else -> key.provider.defaultModel
        }
        cache[key.provider] = model
        return model
    }

    fun cached(provider: ProviderId): String? = cache[provider]

    fun invalidate(provider: ProviderId) {
        cache.remove(provider)
    }

    /** Stores a discovery result obtained elsewhere (e.g. from a key test). */
    fun remember(provider: ProviderId, model: String) {
        cache[provider] = model
    }

    private suspend fun discoverGemini(apiKey: String): String? = try {
        http.newCall(GeminiProvider.modelsRequest(endpoints.gemini, apiKey)).executeAndUse { resp ->
            if (!resp.isSuccessful) null else pickGeminiModel(parseGeminiModels(resp.body?.string().orEmpty()))
        }
    } catch (e: IOException) {
        null
    }

    companion object {
        const val AUTO = "auto"
        const val GEMINI_FALLBACK = "gemini-3.1-flash-lite"

        /** Model ids (without the "models/" prefix) that support generateContent. */
        fun parseGeminiModels(body: String): List<String> {
            val root = runCatching { JsonTools.json.parseToJsonElement(body) }.getOrNull().obj() ?: return emptyList()
            return root["models"].arr().orEmpty().mapNotNull { m ->
                val o = m.obj() ?: return@mapNotNull null
                val methods = o["supportedGenerationMethods"].arr()?.mapNotNull { it.str() }.orEmpty()
                val name = o["name"].str() ?: return@mapNotNull null
                if (methods.isNotEmpty() && "generateContent" !in methods) null else name.removePrefix("models/")
            }
        }

        private val STABLE = Regex("""^gemini-(\d+)(?:\.(\d+))?-flash-lite$""")
        private val PREVIEW = Regex("""^gemini-(\d+)(?:\.(\d+))?-flash-lite-preview(?:-[0-9-]+)?$""")

        fun pickGeminiModel(ids: List<String>): String? {
            fun best(re: Regex): String? = ids.mapNotNull { id ->
                re.matchEntire(id)?.let { m ->
                    Triple(id, m.groupValues[1].toInt(), m.groupValues[2].toIntOrNull() ?: 0)
                }
            }.sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }
                .thenByDescending { it.third }
                .thenByDescending { it.first })
                .firstOrNull()?.first

            return best(STABLE)
                ?: best(PREVIEW)
                ?: ids.firstOrNull { it == "gemini-flash-lite-latest" }
                ?: ids.filter { "flash-lite" in it && listOf("tts", "image", "audio", "live").none { x -> x in it } }
                    .maxOrNull()
                ?: ids.firstOrNull { it == "gemini-flash-latest" }
        }
    }
}
