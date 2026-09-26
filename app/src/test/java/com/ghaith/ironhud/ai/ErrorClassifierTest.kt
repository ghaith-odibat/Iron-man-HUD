package com.ghaith.ironhud.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorClassifierTest {
    private val now = 1_700_000_000_000L
    private val noHeaders: (String) -> String? = { null }

    @Test fun geminiPerMinuteQuotaUsesRetryDelay() {
        val body = """{"error":{"code":429,"message":"You exceeded your current quota, please check your plan and billing details.","status":"RESOURCE_EXHAUSTED",
            "details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{"quotaMetric":"generativelanguage.googleapis.com/generate_content_free_tier_requests","quotaId":"GenerateRequestsPerMinutePerProjectPerModel-FreeTier"}]},
            {"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"43s"}]}}"""
        val f = ErrorClassifier.classify(ProviderId.GEMINI, 429, noHeaders, body, now)
        f as CallFailure.RateLimited
        assertEquals(43_000L, f.retryAfterMs)
        assertEquals(false, f.daily)
    }

    @Test fun geminiPerDayQuotaIsDaily() {
        val body = """{"error":{"code":429,"message":"Quota exceeded","status":"RESOURCE_EXHAUSTED",
            "details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier"}]}]}}"""
        val f = ErrorClassifier.classify(ProviderId.GEMINI, 429, noHeaders, body, now) as CallFailure.RateLimited
        assertTrue(f.daily)
    }

    @Test fun geminiBadKeyIsInvalid() {
        val body = """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT",
            "details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"API_KEY_INVALID"}]}}"""
        assertTrue(ErrorClassifier.classify(ProviderId.GEMINI, 400, noHeaders, body, now) is CallFailure.InvalidKey)
    }

    @Test fun geminiRegionRefusalIsBadRequest() {
        val body = """{"error":{"code":400,"message":"User location is not supported for the API use.","status":"FAILED_PRECONDITION"}}"""
        assertTrue(ErrorClassifier.classify(ProviderId.GEMINI, 400, noHeaders, body, now) is CallFailure.BadRequest)
    }

    @Test fun geminiMissingModel() {
        val body = """{"error":{"code":404,"message":"models/gemini-9-flash-lite is not found for API version v1beta, or is not supported for generateContent.","status":"NOT_FOUND"}}"""
        assertTrue(ErrorClassifier.classify(ProviderId.GEMINI, 404, noHeaders, body, now) is CallFailure.ModelUnavailable)
    }

    @Test fun groqRateLimitPrefersHeader() {
        val body = """{"error":{"message":"Rate limit reached for model `meta-llama/llama-4-scout-17b-16e-instruct` in organization `org_x` service tier `on_demand` on requests per minute (RPM): Limit 30, Used 30, Requested 1. Please try again in 1.5s.","type":"requests","code":"rate_limit_exceeded"}}"""
        val f = ErrorClassifier.classify(ProviderId.GROQ, 429, { if (it == "retry-after") "2" else null }, body, now)
            as CallFailure.RateLimited
        assertEquals(2_000L, f.retryAfterMs)
        assertEquals(false, f.daily)
    }

    @Test fun groqDailyLimitParsesMessage() {
        val body = """{"error":{"message":"Rate limit reached for model `x` in organization `org_x` on requests per day (RPD): Limit 1000, Used 1000, Requested 1. Please try again in 1m26.4s.","type":"requests","code":"rate_limit_exceeded"}}"""
        val f = ErrorClassifier.classify(ProviderId.GROQ, 429, noHeaders, body, now) as CallFailure.RateLimited
        assertEquals(86_400L, f.retryAfterMs)
        assertTrue(f.daily)
    }

    @Test fun groqInvalidKey() {
        val body = """{"error":{"message":"Invalid API Key","type":"invalid_request_error","code":"invalid_api_key"}}"""
        assertTrue(ErrorClassifier.classify(ProviderId.GROQ, 401, noHeaders, body, now) is CallFailure.InvalidKey)
    }

    @Test fun groqDecommissionedModel() {
        val body = """{"error":{"message":"The model `llama-3.2-11b-vision-preview` has been decommissioned and is no longer supported.","type":"invalid_request_error","code":"model_decommissioned"}}"""
        assertTrue(ErrorClassifier.classify(ProviderId.GROQ, 400, noHeaders, body, now) is CallFailure.ModelUnavailable)
    }

    @Test fun openRouterFreeDailyLimitUsesResetHeader() {
        val body = """{"error":{"message":"Rate limit exceeded: free-models-per-day. Add 10 credits to unlock 1000 free model requests per day","code":429,
            "metadata":{"headers":{"X-RateLimit-Limit":"50","X-RateLimit-Remaining":"0","X-RateLimit-Reset":"${now + 3_600_000}"}}}}"""
        val f = ErrorClassifier.classify(ProviderId.OPENROUTER, 429, noHeaders, body, now) as CallFailure.RateLimited
        assertTrue(f.daily)
        assertEquals(3_600_000L, f.retryAfterMs)
    }

    @Test fun serverErrorsAreTransient() {
        assertTrue(ErrorClassifier.classify(ProviderId.GEMINI, 503, noHeaders, "overloaded", now) is CallFailure.Transient)
    }

    @Test fun durations() {
        assertEquals(43_000L, ErrorClassifier.parseDuration("43s"))
        assertEquals(500L, ErrorClassifier.parseDuration("0.5s"))
        assertEquals(750L, ErrorClassifier.parseDuration("750ms"))
        assertEquals(86_400L, ErrorClassifier.parseDuration("1m26.4s"))
        assertEquals(7_380_000L, ErrorClassifier.parseDuration("2h3m"))
        assertEquals(null, ErrorClassifier.parseDuration("soon"))
    }
}
