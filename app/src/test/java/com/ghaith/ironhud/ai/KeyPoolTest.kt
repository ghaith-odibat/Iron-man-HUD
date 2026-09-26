package com.ghaith.ironhud.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyPoolTest {
    private var now = 1_700_000_000_000L
    private val pool = KeyPool { now }

    private val g1 = ApiKeyEntry("g1", ProviderId.GEMINI, "AIza-one-xxxxxxxx")
    private val g2 = ApiKeyEntry("g2", ProviderId.GEMINI, "AIza-two-xxxxxxxx")
    private val q1 = ApiKeyEntry("q1", ProviderId.GROQ, "gsk_one_xxxxxxxxx")

    @Test fun roundRobinsWithinFirstProvider() {
        pool.configure(listOf(g1, g2, q1))
        assertEquals("g1", pool.acquire()?.id)
        assertEquals("g2", pool.acquire()?.id)
        assertEquals("g1", pool.acquire()?.id)
    }

    @Test fun rateLimitedKeySwitchesToNextThenRecovers() {
        pool.configure(listOf(g1, g2, q1))
        pool.reportFailure(g1, CallFailure.RateLimited("429", retryAfterMs = 30_000, daily = false))
        assertEquals("g2", pool.acquire()?.id)
        assertEquals("g2", pool.acquire()?.id)
        assertEquals(KeyHealth.COOLING, pool.statusOf("g1").health)

        now += 30_001
        assertEquals(KeyHealth.READY, pool.statusOf("g1").health)
    }

    @Test fun fallsThroughToNextProviderWhenAllBenched() {
        pool.configure(listOf(g1, g2, q1))
        pool.reportFailure(g1, CallFailure.RateLimited("429", null, daily = false))
        pool.reportFailure(g2, CallFailure.InvalidKey("bad"))
        assertEquals("q1", pool.acquire()?.id)
    }

    @Test fun respectsProviderOrder() {
        pool.configure(listOf(g1, q1), providerOrder = listOf(ProviderId.GROQ, ProviderId.GEMINI))
        assertEquals("q1", pool.acquire()?.id)
    }

    @Test fun excludesTriedKeysAndProviders() {
        pool.configure(listOf(g1, g2, q1))
        assertEquals("q1", pool.acquire(excludeProviders = setOf(ProviderId.GEMINI))?.id)
        assertEquals("g2", pool.acquire(excludeIds = setOf("g1"))?.id)
        assertNull(pool.acquire(excludeIds = setOf("g1", "g2", "q1")))
    }

    @Test fun geminiDailyLimitLastsUntilPacificMidnight() {
        pool.configure(listOf(g1))
        pool.reportFailure(g1, CallFailure.RateLimited("daily", 10_000, daily = true))
        val s = pool.statusOf("g1")
        assertEquals(KeyHealth.EXHAUSTED, s.health)
        assert(s.until > now + 10_000) { "daily bench must outlast the short retry hint" }
        assert(s.until <= now + KeyPool.DAY_MS)
        assertNull(pool.acquire())
        assertEquals(s.until, pool.nextRecoveryAt())
    }

    @Test fun invalidKeyStaysBenchedUntilReset() {
        pool.configure(listOf(g1))
        pool.reportFailure(g1, CallFailure.InvalidKey("nope"))
        now += KeyPool.DAY_MS * 3
        assertNull(pool.acquire())
        pool.reset("g1")
        assertEquals("g1", pool.acquire()?.id)
    }

    @Test fun transientErrorsDoNotBench() {
        pool.configure(listOf(g1))
        pool.reportFailure(g1, CallFailure.Transient("timeout"))
        assertEquals(KeyHealth.READY, pool.statusOf("g1").health)
    }

    @Test fun persistedStatusSurvivesReconfigure() {
        pool.configure(listOf(g1), persisted = mapOf("g1" to KeyStatus(KeyHealth.COOLING, until = now + 5_000)))
        assertNull(pool.acquire())
        pool.configure(listOf(g1, g2))
        assertEquals("g2", pool.acquire()?.id)
    }

    @Test fun maskHidesSecret() {
        assertEquals("AIza••••xxxx", g1.masked)
        assert("one" !in g1.toString())
    }
}
