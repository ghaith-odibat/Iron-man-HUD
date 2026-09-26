package com.ghaith.ironhud.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelResolverTest {
    @Test fun picksNewestStableFlashLite() {
        val ids = listOf(
            "gemini-2.5-flash-lite", "gemini-3.1-flash-lite", "gemini-3.5-flash-lite-preview-06-2026",
            "gemini-3.5-flash", "gemini-flash-lite-latest", "gemini-3.1-flash-lite-tts",
        )
        assertEquals("gemini-3.1-flash-lite", ModelResolver.pickGeminiModel(ids))
    }

    @Test fun fallsBackToPreviewThenAlias() {
        assertEquals(
            "gemini-3.5-flash-lite-preview-06-2026",
            ModelResolver.pickGeminiModel(listOf("gemini-3.5-flash-lite-preview-06-2026", "gemini-2.0-flash")),
        )
        assertEquals("gemini-flash-lite-latest", ModelResolver.pickGeminiModel(listOf("gemini-flash-lite-latest", "gemma-3")))
        assertEquals(null, ModelResolver.pickGeminiModel(listOf("text-embedding-004")))
    }

    @Test fun parsesModelsListAndDropsNonGenerateModels() {
        val body = """{"models":[
            {"name":"models/gemini-3.1-flash-lite","supportedGenerationMethods":["generateContent","countTokens"]},
            {"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]}]}"""
        assertEquals(listOf("gemini-3.1-flash-lite"), ModelResolver.parseGeminiModels(body))
    }
}
