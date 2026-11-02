package com.sumonlogs.faceverify.harness.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CropRecordTest {

    private fun record() = CropRecord(
        installId = "install-1", appVersion = "1.0", device = "Acme Phone", pipelineVersion = 2, takenAtMillis = 1_000L,
        person = "install-_face-1", session = "19700101_dim", lighting = "dim", glasses = false, faceCovered = false,
        matched = true, score = 0.7f, png = "AAAA",
    )

    /** The `hasOnly([...])` list of the `crops` rule: the second one in the file, after `checks`. */
    private fun allowedKeysInCropsRule(): Set<String> {
        val rules = listOf(File("../firestore.rules"), File("firestore.rules")).first { it.exists() }.readText()
        val list = Regex("""hasOnly\(\[(.*?)]\)""", RegexOption.DOT_MATCHES_ALL).findAll(rules).toList()[1].groupValues[1]
        return Regex("""'([A-Za-z0-9]+)'""").findAll(list).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun `every field the app sends is allowed by the crops rule, and no dead entries`() {
        val sent = record().toFields().keys
        val allowed = allowedKeysInCropsRule() - "receivedAt"
        assertEquals(sent, allowed)
    }

    @Test
    fun `only a genuine tester who reached a comparison shares a crop`() {
        assertTrue(CropRecord.eligible("me", livenessPassed = true, failure = null))
        assertTrue(CropRecord.eligible("me", livenessPassed = true, failure = "AmbiguousMatch"))
        assertFalse("someone else never agreed", CropRecord.eligible("other", true, null))
        assertFalse("spoof attempts are not faces to evaluate", CropRecord.eligible("photo", true, null))
        assertFalse(CropRecord.eligible("me", livenessPassed = false, failure = null))
        assertFalse("no comparison happened", CropRecord.eligible("me", true, "SpoofSuspected"))
        assertFalse(CropRecord.eligible("me", true, "Timeout"))
    }

    @Test
    fun `sessions sort by day so the first sitting enrols`() {
        val day1 = CropRecord.sessionKey(0L, "normal", glasses = false, faceCovered = false)
        val day2 = CropRecord.sessionKey(86_400_000L * 3, "dim", glasses = true, faceCovered = false)
        assertEquals("19700101_normal", day1)
        assertEquals("19700104_dim_glasses", day2)
        assertTrue(day1 < day2)
    }

    @Test
    fun `the person key is short and does not contain a full id`() {
        val key = CropRecord.personKey("12345678-aaaa-bbbb", "abcdefgh-1111")
        assertEquals("12345678_abcdefgh", key)
    }

    @Test
    fun `nothing identifying by name is sent`() {
        val keys = record().toFields().keys.map { it.lowercase() }
        listOf("name", "email", "uid", "selfie", "embedding", "template").forEach { forbidden ->
            assertTrue("$forbidden must not be a field", keys.none { it.contains(forbidden) })
        }
    }
}
