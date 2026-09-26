package com.ghaith.ironhud.ai

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections

/** End-to-end failover through real HTTP + SSE against a local mock of all three providers. */
class IdentifyServiceTest {
    private lateinit var server: MockWebServer
    private val seenKeys = Collections.synchronizedList(mutableListOf<String>())
    private var now = 1_700_000_000_000L
    private val pool = KeyPool { now }
    private lateinit var service: IdentifyService

    private val g1 = ApiKeyEntry("g1", ProviderId.GEMINI, "AIza-limited-key-1")
    private val g2 = ApiKeyEntry("g2", ProviderId.GEMINI, "AIza-healthy-key-2")
    private val q1 = ApiKeyEntry("q1", ProviderId.GROQ, "gsk_groq_key_000001")

    private val geminiStream = listOf(
        """{"candidates":[{"content":{"parts":[{"text":"NAME: Ceramic Coffee Mug\nTYPE: Kitchenware\n"}],"role":"model"}}]}""",
        """{"candidates":[{"content":{"parts":[{"text":"CONFIDENCE: high\nBRIEF: A glazed ceramic mug for hot drinks.\n- Holds about 350 ml\n"}],"role":"model"}}]}""",
        """{"candidates":[{"content":{"parts":[{"text":"- Dishwasher safe"}],"role":"model"},"finishReason":"STOP"}]}""",
    ).joinToString("") { "data: $it\r\n\r\n" }

    private val groqStream = listOf(
        """{"choices":[{"index":0,"delta":{"role":"assistant","content":""}}]}""",
        """{"choices":[{"index":0,"delta":{"content":"NAME: Coffee Mug\nBRIEF: A mug."}}]}""",
        "[DONE]",
    ).joinToString("") { "data: $it\n\n" }

    private val rateLimitBody =
        """{"error":{"code":429,"message":"Quota exceeded","status":"RESOURCE_EXHAUSTED","details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"20s"}]}}"""

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val geminiKey = request.getHeader("x-goog-api-key")
                val bearer = request.getHeader("Authorization")?.removePrefix("Bearer ")
                seenKeys += geminiKey ?: bearer ?: "none"
                return when {
                    path.startsWith("/gemini/v1beta/models?") -> MockResponse().setBody(
                        """{"models":[{"name":"models/gemini-3.1-flash-lite","supportedGenerationMethods":["generateContent"]}]}"""
                    )
                    path.startsWith("/gemini/") && geminiKey == g1.secret ->
                        MockResponse().setResponseCode(429).setBody(rateLimitBody)
                    path.startsWith("/gemini/") && geminiKey == g2.secret -> {
                        assertTrue(path, path.contains("/models/gemini-3.1-flash-lite:streamGenerateContent"))
                        MockResponse().setHeader("Content-Type", "text/event-stream").setBody(geminiStream)
                    }
                    path.startsWith("/groq/chat/completions") ->
                        MockResponse().setHeader("Content-Type", "text/event-stream").setBody(groqStream)
                    else -> MockResponse().setResponseCode(404).setBody("""{"error":{"message":"no route $path"}}""")
                }
            }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        service = IdentifyService(
            pool = pool,
            http = Http.newClient(),
            endpoints = Endpoints(gemini = "$base/gemini", groq = "$base/groq", openRouter = "$base/openrouter"),
            clock = { now },
        )
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun rateLimitedKeyFailsOverToNextKeyAndStreams() = runBlocking {
        pool.configure(listOf(g1, g2, q1))
        val events = service.identify(byteArrayOf(1, 2, 3), "Cup").toList()

        val done = events.last() as ScanEvent.Done
        assertEquals("Ceramic Coffee Mug", done.brief.name)
        assertEquals(listOf("Holds about 350 ml", "Dishwasher safe"), done.brief.facts)
        assertEquals(ProviderId.GEMINI, done.provider)
        assertEquals("gemini-3.1-flash-lite", done.model)

        assertTrue(events.any { it is ScanEvent.KeySwitched })
        assertTrue("streams partial briefs", events.count { it is ScanEvent.Partial } >= 2)
        assertEquals(KeyHealth.COOLING, pool.statusOf("g1").health)
        assertEquals(now + 20_000, pool.statusOf("g1").until)
        assertEquals(KeyHealth.READY, pool.statusOf("g2").health)

        // Next scan goes straight to the healthy key: g1 is benched.
        seenKeys.clear()
        val second = service.identify(byteArrayOf(1), null).toList()
        assertTrue(second.last() is ScanEvent.Done)
        assertTrue(g1.secret !in seenKeys)
    }

    @Test fun fallsBackToGroqWhenGeminiIsExhausted() = runBlocking {
        pool.configure(listOf(g1, q1))
        val done = service.identify(byteArrayOf(9), null).toList().last() as ScanEvent.Done
        assertEquals(ProviderId.GROQ, done.provider)
        assertEquals("Coffee Mug", done.brief.name)
    }

    @Test fun reportsFailureWithRecoveryTimeWhenEverythingIsBenched() = runBlocking {
        pool.configure(listOf(g1))
        val last = service.identify(byteArrayOf(9), null).toList().last() as ScanEvent.Failed
        assertEquals(now + 20_000, last.retryAtMs)
    }

    @Test fun noKeysMeansOfflineMessage() = runBlocking {
        pool.configure(emptyList())
        val last = service.identify(byteArrayOf(9), null).toList().single() as ScanEvent.Failed
        assertTrue(last.reason.contains("VAULT"))
    }

    @Test fun keyTestAcceptsGoodKeyAndRemembersModel() = runBlocking {
        pool.configure(listOf(g2))
        val r = service.testKey(g2)
        assertTrue(r.message, r.ok)
        assertEquals("gemini-3.1-flash-lite", service.models.cached(ProviderId.GEMINI))
    }
}
