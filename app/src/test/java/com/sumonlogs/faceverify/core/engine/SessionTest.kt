package com.sumonlogs.faceverify.core.engine

import com.sumonlogs.faceverify.core.api.FaceFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    private val config = FaceConfig()
    private val threshold = 0.42f
    private val alex = FaceRecord("a", "Alex", 0L, FaceModels.SFace.id, listOf(floatArrayOf(1f, 0f, 0f)))
    private val sam = FaceRecord("s", "Sam", 0L, FaceModels.SFace.id, listOf(floatArrayOf(0f, 0f, 1f)))

    private fun session(people: List<FaceRecord> = listOf(alex, sam), embedder: FaceEmbedder = FakeEmbedder()) =
        VerificationSession(config, threshold, LivenessChallenge.Blink, people, embedder)

    /** Feeds frames 66 ms apart (≈15 fps). */
    private class Clock {
        var t = 0L
        fun next(): Long = t.also { t += 66 }
    }

    /** Plays align → blink → capture and returns the last step. */
    private fun VerificationSession.playLive(pixelTag: Int = 0, captureTrackingId: Int = 1): VerifyStep {
        val clock = Clock()
        fun feed(f: FaceObservation) = onFrame(FakeFrame(clock.next(), listOf(f), pixelTag))
        repeat(config.stableFrames) { feed(face()) }
        feed(face(eyes = 0.95f))
        feed(face(eyes = 0.05f))
        feed(face(eyes = 0.95f))
        var last: VerifyStep = VerifyStep.Align(null)
        repeat(12) { if (last !is VerifyStep.Finished) last = feed(face(trackingId = captureTrackingId)) }
        return last
    }

    @Test
    fun `live enrolled face is identified from several averaged frames`() {
        val embedder = FakeEmbedder()
        val result = (session(embedder = embedder).playLive() as VerifyStep.Finished).result
        assertTrue(result.matched)
        assertEquals("a", result.face?.id)
        assertTrue(result.selfie != null)
        assertEquals("all capture frames are embedded in one call", 1, embedder.calls)
    }

    @Test
    fun `live stranger matches nobody but still returns a result`() {
        val result = (session().playLive(pixelTag = 7) as VerifyStep.Finished).result
        assertFalse(result.matched)
        assertNull(result.face)
        assertTrue(result.livenessPassed)
        assertNull(result.failure)
    }

    @Test
    fun `two enrolled people too alike gives an ambiguous result`() {
        val twin = FaceRecord("t", "Twin", 0L, FaceModels.SFace.id, listOf(floatArrayOf(1f, 0.01f, 0f)))
        val result = (session(listOf(alex, twin)).playLive() as VerifyStep.Finished).result
        assertFalse(result.matched)
        assertEquals(FaceFailure.AmbiguousMatch, result.failure)
    }

    @Test
    fun `face swapped after the challenge restarts the check`() {
        val s = session()
        val clock = Clock()
        repeat(config.stableFrames) { s.onFrame(FakeFrame(clock.next(), listOf(face()))) }
        listOf(0.95f, 0.05f, 0.95f).forEach { s.onFrame(FakeFrame(clock.next(), listOf(face(eyes = it)))) }
        assertTrue(s.onFrame(FakeFrame(clock.next(), listOf(face()))) is VerifyStep.Capture)
        // A different tracked face appears: the new person has to start over, liveness included.
        assertTrue(s.onFrame(FakeFrame(clock.next(), listOf(face(trackingId = 2)))) is VerifyStep.Align)
    }

    @Test
    fun `dark face crop is not used for matching`() {
        val s = session()
        val clock = Clock()
        repeat(config.stableFrames) { s.onFrame(FakeFrame(clock.next(), listOf(face()))) }
        listOf(0.95f, 0.05f, 0.95f).forEach { s.onFrame(FakeFrame(clock.next(), listOf(face(eyes = it)))) }
        var step: VerifyStep = VerifyStep.Align(null)
        repeat(10) { step = s.onFrame(FakeFrame(clock.next(), listOf(face()), gray = 20, contrast = 10)) }
        assertEquals(QualityIssue.TooDark, (step as VerifyStep.Capture).issue)
    }

    @Test
    fun `static photo fails liveness`() {
        val s = session()
        var t = 0L
        var step: VerifyStep = VerifyStep.Align(null)
        while (step !is VerifyStep.Finished) {
            step = s.onFrame(FakeFrame(t, listOf(face())))
            t += 66
        }
        assertEquals(FaceFailure.LivenessFailed, step.result.failure)
        assertFalse(step.result.matched)
    }

    @Test
    fun `nobody in front of the camera times out as NoFace without a selfie`() {
        val s = session()
        s.onFrame(FakeFrame(0, emptyList()))
        val result = (s.onFrame(FakeFrame(config.verifyTimeoutMs + 1, emptyList())) as VerifyStep.Finished).result
        assertEquals(FaceFailure.NoFace, result.failure)
        assertNull(result.selfie)
    }

    // ---------------------------------------------------------------- Enrollment

    /** Front until all front frames are taken, then each side pose for a while. */
    private fun EnrollmentSession.play(tagFor: (EnrollPose) -> Int = { 0 }, trackingFor: (EnrollPose) -> Int = { 1 }): EnrollStep {
        val clock = Clock()
        val poses = mapOf(
            EnrollPose.Front to face(),
            EnrollPose.Left to face(yaw = 25f),
            EnrollPose.Right to face(yaw = -25f),
            EnrollPose.Up to face(pitch = 18f),
        )
        var step: EnrollStep = EnrollStep.Posing(EnrollPose.Front, emptySet(), null, 0f)
        repeat(60) {
            val pose = (step as? EnrollStep.Posing)?.pose ?: return step
            val f = poses.getValue(pose).copy(trackingId = trackingFor(pose))
            step = onFrame(FakeFrame(clock.next(), listOf(f), tagFor(pose)))
        }
        return step
    }

    @Test
    fun `enrollment stores only the front frames`() {
        val step = EnrollmentSession(config, FakeEmbedder()).play()
        assertEquals(config.enrollFrontFrames, (step as EnrollStep.Finished).templates.size)
    }

    @Test
    fun `enrollment restarts when a different person appears`() {
        val s = EnrollmentSession(config, FakeEmbedder())
        val step = s.play(trackingFor = { if (it == EnrollPose.Left) 2 else 1 })
        // Never finishes: every time Left comes, the face changed and it starts over.
        assertFalse(step is EnrollStep.Finished)
    }

    @Test
    fun `enrollment waits while pose is wrong`() {
        val s = EnrollmentSession(config, FakeEmbedder())
        val clock = Clock()
        repeat(20) { s.onFrame(FakeFrame(clock.next(), listOf(face()))) }
        val step = s.onFrame(FakeFrame(clock.next(), listOf(face(yaw = -25f))))
        assertEquals(EnrollPose.Left, (step as EnrollStep.Posing).pose)
        assertEquals(setOf(EnrollPose.Front), step.captured)
        assertEquals(0.25f, step.progress, 1e-6f)
    }
}
