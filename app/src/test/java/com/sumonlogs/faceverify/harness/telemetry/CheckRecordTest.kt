package com.sumonlogs.faceverify.harness.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CheckRecordTest {

    private fun record(
        expectedFaceId: String? = "face-1",
        runnerUp: Float? = 0.2f,
        timings: Map<String, Float> = mapOf("embed" to 31.5f),
        failure: String? = "Timeout",
        qualityIssue: String? = "TooDark",
    ) = CheckRecord(
        installId = "install-1", appVersion = "1.0", device = "Acme Phone", sdk = 34, pipelineVersion = 2, takenAtMillis = 1_000L,
        modelId = "sface-2021dec", threshold = 0.42f,
        scenario = "me", lighting = "dim", glasses = true, faceCovered = false,
        expectedFaceId = expectedFaceId, matchedFaceId = "face-1", enrolledCount = 2,
        matched = true, failure = failure, score = 0.71f, runnerUp = runnerUp, spoofScore = 0.9f,
        challenge = "Blink", livenessPassed = true,
        faceBrightness = 88f, faceSharpness = 120f, qualityIssue = qualityIssue,
        detectMs = 18, embedMs = 60, totalMs = 2_400,
        timingAvgMs = timings, timingP95Ms = timings,
    )

    /** The `hasOnly([...])` list in firestore.rules, read from the file the testers' database really uses. */
    private fun allowedKeysInRules(): Set<String> {
        val rules = listOf(File("../firestore.rules"), File("firestore.rules")).first { it.exists() }.readText()
        val list = Regex("""hasOnly\(\[(.*?)]\)""", RegexOption.DOT_MATCHES_ALL).find(rules)!!.groupValues[1]
        return Regex("""'([A-Za-z0-9]+)'""").findAll(list).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun `every field the app sends is allowed by the database rules`() {
        val sent = record().toFields().keys
        val allowed = allowedKeysInRules()
        assertTrue("not allowed by firestore.rules: ${sent - allowed}", allowed.containsAll(sent))
        assertTrue("receivedAt is added by the server", "receivedAt" in allowed)
    }

    @Test
    fun `the rules do not allow a field the app never sends`() {
        val unused = allowedKeysInRules() - record().toFields().keys - "receivedAt"
        assertTrue("dead entries in firestore.rules: $unused", unused.isEmpty())
    }

    @Test
    fun `empty values are left out`() {
        val fields = record(expectedFaceId = null, runnerUp = null, timings = emptyMap()).toFields()
        assertFalse("expectedFaceId" in fields)
        assertFalse("runnerUp" in fields)
        assertFalse("timingAvgMs" in fields)
        assertEquals("sface-2021dec", fields["modelId"])
    }

    @Test
    fun `nothing that identifies a person or a face is sent`() {
        val keys = record().toFields().keys.map { it.lowercase() }
        listOf("name", "image", "photo", "selfie", "embedding", "template", "email", "uid").forEach { forbidden ->
            assertTrue("$forbidden must not be a field", keys.none { it.contains(forbidden) })
        }
    }

    @Test
    fun `the schema version is sent so old and new rows can be told apart`() {
        assertEquals(CheckRecord.SCHEMA, record().toFields()["schema"])
    }
}
