package com.babymomo.core.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the [MemoryRecaller] reranker's 4-signal scoring formula.
 *
 * The full `recall()` pipeline depends on `EmbedderProvider`, `VectorIndex`, `MemoryGraph`,
 * and `MemoryService` (all Hilt-injected, all with non-trivial collaborators). Spinning up
 * the whole graph just to verify a weighted-sum formula is overkill. Instead, the scoring
 * math has been extracted into [MemoryRecaller.computeRerankScore] — an `internal` companion
 * function — so we can verify the math in isolation.
 *
 * Formula:
 * ```
 *   score = 0.5 · cosineSimilarity
 *         + 0.2 · graphProximity
 *         + 0.2 · confidence
 *         + 0.1 · recencyDecay
 * ```
 * where `recencyDecay = exp(-ageInMillis / 30 days)` — a memory 30 days old gets ~37% of the
 * recency weight, 60 days old ~13%, 90 days old ~5%.
 *
 * Per `docs/architecture-decisions.md` → "Testing strategy", this is the recommended approach:
 * test the math, not the framework.
 */
class MemoryRecallerRerankTest {

    // Fixed time base — the `now` parameter exists precisely so tests don't depend on wall-clock.
    private val now = 1_700_000_000_000L

    // ---- known-value formula check ----

    @Test
    fun `known 4-signal score matches the formula`() {
        // cosine=0.8, graphProx=0.0, confidence=0.9, age=0 → recencyDecay=exp(0)=1.0
        // expected = 0.5·0.8 + 0.2·0 + 0.2·0.9 + 0.1·1.0
        //          = 0.4   + 0    + 0.18  + 0.1
        //          = 0.68
        val score = MemoryRecaller.computeRerankScore(
            cosineSimilarity = 0.8f,
            graphProximity = 0.0f,
            confidence = 0.9f,
            ageInMillis = 0L,
            now = now
        )
        assertEquals(0.68f, score, 1e-5f)
    }

    @Test
    fun `zero-signal score is exactly zero`() {
        // cosine=0, graphProx=0, confidence=0, recencyDecay=exp(-age/30days).
        // For age=very large, recencyDecay → 0 → score → 0.
        val veryOld = 365L * 24 * 60 * 60 * 1000 // 1 year
        val score = MemoryRecaller.computeRerankScore(
            cosineSimilarity = 0f,
            graphProximity = 0f,
            confidence = 0f,
            ageInMillis = veryOld,
            now = now
        )
        // exp(-365/30) ≈ exp(-12.17) ≈ 5.2e-6 → 0.1 · 5.2e-6 ≈ 5.2e-7 ≈ 0
        assertEquals(0f, score, 1e-4f)
    }

    // ---- individual signal dominance ----

    @Test
    fun `graph proximity boosts score by exactly 0_2`() {
        // All else equal, raising graphProximity from 0.0 to 1.0 must add exactly 0.2 to the score.
        val without = MemoryRecaller.computeRerankScore(
            cosineSimilarity = 0.7f, graphProximity = 0.0f, confidence = 0.8f, ageInMillis = 0L, now = now
        )
        val with = MemoryRecaller.computeRerankScore(
            cosineSimilarity = 0.7f, graphProximity = 1.0f, confidence = 0.8f, ageInMillis = 0L, now = now
        )
        assertTrue("graph-prox hit must outscore non-graph hit (with=$with, without=$without)", with > without)
        assertEquals(0.2f, with - without, 1e-5f)
    }

    @Test
    fun `recent memory outscores old memory`() {
        // All else equal, age=0 (recencyDecay=1.0) must outscore age=60 days (recencyDecay=exp(-2)≈0.135).
        val recent = MemoryRecaller.computeRerankScore(
            cosineSimilarity = 0.7f, graphProximity = 0.0f, confidence = 0.8f, ageInMillis = 0L, now = now
        )
        val old = MemoryRecaller.computeRerankScore(
            cosineSimilarity = 0.7f, graphProximity = 0.0f, confidence = 0.8f,
            ageInMillis = 60L * 24 * 60 * 60 * 1000, now = now
        )
        assertTrue("recent must outscore old (recent=$recent, old=$old)", recent > old)
        // Expected delta: 0.1 · (1.0 - exp(-2)) ≈ 0.1 · 0.8647 ≈ 0.0865
        val expectedDelta = 0.1f * (1.0f - kotlin.math.exp(-2.0).toFloat())
        assertEquals(expectedDelta, recent - old, 1e-5f)
    }

    @Test
    fun `high confidence outscores low confidence by exactly 0_16`() {
        // All else equal, raising confidence from 0.1 to 0.9 must add 0.2 · 0.8 = 0.16 to the score.
        val low = MemoryRecaller.computeRerankScore(
            cosineSimilarity = 0.7f, graphProximity = 0.0f, confidence = 0.1f, ageInMillis = 0L, now = now
        )
        val high = MemoryRecaller.computeRerankScore(
            cosineSimilarity = 0.7f, graphProximity = 0.0f, confidence = 0.9f, ageInMillis = 0L, now = now
        )
        assertTrue("high-conf must outscore low-conf (high=$high, low=$low)", high > low)
        assertEquals(0.16f, high - low, 1e-5f)
    }

    @Test
    fun `cosine similarity boost of 0_1 adds exactly 0_05 to the score`() {
        // All else equal, raising cosine from 0.5 to 0.6 must add 0.5 · 0.1 = 0.05 to the score.
        val lower = MemoryRecaller.computeRerankScore(
            cosineSimilarity = 0.5f, graphProximity = 0.0f, confidence = 0.5f, ageInMillis = 0L, now = now
        )
        val higher = MemoryRecaller.computeRerankScore(
            cosineSimilarity = 0.6f, graphProximity = 0.0f, confidence = 0.5f, ageInMillis = 0L, now = now
        )
        assertEquals(0.05f, higher - lower, 1e-5f)
    }

