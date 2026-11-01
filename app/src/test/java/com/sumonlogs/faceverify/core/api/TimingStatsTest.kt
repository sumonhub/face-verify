package com.sumonlogs.faceverify.core.api

import org.junit.Assert.assertEquals
import org.junit.Test

class TimingStatsTest {

    @Test
    fun `average and nearest-rank p95`() {
        val samples = (1..20).map { it.toFloat() }
        val t = TimingStats.summarize("embed", samples)
        assertEquals(20, t.count)
        assertEquals(10.5f, t.avgMs, 1e-4f)
        assertEquals("19th of 20 sorted values", 19f, t.p95Ms, 1e-4f)
    }

    @Test
    fun `order of the samples does not matter`() {
        val t = TimingStats.summarize("x", listOf(50f, 10f, 30f, 20f, 40f))
        assertEquals(30f, t.avgMs, 1e-4f)
        assertEquals(50f, t.p95Ms, 1e-4f)
    }

    @Test
    fun `a single sample is its own average and p95`() {
        val t = TimingStats.summarize("x", listOf(7f))
        assertEquals(7f, t.avgMs, 1e-4f)
        assertEquals(7f, t.p95Ms, 1e-4f)
    }

    @Test
    fun `no samples gives zeros`() {
        assertEquals(StageTiming("x", 0, 0f, 0f), TimingStats.summarize("x", emptyList()))
    }

    @Test
    fun `diagnostics keeps the newest samples and can be cleared`() {
        val d = FaceDiagnostics(publishTimingsEveryMs = 0)
        repeat(250) { d.recordTiming("embed", it.toFloat()) }
        d.recordTiming("embed", 0f)
        val timing = d.stageTimings.value.single { it.stage == "embed" }
        assertEquals("only the last 200 are kept", 200, timing.count)
        d.clearTimings()
        assertEquals(emptyList<StageTiming>(), d.stageTimings.value)
    }
}
