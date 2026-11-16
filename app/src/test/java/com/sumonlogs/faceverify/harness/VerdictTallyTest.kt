package com.sumonlogs.faceverify.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerdictTallyTest {

    @Test
    fun `the four outcomes follow what the app did and what the tester said`() {
        assertEquals(Outcome.Accepted, Outcome.of(matched = true, correct = true))
        assertEquals(Outcome.Refused, Outcome.of(matched = false, correct = true))
        assertEquals(Outcome.WrongAccepted, Outcome.of(matched = true, correct = false))
        assertEquals(Outcome.WrongRefused, Outcome.of(matched = false, correct = false))
        assertTrue(Outcome.Accepted.right && Outcome.Refused.right)
        assertFalse(Outcome.WrongAccepted.right || Outcome.WrongRefused.right)
    }

    @Test
    fun `codes round trip and unknown codes are dropped`() {
        Outcome.entries.forEach { assertEquals(it, Outcome.fromCode(it.code)) }
        assertNull(Outcome.fromCode("zz"))
    }

    @Test
    fun `tally counts one model only`() {
        val verdicts = listOf(
            Verdict("a", Outcome.Accepted), Verdict("a", Outcome.Accepted), Verdict("a", Outcome.Refused),
            Verdict("a", Outcome.WrongRefused), Verdict("b", Outcome.WrongAccepted),
        )
        val tally = VerdictTally.of(verdicts, "a")
        assertEquals(VerdictTally(accepted = 2, refused = 1, wrongAccepted = 0, wrongRefused = 1), tally)
        assertEquals(3, tally.right)
        assertEquals(1, tally.wrong)
        assertEquals(4, tally.total)
        assertEquals(75, tally.rightPercent)
    }

    @Test
    fun `no answers means no percentage`() {
        assertNull(VerdictTally().rightPercent)
        assertEquals(0, VerdictTally().total)
    }
}
