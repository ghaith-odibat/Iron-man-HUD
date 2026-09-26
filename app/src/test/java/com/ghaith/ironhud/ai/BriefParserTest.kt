package com.ghaith.ironhud.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefParserTest {
    @Test fun parsesCanonicalFormat() {
        val b = BriefParser.parse(
            """
            NAME: Santa Monica Ferris Wheel
            TYPE: Landmark
            CONFIDENCE: high
            BRIEF: The Pacific Wheel is a solar-powered Ferris wheel on Santa Monica Pier.
            It is a Los Angeles icon.
            - Height of 85 feet
            - Powered by 650 solar panels
            - Rebuilt in 2008
            """.trimIndent()
        )
        assertEquals("Santa Monica Ferris Wheel", b.name)
        assertEquals("Landmark", b.type)
        assertEquals("high", b.confidence)
        assertEquals(
            "The Pacific Wheel is a solar-powered Ferris wheel on Santa Monica Pier. It is a Los Angeles icon.",
            b.summary,
        )
        assertEquals(3, b.facts.size)
        assertEquals("Powered by 650 solar panels", b.facts[1])
    }

    @Test fun toleratesMarkdownAndOtherBullets() {
        val b = BriefParser.parse("**Name:** Logitech MX Master 3S\n**Type:** Mouse\n**Brief:** A wireless mouse.\n• 8K DPI sensor\n1. Quiet clicks")
        assertEquals("Logitech MX Master 3S", b.name)
        assertEquals(listOf("8K DPI sensor", "Quiet clicks"), b.facts)
    }

    @Test fun partialStreamIsSafe() {
        val b = BriefParser.parse("NAME: Coffee M")
        assertEquals("Coffee M", b.name)
        assertTrue(b.isUsable)
        assertEquals("", BriefParser.parse("").name)
    }

    @Test fun missingNameFallsBackToFirstLine() {
        val b = BriefParser.parse("A ceramic coffee mug\nBRIEF: Holds hot drinks.")
        assertEquals("A ceramic coffee mug", b.name)
        assertEquals("Holds hot drinks.", b.summary)
    }
}