    // ---- signal weighting order ----

    @Test
    fun `cosine weight is largest — a 0_1 cosine gain beats a 0_1 graph gain`() {
        val base = MemoryRecaller.computeRerankScore(
            cosineSimilarity = 0.5f, graphProximity = 0.0f, confidence = 0.5f, ageInMillis = 0L, now = now
        )
        val cosineBoosted = MemoryRecaller.computeRerankScore(
            cosineSimilarity = 0.6f, graphProximity = 0.0f, confidence = 0.5f, ageInMillis = 0L, now = now
        )
        val graphBoosted = MemoryRecaller.computeRerankScore(
            cosineSimilarity = 0.5f, graphProximity = 0.1f, confidence = 0.5f, ageInMillis = 0L, now = now
        )
        // 0.5 · 0.1 = 0.05  vs  0.2 · 0.1 = 0.02
        assertTrue("cosine gain (0.05) must exceed graph gain (0.02)", (cosineBoosted - base) > (graphBoosted - base))
    }

    @Test
    fun `confidence and graph proximity have equal weight`() {
        // Both signals carry weight 0.2 — a 0.1 gain in confidence (0.2 · 0.1 = 0.02)
        // must equal a 0.1 gain in graphProximity (0.2 · 0.1 = 0.02).
        val base = MemoryRecaller.computeRerankScore(
            cosineSimilarity = 0.5f, graphProximity = 0.0f, confidence = 0.5f, ageInMillis = 0L, now = now
        )
        val confBoosted = MemoryRecaller.computeRerankScore(
            cosineSimilarity = 0.5f, graphProximity = 0.0f, confidence = 0.6f, ageInMillis = 0L, now = now
        )
        val graphBoosted = MemoryRecaller.computeRerankScore(
            cosineSimilarity = 0.5f, graphProximity = 0.1f, confidence = 0.5f, ageInMillis = 0L, now = now
        )
        assertEquals("conf and graph must have equal weight (0.2)", confBoosted - base, graphBoosted - base, 1e-6f)
    }

    // ---- recency decay curve ----

    @Test
    fun `recency decay at age 0 is exactly 1_0`() {
        // age=0 → exp(0) = 1.0 → recency contribution = 0.1 · 1.0 = 0.1.
        // With cosine=0, graph=0, conf=0: score = 0.1.
        val score = MemoryRecaller.computeRerankScore(
            cosineSimilarity = 0f, graphProximity = 0f, confidence = 0f, ageInMillis = 0L, now = now
        )
        assertEquals(0.1f, score, 1e-6f)
    }

    @Test
    fun `recency decay at 30 days is exp(-1) ≈ 0_368`() {
        val thirtyDays = 30L * 24 * 60 * 60 * 1000
        val score = MemoryRecaller.computeRerankScore(
            cosineSimilarity = 0f, graphProximity = 0f, confidence = 0f, ageInMillis = thirtyDays, now = now
        )
        val expected = 0.1f * kotlin.math.exp(-1.0).toFloat()
        assertEquals(expected, score, 1e-6f)
    }

    @Test
    fun `recency decay at 60 days is exp(-2) ≈ 0_135`() {
        val sixtyDays = 60L * 24 * 60 * 60 * 1000
        val score = MemoryRecaller.computeRerankScore(
            cosineSimilarity = 0f, graphProximity = 0f, confidence = 0f, ageInMillis = sixtyDays, now = now
        )
        val expected = 0.1f * kotlin.math.exp(-2.0).toFloat()
        assertEquals(expected, score, 1e-6f)
    }

    // ---- combined-scenario end-to-end math ----

    @Test
    fun `perfect hit at age 0 with full graph and confidence scores 1_0`() {
        // cosine=1.0, graph=1.0, conf=1.0, recency=1.0 → score = 0.5 + 0.2 + 0.2 + 0.1 = 1.0
        val score = MemoryRecaller.computeRerankScore(
            cosineSimilarity = 1.0f, graphProximity = 1.0f, confidence = 1.0f, ageInMillis = 0L, now = now
        )
        assertEquals(1.0f, score, 1e-5f)
    }

    @Test
    fun `worst-case hit at age 0 still gets the recency contribution`() {
        // cosine=-1.0 (opposite), graph=0, conf=0, recency=1.0 → score = -0.5 + 0 + 0 + 0.1 = -0.4
        val score = MemoryRecaller.computeRerankScore(
            cosineSimilarity = -1.0f, graphProximity = 0.0f, confidence = 0.0f, ageInMillis = 0L, now = now
        )
        assertEquals(-0.4f, score, 1e-5f)
    }

    @Test
    fun `score is bounded above by 1_0 when all signals are at max`() {
        // The formula is a weighted sum with weights summing to 1.0; with all inputs in [0, 1]
        // (and cosine in [-1, 1]) the max is 1.0 (cosine=1.0 case). The min for cosine=1.0
        // case is 0.5 (when graph=conf=recency=0).
        val maxScore = MemoryRecaller.computeRerankScore(
            cosineSimilarity = 1.0f, graphProximity = 1.0f, confidence = 1.0f, ageInMillis = 0L, now = now
        )
        assertTrue("score should be ≤ 1.0", maxScore <= 1.0f + 1e-5f)
    }
}
