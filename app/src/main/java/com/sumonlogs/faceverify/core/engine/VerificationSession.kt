package com.sumonlogs.faceverify.core.engine

import com.sumonlogs.faceverify.core.api.EnrolledFace
import com.sumonlogs.faceverify.core.api.FaceFailure
import com.sumonlogs.faceverify.core.api.FaceMatchResult

sealed interface VerifyStep {
    /** Step 1 of 3: get one face centred and still. */
    data class Align(val issue: QualityIssue?) : VerifyStep

    /** Step 2 of 3: the liveness challenge. */
    data class Liveness(val challenge: LivenessChallenge, val progress: Float, val issue: QualityIssue?) : VerifyStep

    /** Step 3 of 3: facing the camera again while the match frames are taken. */
    data class Capture(val issue: QualityIssue?, val progress: Float) : VerifyStep

    data class Finished(val result: FaceMatchResult) : VerifyStep
}

data class VerifyTimings(val detectMs: Long, val embedMs: Long?, val totalMs: Long)

/**
 * One face check: align → liveness challenge → capture [FaceConfig.verifyFrames] good front frames →
 * identify among [people] using the average of those frames. The same tracked face must be seen from
 * the challenge to the last capture, otherwise it starts over.
 */
class VerificationSession(
    private val config: FaceConfig,
    private val threshold: Float,
    val challenge: LivenessChallenge,
    private val people: List<FaceRecord>,
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
    private val crops = mutableListOf<IntArray>()
    private var lastCropAtMs: Long? = null
    private var selfie: ByteArray? = null
    private var finished: VerifyStep.Finished? = null

    var lastFace: FaceObservation? = null
        private set
    var lastCrop: CropStats? = null
        private set
    var timings: VerifyTimings? = null
        private set
    var identification: Identification? = null
        private set

    /** Id of the best-scoring enrolled person (matched or not), for calibration. */
    var bestFaceId: String? = null
        private set

    fun onFrame(frame: FaceFrame): VerifyStep {
        finished?.let { return it }
        val start = startedAtMs ?: frame.timestampMs.also { startedAtMs = it }
        val elapsed = frame.timestampMs - start
        timings = VerifyTimings(detectMs = frame.detectMs, embedMs = timings?.embedMs, totalMs = elapsed)

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
                VerifyStep.Capture(null, 0f)
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
        val q = check(frame, requireFront = true)
        val face = (q as? QualityCheck.Good)?.face ?: return VerifyStep.Capture(lastIssue, captureProgress())
        if (isDifferentPerson(face)) return restart()
        if (tooSoon(frame.timestampMs)) return VerifyStep.Capture(null, captureProgress())

        val pixels = frame.alignedFace(face)
            ?: return finish(FaceMatchResult.failed(FaceFailure.EngineError, frame.selfieJpeg(), livenessPassed = true))
        val stats = CropQuality.stats(pixels, embedder.inputSize)
        lastCrop = stats
        val cropIssue = CropQuality.issue(stats, config)
        if (cropIssue != null) {
            lastIssue = cropIssue
            return VerifyStep.Capture(cropIssue, captureProgress())
        }
        if (crops.isEmpty()) selfie = frame.selfieJpeg()
        crops += pixels
        lastCropAtMs = frame.timestampMs
        if (crops.size < config.verifyFrames) return VerifyStep.Capture(null, captureProgress())
        return finish(identify())
    }

    private fun identify(): FaceMatchResult {
        val t0 = nanoTime()
        val probe = try {
            FaceMatcher.centroid(embedder.embed(crops))
        } catch (e: Exception) {
            return FaceMatchResult.failed(FaceFailure.EngineError, selfie, livenessPassed = true)
        }
        timings = timings?.copy(embedMs = (nanoTime() - t0) / 1_000_000)
        val result = FaceMatcher.identify(probe, people.map { it.templates }, threshold, config.identifyMargin)
        identification = result
        val best = result.bestIndex?.let(people::get)
        bestFaceId = best?.id
        return when {
            result.matched && best != null -> FaceMatchResult(
                matched = true,
                face = EnrolledFace(best.id, best.name, best.enrolledAtMillis, best.modelId),
                score = result.score,
                livenessPassed = true,
                selfie = selfie,
                failure = null,
            )
            result.ambiguous -> FaceMatchResult.failed(FaceFailure.AmbiguousMatch, selfie, livenessPassed = true, score = result.score)
            else -> FaceMatchResult(
                matched = false,
                face = null,
                score = result.score,
                livenessPassed = true,
                selfie = selfie,
                failure = null,
            )
        }
    }

    private fun captureProgress(): Float = crops.size.toFloat() / config.verifyFrames

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
        crops.clear()
        lastCropAtMs = null
        selfie = null
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
        val evidence = if (lastIssue == QualityIssue.NoFace) null else selfie ?: frame.selfieJpeg()
        return FaceMatchResult.failed(failure, evidence, livenessPassed = stage == Stage.Capture)
    }

    private fun finish(result: FaceMatchResult): VerifyStep.Finished =
        VerifyStep.Finished(result).also { finished = it }

    /** Frames used for embeddings must be [FaceConfig.captureSpacingMs] apart, so they aren't near-duplicates. */
    private fun tooSoon(timestampMs: Long): Boolean {
        val last = lastCropAtMs ?: return false
        return timestampMs - last < config.captureSpacingMs
    }
}
