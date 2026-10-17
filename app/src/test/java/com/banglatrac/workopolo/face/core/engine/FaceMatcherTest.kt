package com.banglatrac.workopolo.face.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceMatcherTest {

    @Test
    fun `identical vectors score 1 and match`() {
        val v = floatArrayOf(0.3f, -0.2f, 0.9f)
        val result = FaceMatcher.match(v, listOf(v), threshold = 0.6f)
        assertEquals(1f, result.score, 1e-5f)
        assertTrue(result.matched)
    }

    @Test
    fun `orthogonal vectors score 0`() {
        assertEquals(0f, FaceMatcher.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-6f)
    }

    @Test
    fun `negative similarity is clamped to 0`() {
        val result = FaceMatcher.match(floatArrayOf(1f, 0f), listOf(floatArrayOf(-1f, 0f)), 0.6f)
        assertEquals(0f, result.score, 0f)
        assertFalse(result.matched)
    }

    @Test
    fun `best template wins`() {
        val probe = floatArrayOf(1f, 0f)
        val result = FaceMatcher.match(probe, listOf(floatArrayOf(0f, 1f), floatArrayOf(1f, 0.1f)), 0.6f)
        assertTrue(result.score > 0.99f)
    }

    @Test
    fun `score exactly at threshold matches`() {
        assertTrue(MatchScore(score = 0.6f, threshold = 0.6f).matched)
    }

    @Test
    fun `no templates never match`() {
        assertFalse(FaceMatcher.match(floatArrayOf(1f), emptyList(), 0.6f).matched)
    }

    @Test
    fun `l2 normalize gives unit length and leaves zero vector alone`() {
        val n = FaceMatcher.l2Normalize(floatArrayOf(3f, 4f))
        assertEquals(0.6f, n[0], 1e-6f)
        assertEquals(0.8f, n[1], 1e-6f)
        assertEquals(0f, FaceMatcher.l2Normalize(floatArrayOf(0f, 0f))[0], 0f)
    }
}
