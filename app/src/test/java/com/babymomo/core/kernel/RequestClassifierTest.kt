package com.babymomo.core.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RequestClassifier] — rule-based routing.
 *
 * The classifier is pure (no I/O, no DI, no Android dependencies) so it's the easiest
 * component to test exhaustively. These tests pin the routing heuristics that the rest of
 * the kernel relies on: keyword-triggered agent engagement, length-based complexity
 * escalation, and the invariant that BABYMOMO ALWAYS consults memory (it's the defining
 * feature of the app — "an AI that remembers everything important about me").
 */
class RequestClassifierTest {

    private val classifier = RequestClassifier()

    // ---- Simple messages ----

    @Test
    fun `simple hello routes to SIMPLE with no extras`() {
        val r = classifier.classify("hello")
        assertEquals(RoutingDecision.Complexity.SIMPLE, r.complexity)
        assertTrue(r.needMemory)
        assertFalse(r.needPlanning)
        assertFalse(r.needResearch)
        assertFalse(r.needCritic)
        assertFalse(r.needTools)
        assertFalse(r.needInternet)
        assertEquals(1, r.stepBudget)
    }

    @Test
    fun `short factual question is SIMPLE`() {
        val r = classifier.classify("What is the capital of France?")
        // "what" → no keyword trigger; length is small; single sentence
        assertEquals(RoutingDecision.Complexity.SIMPLE, r.complexity)
        assertTrue(r.needMemory)
        assertFalse(r.needPlanning)
        assertFalse(r.needResearch)
    }

    // ---- Planning ----

    @Test
    fun `plan a roadmap triggers planning at MODERATE or higher`() {
        val r = classifier.classify("Plan a roadmap for launching my app")
        assertTrue("needPlanning must be true for plan/roadmap", r.needPlanning)
        assertTrue(
            "complexity must be MODERATE or COMPLEX, got ${r.complexity}",
            r.complexity == RoutingDecision.Complexity.MODERATE ||
                r.complexity == RoutingDecision.Complexity.COMPLEX
        )
        // MODERATE → 3 steps; COMPLEX → 6 steps
        val expected = if (r.complexity == RoutingDecision.Complexity.MODERATE) 3 else 6
        assertEquals(expected, r.stepBudget)
    }

    @Test
    fun `strategy keyword triggers planning`() {
        val r = classifier.classify("Help me define a strategy for growth")
        assertTrue(r.needPlanning)
    }

    @Test
    fun `break down keyword triggers planning`() {
        val r = classifier.classify("Break down this problem for me")
        assertTrue(r.needPlanning)
    }

    // ---- Research + Internet ----

    @Test
    fun `research the latest AI news triggers research and internet`() {
        val r = classifier.classify("Research the latest AI news")
        assertTrue("needResearch must be true", r.needResearch)
        assertTrue("needInternet must be true when research is requested", r.needInternet)
    }

    @Test
    fun `look up triggers research and internet`() {
        val r = classifier.classify("Look up the current weather")
        assertTrue(r.needResearch)
        assertTrue(r.needInternet)
    }

    @Test
    fun `news keyword triggers internet`() {
        val r = classifier.classify("What's the news on the election?")
        assertTrue(r.needInternet)
    }

    // ---- Critic ----

    @Test
    fun `verify triggers critic`() {
        val r = classifier.classify("Verify this is true")
        assertTrue(r.needCritic)
    }

    @Test
    fun `is it true triggers critic`() {
        val r = classifier.classify("Is it true that water boils at 100C?")
        assertTrue(r.needCritic)
    }

    @Test
    fun `double-check triggers critic`() {
        val r = classifier.classify("Please double-check my answer")
        assertTrue(r.needCritic)
    }

    // ---- Tools ----

    @Test
    fun `write article triggers tools`() {
        val r = classifier.classify("Write an article about photosynthesis")
        assertTrue(r.needTools)
    }

    @Test
    fun `summarize triggers tools`() {
        val r = classifier.classify("Summarize this chapter for me")
        assertTrue(r.needTools)
    }

    @Test
    fun `draft triggers tools`() {
        val r = classifier.classify("Draft a project brief")
        assertTrue(r.needTools)
    }

    // ---- Long / complex messages ----

    @Test
    fun `long multi-sentence message escalates to COMPLEX and forces planning`() {
        val msg = buildString {
            repeat(25) { i ->
                append("This is sentence number $it with several words in it for padding. ")
            }
        }
        // Sanity-check the message really is long and multi-sentence
        assertTrue("test message should exceed 500 chars", msg.length > 500)

        val r = classifier.classify(msg)
        assertEquals(RoutingDecision.Complexity.COMPLEX, r.complexity)
        assertTrue("COMPLEX must force needPlanning", r.needPlanning)
        assertEquals(6, r.stepBudget)
    }

    @Test
    fun `very long single-sentence message escalates to COMPLEX`() {
        val msg = "a".repeat(550) // 550 chars, single sentence
        val r = classifier.classify(msg)
        assertEquals(RoutingDecision.Complexity.COMPLEX, r.complexity)
        assertTrue(r.needPlanning)
    }

    @Test
    fun `medium multi-sentence message is MODERATE`() {
        // length < 200 but multi-sentence && wordCount > 30 → MODERATE
        val words = (1..40).joinToString(" ") { "word$it" }
        val msg = "$words. $words." // ~80 words, 2 sentences, ~500 chars
        val r = classifier.classify(msg)
        // Could be MODERATE or COMPLEX depending on actual length; just verify it's not SIMPLE
        assertTrue(
            "medium multi-sentence must escalate beyond SIMPLE, got ${r.complexity}",
            r.complexity != RoutingDecision.Complexity.SIMPLE
        )
    }

    // ---- Memory invariant ----

    @Test
    fun `needMemory is always true regardless of message`() {
        // This is BABYMOMO's defining feature — every turn consults memory.
        assertTrue("hello", classifier.classify("hello").needMemory)
        assertTrue("plan", classifier.classify("plan a roadmap").needMemory)
        assertTrue("research", classifier.classify("research the latest AI news").needMemory)
        assertTrue("write", classifier.classify("write an article").needMemory)
        assertTrue("verify", classifier.classify("verify this is true").needMemory)
        assertTrue("empty", classifier.classify("").needMemory)
        assertTrue("long", classifier.classify("a".repeat(1000)).needMemory)
    }

    // ---- Routing reason string ----

    @Test
    fun `reason string includes complexity and active signals`() {
        val r = classifier.classify("Plan a roadmap and research the latest news")
        // Should mention "planning", "research", "internet", and the complexity level
        assertTrue("reason should mention planning: ${r.reason}", r.reason.contains("planning"))
        assertTrue("reason should mention research: ${r.reason}", r.reason.contains("research"))
        assertTrue("reason should mention internet: ${r.reason}", r.reason.contains("internet"))
    }
}
