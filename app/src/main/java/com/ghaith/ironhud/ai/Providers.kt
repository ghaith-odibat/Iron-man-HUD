package com.ghaith.ironhud.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

/** One parsed SSE event from a provider stream. */
sealed interface Chunk {
    data class Text(val text: String) : Chunk
    data class Error(val failure: CallFailure) : Chunk
    data object Done : Chunk
    data object Skip : Chunk
}

interface VisionProvider {
    val id: ProviderId

    /** A streaming "identify this image" request. */
    fun streamRequest(apiKey: String, model: String, jpeg: ByteArray, userText: String): Request

    /** A cheap authenticated request that proves the key works without spending generate quota. */
    fun probeRequest(apiKey: String): Request

    fun parseChunk(data: String, nowMs: Long): Chunk
}

private fun ByteArray.base64(): String = Base64.getEncoder().encodeToString(this)

private const val MAX_OUTPUT_TOKENS = 512

class GeminiProvider(private val baseUrl: String) : VisionProvider {
    override val id = ProviderId.GEMINI

    override fun streamRequest(apiKey: String, model: String, jpeg: ByteArray, userText: String): Request {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", Prompt.SYSTEM) } }
            }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", "image/jpeg")
                                put("data", jpeg.base64())
                            }
                        }
                        addJsonObject { put("text", userText) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("maxOutputTokens", MAX_OUTPUT_TOKENS)
                put("temperature", 0.4)
                // 2.5 models think by default; switch it off for speed. Gemini 3 Flash-Lite
                // already defaults to minimal thinking, so nothing is sent for it.
                if (model.startsWith("gemini-2.5")) {
                    putJsonObject("thinkingConfig") { put("thinkingBudget", 0) }
                }
            }
        }
        return Request.Builder()
            .url("$baseUrl/v1beta/models/$model:streamGenerateContent?alt=sse")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
    }

    override fun probeRequest(apiKey: String): Request = modelsRequest(baseUrl, apiKey)

    override fun parseChunk(data: String, nowMs: Long): Chunk {
        val root = runCatching { JsonTools.json.parseToJsonElement(data) }.getOrNull().obj()
            ?: return Chunk.Skip
        root["error"]?.let { return Chunk.Error(ErrorClassifier.fromErrorJson(id, it, nowMs)) }
        root["promptFeedback"].obj()?.get("blockReason").str()?.let {
            return Chunk.Error(CallFailure.BadRequest("Blocked by Gemini safety filter: $it"))
        }
        val parts = root["candidates"].arr()?.firstOrNull().obj()
            ?.get("content").obj()?.get("parts").arr() ?: return Chunk.Skip
        val text = buildString {
            for (p in parts) {
                val part = p.obj() ?: continue
                if (part["thought"].bool() == true) continue
                append(part["text"].str().orEmpty())
            }
        }
        return if (text.isEmpty()) Chunk.Skip else Chunk.Text(text)
    }

    companion object {
        fun modelsRequest(baseUrl: String, apiKey: String): Request = Request.Builder()
            .url("$baseUrl/v1beta/models?pageSize=1000")
            .header("x-goog-api-key", apiKey)
            .get()
            .build()
    }
}

/** Groq and OpenRouter both speak the OpenAI chat-completions dialect. */
class OpenAiCompatProvider(override val id: ProviderId, private val baseUrl: String) : VisionProvider {

    override fun streamRequest(apiKey: String, model: String, jpeg: ByteArray, userText: String): Request {
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("max_tokens", MAX_OUTPUT_TOKENS)
            put("temperature", 0.4)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        // Some vision models reject a separate system message alongside an image,
                        // so the instructions ride in the user turn.
                        addJsonObject {
                            put("type", "text")
                            put("text", Prompt.SYSTEM + "\n\n" + userText)
                        }
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", "data:image/jpeg;base64," + jpeg.base64()) }
                        }
                    }
                }
            }
        }
        return authed(apiKey)
            .url("$baseUrl/chat/completions")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
    }

    override fun probeRequest(apiKey: String): Request {
        // OpenRouter's /models is public, /key needs a valid key and reports its limits.
        val path = if (id == ProviderId.OPENROUTER) "/key" else "/models"
        return authed(apiKey).url(baseUrl + path).get().build()
    }

    private fun authed(apiKey: String): Request.Builder {
        val b = Request.Builder().header("Authorization", "Bearer $apiKey")
        if (id == ProviderId.OPENROUTER) {
            b.header("HTTP-Referer", "https://github.com/ghaith-odibat/Iron-man-HUD")
            b.header("X-Title", "Iron HUD")
        }
        return b
    }

    override fun parseChunk(data: String, nowMs: Long): Chunk {
        if (data.trim() == "[DONE]") return Chunk.Done
        val root: JsonObject = runCatching { JsonTools.json.parseToJsonElement(data) }.getOrNull().obj()
            ?: return Chunk.Skip
        root["error"]?.let { return Chunk.Error(ErrorClassifier.fromErrorJson(id, it, nowMs)) }
        val choice = root["choices"].arr()?.firstOrNull().obj() ?: return Chunk.Skip
        val text = choice["delta"].obj()?.get("content").str()
        return if (text.isNullOrEmpty()) Chunk.Skip else Chunk.Text(text)
    }
}
