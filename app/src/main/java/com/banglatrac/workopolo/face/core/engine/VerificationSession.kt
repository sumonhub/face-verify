package com.banglatrac.workopolo.face.core.engine

import com.banglatrac.workopolo.face.core.api.FaceFailure
import com.banglatrac.workopolo.face.core.api.FaceMatchResult

sealed interface VerifyStep {
    /** Step 1 of 3: get one face centred and still. */
    data class Align(val issue: QualityIssue?) : VerifyStep

    /** Step 2 of 3: the liveness challenge. */
    data class Liveness(val challenge: LivenessChallenge, val progress: Float, val issue: QualityIssue?) : VerifyStep

    /** Step 3 of 3: back to facing the camera; the match frame is taken here. */
    data class Capture(val issue: QualityIssue?) : VerifyStep

    data class Finished(val result: FaceMatchResult) : VerifyStep
}

data class VerifyTimings(val detectMs: Long, val embedMs: Long?, val totalMs: Long)

/**
 * One face check: align → liveness challenge → capture and match. Feed it every analysed frame.
 * The same tracked face must be seen from the challenge to the capture, otherwise it starts over.
 */
class VerificationSession(
    private val config: FaceConfig,
    val challenge: LivenessChallenge,
    private val templates: List<FloatArray>,
    private val embedder: FaceEmbedder,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private enum class Stage { Align, Liveness, Capture }

    private var stage = Stage.Align
    private var goodFrames = 0
    private var startedAtMs: Long? = null
    private var lockedTrackingId: Int? = null
    private var liveness = LivenessChecker(challenge, config)
    private var livenessProgress = 0f
    private var lastIssue: QualityIssue? = null
    private var finished: VerifyStep.Finished? = null

    var lastFace: FaceObservation? = null
        private set
    var timings: VerifyTimings? = null
        private set

    fun onFrame(frame: FaceFrame): VerifyStep {
        finished?.let { return it }
        val start = startedAtMs ?: frame.timestampMs.also { startedAtMs = it }
        val elapsed = frame.timestampMs - start
        timings = VerifyTimings(detectMs = frame.detectMs, embedMs = null, totalMs = elapsed)

        if (elapsed > config.verifyTimeoutMs) return finish(timeoutResult(frame))

        return when (stage) {
            Stage.Align -> align(frame)
            Stage.Liveness -> liveness(frame)
            Stage.Capture -> capture(frame)
        }
    }

    private fun align(frame: FaceFrame): VerifyStep {
        when (val q = check(frame, requireFront = true)) {
            is QualityCheck.Good -> {
                if (++goodFrames >= config.stableFrames) {
                    lockedTrackingId = q.face.trackingId
                    goodFrames = 0
                    stage = Stage.Liveness
                    return VerifyStep.Liveness(challenge, 0f, null)
                }
            }
            is QualityCheck.Bad -> goodFrames = 0
        }
        return VerifyStep.Align(lastIssue)
    }

    private fun liveness(frame: FaceFrame): VerifyStep {
        val q = check(frame, requireFront = false)
        val face = (q as? QualityCheck.Good)?.face
            ?: return VerifyStep.Liveness(challenge, livenessProgress, lastIssue)
        if (isDifferentPerson(face)) return restart()

        return when (val status = liveness.onFace(face, frame.timestampMs)) {
            LivenessStatus.Passed -> {
                stage = Stage.Capture
                VerifyStep.Capture(null)
            }
            LivenessStatus.TimedOut -> finish(
                FaceMatchResult.failed(FaceFailure.LivenessFailed, selfie = frame.selfieJpeg()),
            )
            is LivenessStatus.InProgress -> {
                livenessProgress = status.progress
                VerifyStep.Liveness(challenge, status.progress, null)
            }
        }
    }

    private fun capture(frame: FaceFrame): VerifyStep {
        when (val q = check(frame, requireFront = true)) {
            is QualityCheck.Good -> {
                if (isDifferentPerson(q.face)) return restart()
                if (++goodFrames >= 2) return finish(match(frame, q.face))
            }
            is QualityCheck.Bad -> goodFrames = 0
        }
        return VerifyStep.Capture(lastIssue)
    }

    private fun match(frame: FaceFrame, face: FaceObservation): FaceMatchResult {
        val selfie = frame.selfieJpeg()
        val pixels = frame.alignedFace(face)
            ?: return FaceMatchResult.failed(FaceFailure.EngineError, selfie, livenessPassed = true)
        val t0 = nanoTime()
        val probe = try {
            embedder.embed(pixels)
        } catch (e: Exception) {
            return FaceMatchResult.failed(FaceFailure.EngineError, selfie, livenessPassed = true)
        }
        val embedMs = (nanoTime() - t0) / 1_000_000
        timings = timings?.copy(embedMs = embedMs)
        val score = FaceMatcher.match(probe, templates, config.matchThreshold)
        return FaceMatchResult(
            matched = score.matched,
            score = score.score,
            livenessPassed = true,
            selfie = selfie,
            failure = null,
        )
    }

    private fun check(frame: FaceFrame, requireFront: Boolean): QualityCheck {
        val q = FrameQuality.check(frame.faces, frame.width, frame.height, config, requireFront)
        lastIssue = (q as? QualityCheck.Bad)?.issue
        lastFace = when (q) {
            is QualityCheck.Good -> q.face
            is QualityCheck.Bad -> q.face
        }
        return q
    }

    private fun isDifferentPerson(face: FaceObservation): Boolean {
        val locked = lockedTrackingId ?: return false
        val current = face.trackingId ?: return false
        return locked != current
    }

    private fun restart(): VerifyStep {
        stage = Stage.Align
        goodFrames = 0
        lockedTrackingId = null
        liveness = LivenessChecker(challenge, config)
        livenessProgress = 0f
        return VerifyStep.Align(null)
    }

    private fun timeoutResult(frame: FaceFrame): FaceMatchResult {
        val failure = when (stage) {
            Stage.Liveness -> FaceFailure.LivenessFailed
            Stage.Align, Stage.Capture -> when (lastIssue) {
                QualityIssue.NoFace -> FaceFailure.NoFace
                QualityIssue.MultipleFaces -> FaceFailure.MultipleFaces
                null -> FaceFailure.Timeout
                else -> FaceFailure.PoorQuality
            }
        }
        val selfie = if (lastIssue == QualityIssue.NoFace) null else frame.selfieJpeg()
        return FaceMatchResult.failed(failure, selfie, livenessPassed = stage == Stage.Capture)
    }

    private fun finish(result: FaceMatchResult): VerifyStep.Finished =
        VerifyStep.Finished(result).also { finished = it }
}
