package com.banglatrac.workopolo.face.core.engine

import com.banglatrac.workopolo.face.core.api.FaceFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    private val config = FaceConfig()
    private val enrolled = listOf(floatArrayOf(1f, 0f, 0f))

    private fun session() = VerificationSession(config, LivenessChallenge.Blink, enrolled, FakeEmbedder())

    /** Plays align → blink → capture and returns the last step. */
    private fun VerificationSession.playLive(pixelTag: Int = 0, captureTrackingId: Int = 1): VerifyStep {
        var t = 0L
        fun feed(f: FaceObservation) = onFrame(FakeFrame(t, listOf(f), pixelTag)).also { t += 66 }
        repeat(config.stableFrames) { feed(face()) }
        feed(face(eyes = 0.95f))
        feed(face(eyes = 0.05f))
        feed(face(eyes = 0.95f))
        var last: VerifyStep = VerifyStep.Align(null)
        repeat(3) { last = feed(face(trackingId = captureTrackingId)) }
        return last
    }

    @Test
    fun `live enrolled face matches`() {
        val step = session().playLive()
        val result = (step as VerifyStep.Finished).result
        assertTrue(result.matched)
        assertTrue(result.livenessPassed)
        assertEquals(null, result.failure)
        assertTrue(result.selfie != null)
    }

    @Test
    fun `live stranger does not match but still returns a result`() {
        val result = (session().playLive(pixelTag = 7) as VerifyStep.Finished).result
        assertFalse(result.matched)
        assertTrue(result.livenessPassed)
        assertEquals(null, result.failure)
    }

    @Test
    fun `face swapped after the challenge restarts the check`() {
        assertTrue(session().playLive(captureTrackingId = 2) is VerifyStep.Align)
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
        val step = s.onFrame(FakeFrame(0, emptyList())).let { s.onFrame(FakeFrame(config.verifyTimeoutMs + 1, emptyList())) }
        val result = (step as VerifyStep.Finished).result
        assertEquals(FaceFailure.NoFace, result.failure)
        assertEquals(null, result.selfie)
    }

    @Test
    fun `enrollment captures four poses`() {
        val s = EnrollmentSession(config, FakeEmbedder())
        val poses = listOf(face(), face(yaw = 25f), face(yaw = -25f), face(pitch = 18f))
        var step: EnrollStep = EnrollStep.Posing(EnrollPose.Front, emptySet(), null)
        var t = 0L
        poses.forEach { p -> repeat(config.stableFrames) { step = s.onFrame(FakeFrame(t, listOf(p))); t += 66 } }
        assertEquals(4, (step as EnrollStep.Finished).templates.size)
    }

    @Test
    fun `enrollment with two different people fails`() {
        val s = EnrollmentSession(config, FakeEmbedder())
        val poses = listOf(face() to 0, face(yaw = 25f) to 7, face(yaw = -25f) to 0, face(pitch = 18f) to 0)
        var step: EnrollStep = EnrollStep.Posing(EnrollPose.Front, emptySet(), null)
        var t = 0L
        poses.forEach { (p, tag) -> repeat(config.stableFrames) { step = s.onFrame(FakeFrame(t, listOf(p), tag)); t += 66 } }
        assertEquals(EnrollStep.Failed(EnrollFailure.Inconsistent), step)
    }

    @Test
    fun `enrollment waits while pose is wrong`() {
        val s = EnrollmentSession(config, FakeEmbedder())
        repeat(config.stableFrames) { s.onFrame(FakeFrame(it * 66L, listOf(face()))) }
        val step = s.onFrame(FakeFrame(1000, listOf(face(yaw = -25f))))
        assertEquals(EnrollPose.Left, (step as EnrollStep.Posing).pose)
        assertEquals(setOf(EnrollPose.Front), step.captured)
    }
}
