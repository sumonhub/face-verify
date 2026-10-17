package com.sumonlogs.faceverify.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationStatsTest {

    @Test
    fun `separated scores suggest the middle of the gap`() {
        val stats = CalibrationStats(genuine = listOf(0.7f, 0.8f), impostor = listOf(0.2f, 0.3f))
        assertFalse(stats.overlaps)
        assertEquals(0.5f, requireNotNull(stats.suggestedThreshold), 1e-6f)
        assertEquals(0, stats.falseAccepts(0.5f))
        assertEquals(0, stats.falseRejects(0.5f))
    }

    @Test
    fun `overlap keeps every labelled stranger out`() {
        val stats = CalibrationStats(genuine = listOf(0.45f, 0.8f), impostor = listOf(0.2f, 0.5f))
        assertTrue(stats.overlaps)
        val t = requireNotNull(stats.suggestedThreshold)
        assertEquals(0, stats.falseAccepts(t))
        assertEquals(1, stats.falseRejects(t))
    }

    @Test
    fun `no strangers labelled yet gives no suggestion`() {
        assertNull(CalibrationStats(genuine = listOf(0.7f), impostor = emptyList()).suggestedThreshold)
    }

    @Test
    fun `stats are per model`() {
        val samples = listOf(
            CalibrationSample("a", true, 0.7f),
            CalibrationSample("b", false, 0.9f),
            CalibrationSample("a", false, 0.2f),
        )
        val stats = CalibrationStats.of(samples, "a")
        assertEquals(listOf(0.7f), stats.genuine)
        assertEquals(listOf(0.2f), stats.impostor)
    }
}
