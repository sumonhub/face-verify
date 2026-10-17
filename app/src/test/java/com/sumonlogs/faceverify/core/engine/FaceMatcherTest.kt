package com.sumonlogs.faceverify.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceMatcherTest {
    private val alex = listOf(floatArrayOf(1f, 0f, 0f))
    private val sam = listOf(floatArrayOf(0f, 1f, 0f))

    @Test
    fun `identical vectors score 1`() {
        val v = floatArrayOf(0.3f, -0.2f, 0.9f)
        assertEquals(1f, FaceMatcher.score(v, listOf(v)), 1e-5f)
    }

    @Test
    fun `orthogonal vectors score 0 and negative similarity is clamped`() {
        assertEquals(0f, FaceMatcher.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-6f)
        assertEquals(0f, FaceMatcher.score(floatArrayOf(1f, 0f), listOf(floatArrayOf(-1f, 0f))), 0f)
    }

    @Test
    fun `identifies the right person among several`() {
        val result = FaceMatcher.identify(floatArrayOf(0.1f, 1f, 0f), listOf(alex, sam), threshold = 0.6f, margin = 0.05f)
        assertEquals(1, result.bestIndex)
        assertTrue(result.matched)
        assertFalse(result.ambiguous)
        assertTrue(result.runnerUpScore < 0.2f)
    }

    @Test
    fun `stranger matches nobody`() {
        val result = FaceMatcher.identify(floatArrayOf(0f, 0f, 1f), listOf(alex, sam), 0.6f, 0.05f)
        assertFalse(result.matched)
        assertFalse(result.ambiguous)
    }

    @Test
    fun `two people scoring too close is ambiguous, not a match`() {
        val twin = listOf(floatArrayOf(1f, 0.05f, 0f))
        val result = FaceMatcher.identify(floatArrayOf(1f, 0.02f, 0f), listOf(alex, twin), 0.6f, 0.05f)
        assertTrue(result.ambiguous)
        assertFalse(result.matched)
    }

    @Test
    fun `a person is scored against the average of their templates, not the best one`() {
        // One odd template that happens to point at the probe must not decide the match on its own.
        val person = listOf(floatArrayOf(0f, 1f), floatArrayOf(0f, 1f), floatArrayOf(1f, 0f))
        val score = FaceMatcher.score(floatArrayOf(1f, 0f), person)
        assertTrue("score was $score", score < 0.5f)
    }

    @Test
    fun `centroid of identical vectors is that vector, normalised`() {
        val c = FaceMatcher.centroid(listOf(floatArrayOf(3f, 4f), floatArrayOf(6f, 8f)))
        assertEquals(0.6f, c[0], 1e-6f)
        assertEquals(0.8f, c[1], 1e-6f)
    }

    @Test
    fun `nobody enrolled gives no best index`() {
        val result = FaceMatcher.identify(floatArrayOf(1f), emptyList(), 0.6f, 0.05f)
        assertNull(result.bestIndex)
        assertFalse(result.matched)
    }

    @Test
    fun `l2 normalize gives unit length and leaves zero vector alone`() {
        val n = FaceMatcher.l2Normalize(floatArrayOf(3f, 4f))
        assertEquals(0.6f, n[0], 1e-6f)
        assertEquals(0.8f, n[1], 1e-6f)
        assertEquals(0f, FaceMatcher.l2Normalize(floatArrayOf(0f, 0f))[0], 0f)
    }
}
