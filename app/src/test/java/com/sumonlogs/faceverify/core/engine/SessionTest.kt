package com.sumonlogs.faceverify.core.engine

import com.sumonlogs.faceverify.core.api.FaceFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    private val config = FaceConfig()
    private val threshold = 0.42f
    private val alex = FaceRecord("a", "Alex", 0L, FaceModels.SFace.id, listOf(floatArrayOf(1f, 0f, 0f)))
    private val sam = FaceRecord("s", "Sam", 0L, FaceModels.SFace.id, listOf(floatArrayOf(0f, 0f, 1f)))

    private fun session(
        people: List<FaceRecord> = listOf(alex, sam),
        embedder: FaceEmbedder = FakeEmbedder(),
        spoof: SpoofDetector? = null,
    ) = VerificationSession(config, threshold, LivenessChallenge.Blink, people, embedder, spoof)

    /** Feeds frames 66 ms apart (≈15 fps). */
    private class Clock {
        var t = 0L
        fun next(): Long = t.also { t += 66 }
    }

    /** The app runs the final check on a worker; here it runs inline so the result can be asserted. */
    private fun VerifyStep.resolve(): VerifyStep =
        if (this is VerifyStep.Deciding) VerifyStep.Finished(check.run()) else this

    /** Plays align → blink → capture and returns the last step. */
    private fun VerificationSession.playLive(pixelTag: Int = 0, captureTrackingId: Int = 1): VerifyStep {
        val clock = Clock()
        fun feed(f: FaceObservation) = onFrame(FakeFrame(clock.next(), listOf(f), pixelTag)).resolve()
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
    fun `last capture frame hands back a check without running any model`() {
        val embedder = FakeEmbedder()
        val spoof = FakeSpoofDetector(0.95f)
        val s = session(embedder = embedder, spoof = spoof)
        val clock = Clock()
        fun feed(f: FaceObservation) = s.onFrame(FakeFrame(clock.next(), listOf(f)))
        repeat(config.stableFrames) { feed(face()) }
        listOf(0.95f, 0.05f, 0.95f).forEach { feed(face(eyes = it)) }
        var step: VerifyStep = VerifyStep.Align(null)
        repeat(12) { if (step !is VerifyStep.Deciding) step = feed(face()) }

        val deciding = step as VerifyStep.Deciding
        assertEquals("nothing heavy ran on the camera thread", 0, embedder.calls + spoof.calls)
        assertSame("later frames get the same check back", deciding, feed(face()))
        assertTrue(deciding.check.run().matched)
        assertEquals(1, embedder.calls)
        assertEquals(config.verifyFrames, spoof.calls)
    }

    @Test
    fun `real face passes anti-spoofing and is identified`() {
        val spoof = FakeSpoofDetector(0.95f)
        val result = (session(spoof = spoof).playLive() as VerifyStep.Finished).result
        assertTrue(result.matched)
        assertEquals("one score per capture frame", config.verifyFrames, spoof.calls)
    }

    @Test
    fun `replayed face that passes liveness is rejected as a spoof and never matched`() {
        val embedder = FakeEmbedder()
        val s = session(embedder = embedder, spoof = FakeSpoofDetector(0.1f))
        val result = (s.playLive() as VerifyStep.Finished).result
        assertFalse(result.matched)
        assertNull(result.face)
        assertEquals(FaceFailure.SpoofSuspected, result.failure)
        assertFalse("must stay out of calibration", result.livenessPassed)
        assertEquals("a spoof is never embedded", 0, embedder.calls)
    }

    @Test
    fun `one doubtful frame among good ones does not fail the check`() {
        // mean of 0.9, 0.4, 0.95 is above the 0.5 minimum
        val result = (session(spoof = FakeSpoofDetector(0.9f, 0.4f, 0.95f)).playLive() as VerifyStep.Finished).result
        assertTrue(result.matched)
    }

    @Test
    fun `anti-spoof model failure ends the check as an engine error`() {
        val result = (session(spoof = FakeSpoofDetector(fail = true)).playLive() as VerifyStep.Finished).result
        assertFalse(result.matched)
        assertEquals(FaceFailure.EngineError, result.failure)
    }

    @Test
    fun `a dark crop is rejected at once but the light hint waits for a lasting problem`() {
        val s = session()
        val clock = Clock()
        repeat(config.stableFrames) { s.onFrame(FakeFrame(clock.next(), listOf(face()))) }
        listOf(0.95f, 0.05f, 0.95f).forEach { s.onFrame(FakeFrame(clock.next(), listOf(face(eyes = it)))) }
        val hints = List(config.cropHintAfterFrames + 2) {
            (s.onFrame(FakeFrame(clock.next(), listOf(face()), gray = 20, contrast = 10)) as VerifyStep.Capture).issue
        }
        assertEquals("no flash on the first dark frames", List(config.cropHintAfterFrames - 1) { null }, hints.take(config.cropHintAfterFrames - 1))
        assertEquals(QualityIssue.TooDark, hints.last())
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
        )
        var step: EnrollStep = EnrollStep.Posing(EnrollPose.Front, emptySet(), null, 0f)
        repeat(160) {
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

    /** Embeds the n-th frame as [vectors][n], so a test decides which frames are outliers. */
    private class ScriptedEmbedder(private val vectors: List<FloatArray>) : FaceEmbedder {
        override val model = FaceModels.SFace
        override val inputSize = FAKE_CROP
        override fun embed(faces: List<IntArray>): List<FloatArray> = faces.indices.map { vectors[it] }
    }

    private val same = floatArrayOf(1f, 0f, 0f)
    private val odd = floatArrayOf(0f, 1f, 0f)

    @Test
    fun `an outlier enrollment frame is dropped, not fatal`() {
        val vectors = List(config.enrollFrontFrames) { if (it == 3) odd else same }
        val step = EnrollmentSession(config, ScriptedEmbedder(vectors)).play()
        val kept = (step as EnrollStep.Finished).templates
        assertEquals(config.enrollFrontFrames - 1, kept.size)
        assertTrue("the odd frame is gone", kept.none { it.contentEquals(odd) })
    }

    @Test
    fun `enrollment fails when too few frames agree`() {
        val agree = config.enrollMinKept - 1
        val vectors = List(config.enrollFrontFrames) { if (it < agree) same else if (it % 2 == 0) odd else floatArrayOf(0f, 0f, 1f) }
        val step = EnrollmentSession(config, ScriptedEmbedder(vectors)).play()
        assertEquals(EnrollFailure.Inconsistent, (step as EnrollStep.Failed).reason)
    }

    @Test
    fun `enrollment frames are spread out over time`() {
        val s = EnrollmentSession(config, FakeEmbedder())
        // Frames every 66 ms: only one in about five may be used, so 8 frames take about 2.5 s.
        var framesUsed = 0
        var t = 0L
        var lastFrames = 0f
        repeat(10) {
            val step = s.onFrame(FakeFrame(t, listOf(face()))) as EnrollStep.Posing
            if (step.progress > lastFrames) framesUsed++
            lastFrames = step.progress
            t += 66
        }
        assertTrue("took $framesUsed of 10 frames in 660 ms", framesUsed <= 2)
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
        repeat(80) { s.onFrame(FakeFrame(clock.next(), listOf(face()))) }
        val step = s.onFrame(FakeFrame(clock.next(), listOf(face(yaw = -25f))))
        assertEquals(EnrollPose.Left, (step as EnrollStep.Posing).pose)
        assertEquals(setOf(EnrollPose.Front), step.captured)
        assertEquals("front done is 1 of 3 poses", 1f / 3f, step.progress, 1e-6f)
    }
}
