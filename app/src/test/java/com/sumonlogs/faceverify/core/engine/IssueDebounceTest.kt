package com.sumonlogs.faceverify.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IssueDebounceTest {

    @Test
    fun `a problem is reported only after it lasts the minimum number of frames`() {
        val d = IssueDebounce(minFrames = 3)
        assertNull(d.report(QualityIssue.TooDark))
        assertNull(d.report(QualityIssue.TooDark))
        assertEquals(QualityIssue.TooDark, d.report(QualityIssue.TooDark))
        assertEquals("keeps reporting while it lasts", QualityIssue.TooDark, d.report(QualityIssue.TooDark))
    }

    @Test
    fun `one good frame starts the count over`() {
        val d = IssueDebounce(minFrames = 3)
        d.report(QualityIssue.TooDark)
        d.report(QualityIssue.TooDark)
        assertNull(d.report(null))
        assertNull(d.report(QualityIssue.TooDark))
        assertNull(d.report(QualityIssue.TooDark))
    }

    @Test
    fun `a different problem starts its own count`() {
        val d = IssueDebounce(minFrames = 2)
        d.report(QualityIssue.TooDark)
        assertNull(d.report(QualityIssue.Blurry))
        assertEquals(QualityIssue.Blurry, d.report(QualityIssue.Blurry))
    }

    @Test
    fun `reset forgets the streak`() {
        val d = IssueDebounce(minFrames = 2)
        d.report(QualityIssue.TooDark)
        d.reset()
        assertNull(d.report(QualityIssue.TooDark))
    }
}
