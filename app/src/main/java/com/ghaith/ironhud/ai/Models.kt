package com.ghaith.ironhud.ai

/** The AI back-ends the HUD can use. All three have free tiers with vision (image) input. */
enum class ProviderId(val display: String, val defaultModel: String) {
    GEMINI("GEMINI", ModelResolver.AUTO),
    GROQ("GROQ", "meta-llama/llama-4-scout-17b-16e-instruct"),
    OPENROUTER("OPENROUTER", "openrouter/free"),
}

/** One user-supplied API key. [secret] never leaves the device except in the provider's auth header. */
data class ApiKeyEntry(val id: String, val provider: ProviderId, val secret: String) {
    val masked: String
        get() = if (secret.length <= 10) "••••" else secret.take(4) + "••••" + secret.takeLast(4)

    override fun toString() = "ApiKeyEntry($id, $provider, $masked)"
}

/** Base URLs, overridable so tests can point every provider at a local mock server. */
data class Endpoints(
    val gemini: String = "https://generativelanguage.googleapis.com",
    val groq: String = "https://api.groq.com/openai/v1",
    val openRouter: String = "https://openrouter.ai/api/v1",
)

/** The structured brief shown in the HUD panel, parsed progressively from the model's stream. */
data class Brief(
    val name: String = "",
    val type: String = "",
    val confidence: String = "",
    val summary: String = "",
    val facts: List<String> = emptyList(),
) {
    val isUsable: Boolean get() = name.isNotBlank()
}

/** Progress of one identification, as emitted by [IdentifyService.identify]. */
sealed interface ScanEvent {
    data class Attempt(val provider: ProviderId, val keyIndex: Int, val keyCount: Int, val model: String) : ScanEvent
    data class Partial(val brief: Brief) : ScanEvent
    data class KeySwitched(val provider: ProviderId, val reason: String) : ScanEvent
    data class Done(val brief: Brief, val provider: ProviderId, val model: String, val latencyMs: Long) : ScanEvent
    data class Failed(val reason: String, val retryAtMs: Long?) : ScanEvent
}

object Prompt {
    const val SYSTEM = """You are J.A.R.V.I.S., the heads-up display AI inside an Iron Man suit.
The image is a crop centred on the object the user is looking at. Identify that main object.
Be as specific as you can: brand and model, species, landmark or artwork name when recognisable; otherwise the precise common name.
Reply in EXACTLY this format, plain text, no markdown, nothing else:
NAME: <specific name, max 5 words>
TYPE: <category, max 3 words>
CONFIDENCE: <high|medium|low>
BRIEF: <one or two crisp sentences, max 40 words>
- <useful fact, max 12 words>
- <useful fact, max 12 words>
- <useful fact, max 12 words>"""

    fun user(hint: String?): String =
        if (hint.isNullOrBlank()) "Identify the object."
        else "Identify the object. On-device sensor guess: \"$hint\" (may be wrong or too generic)."
}
