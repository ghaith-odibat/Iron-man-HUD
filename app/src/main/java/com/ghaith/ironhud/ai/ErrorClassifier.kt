package com.ghaith.ironhud.ai

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Why a provider call failed, reduced to what the key pool needs to know. */
sealed interface CallFailure {
    val message: String

    /** Quota hit. [daily] means the per-day allowance is gone, not just the per-minute one. */
    data class RateLimited(override val message: String, val retryAfterMs: Long?, val daily: Boolean) : CallFailure

    /** The key itself is wrong, revoked or not allowed to use the API. */
    data class InvalidKey(override val message: String) : CallFailure

    /** The model id doesn't exist (any more) for this provider. */
    data class ModelUnavailable(override val message: String) : CallFailure

    /** The provider rejected the request itself (region, safety block, unsupported input); other keys would fail too. */
    data class BadRequest(override val message: String) : CallFailure

    /** Network trouble or a 5xx; nothing wrong with the key. */
    data class Transient(override val message: String) : CallFailure
}

object ErrorClassifier {

    fun classify(
        provider: ProviderId,
        httpCode: Int,
        header: (String) -> String?,
        body: String,
        nowMs: Long,
    ): CallFailure {
        val root = runCatching { JsonTools.json.parseToJsonElement(body) }.getOrNull()
        val message = extractMessage(root).ifBlank { body.take(160).ifBlank { "HTTP $httpCode" } }.take(300)
        val haystack = (message + " " + body.take(4000)).lowercase()
        val retryAfter = retryAfterMs(header, root, message, nowMs)

        val rateLimited = httpCode == 429 ||
            "resource_exhausted" in haystack ||
            "rate_limit_exceeded" in haystack ||
            "rate limit" in haystack
        return when {
            rateLimited -> CallFailure.RateLimited(message, retryAfter, isDaily(haystack))
            // OpenRouter: free credits used up.
            httpCode == 402 -> CallFailure.RateLimited(message, retryAfter, daily = true)
            httpCode == 401 || looksLikeBadKey(haystack) -> CallFailure.InvalidKey(message)
            httpCode == 403 -> CallFailure.InvalidKey(message)
            httpCode == 404 || looksLikeMissingModel(haystack) -> CallFailure.ModelUnavailable(message)
            httpCode == 408 || httpCode == 409 || httpCode >= 500 -> CallFailure.Transient(message)
            httpCode in 400..499 -> CallFailure.BadRequest(message)
            else -> CallFailure.Transient(message)
        }
    }

    /** Classifies an `error` object that arrived inside a 200 stream. */
    fun fromErrorJson(provider: ProviderId, error: JsonElement, nowMs: Long): CallFailure {
        val code = ((error as? JsonObject)?.get("code") as? JsonPrimitive)?.intOrNull ?: 0
        val body = JsonObject(mapOf("error" to error)).toString()
        return classify(provider, if (code in 100..599) code else 500, { null }, body, nowMs)
    }

    private fun looksLikeBadKey(s: String) =
        "api_key_invalid" in s || "api key not valid" in s || "invalid_api_key" in s ||
            "invalid api key" in s || "api key expired" in s || "no auth credentials" in s ||
            "user not found" in s

    private fun looksLikeMissingModel(s: String) =
        "model_not_found" in s || "model_decommissioned" in s || "decommissioned" in s ||
            "is not found for api version" in s || "not supported for generatecontent" in s ||
            "does not exist" in s && "model" in s

    private fun isDaily(s: String) =
        "per day" in s || "perday" in s || "per-day" in s || "daily" in s ||
            "(rpd)" in s || "(tpd)" in s || " rpd" in s || " tpd" in s

    private fun extractMessage(root: JsonElement?): String {
        val error = (root as? JsonObject)?.get("error") ?: return ""
        if (error is JsonPrimitive) return error.contentOrNull.orEmpty()
        return ((error as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull.orEmpty()
    }

    private fun retryAfterMs(header: (String) -> String?, root: JsonElement?, message: String, nowMs: Long): Long? {
        header("retry-after")?.trim()?.toDoubleOrNull()?.let { return (it * 1000).toLong() }

        // Gemini: error.details[].retryDelay = "43s"
        val details = ((root as? JsonObject)?.get("error") as? JsonObject)?.get("details")
        if (details is kotlinx.serialization.json.JsonArray) {
            for (d in details) {
                val delay = ((d as? JsonObject)?.get("retryDelay") as? JsonPrimitive)?.contentOrNull
                if (delay != null) parseDuration(delay)?.let { return it }
            }
        }

        // OpenRouter: error.metadata.headers["X-RateLimit-Reset"] = epoch millis
        val reset = (((root as? JsonObject)?.get("error") as? JsonObject)
            ?.get("metadata") as? JsonObject)
            ?.get("headers")?.let { it as? JsonObject }
            ?.get("X-RateLimit-Reset")?.let { (it as? JsonPrimitive)?.contentOrNull?.toLongOrNull() }
        if (reset != null && reset > nowMs) return reset - nowMs

        // Groq: "... Please try again in 1m26.4s."
        Regex("""try again in ([0-9hms.]+)""", RegexOption.IGNORE_CASE).find(message)?.let { m ->
            parseDuration(m.groupValues[1].trimEnd('.'))?.let { return it }
        }
        return null
    }

    /** Parses "43s", "0.5s", "750ms", "1m26.4s", "2h3m" into milliseconds. */
    fun parseDuration(text: String): Long? {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return null
        Regex("""^(\d+(?:\.\d+)?)ms$""").matchEntire(t)?.let { return it.groupValues[1].toDouble().toLong() }
        val m = Regex("""^(?:(\d+(?:\.\d+)?)h)?(?:(\d+(?:\.\d+)?)m)?(?:(\d+(?:\.\d+)?)s)?$""").matchEntire(t)
            ?: return null
        if (m.groupValues.drop(1).all { it.isEmpty() }) return null
        val h = m.groupValues[1].toDoubleOrNull() ?: 0.0
        val min = m.groupValues[2].toDoubleOrNull() ?: 0.0
        val s = m.groupValues[3].toDoubleOrNull() ?: 0.0
        return ((h * 3600 + min * 60 + s) * 1000).toLong()
    }
}
