package com.sumonlogs.faceverify.core.engine

import kotlin.math.abs

enum class EnrollPose { Front, Left, Right, Up }

enum class EnrollFailure { Inconsistent, EngineError }

sealed interface EnrollStep {
    data class Posing(
        val pose: EnrollPose,
        val captured: Set<EnrollPose>,
        val issue: QualityIssue?,
        /** 0..1 over the whole enrollment. */
        val progress: Float,
    ) : EnrollStep

    /** The front-facing embeddings that make up the new person's template. */
    data class Finished(val templates: List<FloatArray>) : EnrollStep

    data class Failed(val reason: EnrollFailure) : EnrollStep
}

/**
 * Enrolls one person. First [FaceConfig.enrollFrontFrames] good front-facing frames are captured;
 * those become the template. Then left, right and up must each be held for [FaceConfig.stableFrames]
 * frames: that proves a live, moving head, but those side views are not stored, because turned faces
 * make a template less specific. The same tracked face must stay in view; if it changes, it starts over.
 */
class EnrollmentSession(
    private val config: FaceConfig,
    private val embedder: FaceEmbedder,
) {
    private val poses = EnrollPose.entries
    private val done = linkedSetOf<EnrollPose>()
    private val crops = mutableListOf<IntArray>()
    private var lastCropAtMs: Long? = null
    private var goodFrames = 0
    private var lockedTrackingId: Int? = null
    private var result: EnrollStep? = null

    var lastFace: FaceObservation? = null
        private set
    var lastCrop: CropStats? = null
        private set

    private val currentPose: EnrollPose get() = poses.firstOrNull { it !in done } ?: poses.last()

    fun onFrame(frame: FaceFrame): EnrollStep {
        result?.let { return it }
        val pose = currentPose
        val q = FrameQuality.check(frame.faces, frame.width, frame.height, config, requireFront = pose == EnrollPose.Front)
        lastFace = when (q) {
            is QualityCheck.Good -> q.face
            is QualityCheck.Bad -> q.face
        }
        val face = (q as? QualityCheck.Good)?.face
        if (face == null) {
            goodFrames = 0
            return posing((q as? QualityCheck.Bad)?.issue)
        }
        if (isDifferentPerson(face)) return restart()
        if (lockedTrackingId == null) lockedTrackingId = face.trackingId

        if (!isInPose(face, pose)) {
            goodFrames = 0
            return posing(null)
        }
        return if (pose == EnrollPose.Front) front(frame, face) else sidePose(pose)
    }

    private fun front(frame: FaceFrame, face: FaceObservation): EnrollStep {
        if (tooSoon(frame.timestampMs)) return posing(null)
        val pixels = frame.alignedFace(face) ?: return fail(EnrollFailure.EngineError)
        val stats = CropQuality.stats(pixels, embedder.inputSize)
        lastCrop = stats
        val issue = CropQuality.issue(stats, config)
        if (issue != null) return posing(issue)
        crops += pixels
        lastCropAtMs = frame.timestampMs
        if (crops.size >= config.enrollFrontFrames) done += EnrollPose.Front
        return posing(null)
    }

    private fun sidePose(pose: EnrollPose): EnrollStep {
        if (++goodFrames < config.stableFrames) return posing(null)
        goodFrames = 0
        done += pose
        return if (done.size == poses.size) finish() else posing(null)
    }

    private fun finish(): EnrollStep {
        val embeddings = try {
            embedder.embed(crops)
        } catch (e: Exception) {
            return fail(EnrollFailure.EngineError)
        }
        val centre = FaceMatcher.centroid(embeddings)
        val consistent = embeddings.all { FaceMatcher.cosine(centre, it) >= config.enrollConsistencyMin }
        if (!consistent) return fail(EnrollFailure.Inconsistent)
        return EnrollStep.Finished(embeddings).also { result = it }
    }

    private fun posing(issue: QualityIssue?): EnrollStep.Posing {
        val frontShare = (crops.size.toFloat() / config.enrollFrontFrames).coerceAtMost(1f)
        val sideDone = done.count { it != EnrollPose.Front }
        return EnrollStep.Posing(
            pose = currentPose,
            captured = done.toSet(),
            issue = issue,
            progress = (frontShare + sideDone) / poses.size,
        )
    }

    private fun restart(): EnrollStep {
        done.clear()
        crops.clear()
        lastCropAtMs = null
        goodFrames = 0
        lockedTrackingId = null
        return posing(QualityIssue.FaceChanged)
    }

    private fun fail(reason: EnrollFailure): EnrollStep = EnrollStep.Failed(reason).also { result = it }

    private fun isDifferentPerson(face: FaceObservation): Boolean {
        val locked = lockedTrackingId ?: return false
        val current = face.trackingId ?: return false
        return locked != current
    }

    private fun isInPose(face: FaceObservation, pose: EnrollPose): Boolean = when (pose) {
        EnrollPose.Front -> FrameQuality.isFront(face, config) && FrameQuality.eyesOpen(face, config)
        EnrollPose.Left -> face.yaw >= config.enrollTurnYawDeg && abs(face.pitch) <= config.maxFrontPitchDeg * 1.5f
        EnrollPose.Right -> face.yaw <= -config.enrollTurnYawDeg && abs(face.pitch) <= config.maxFrontPitchDeg * 1.5f
        EnrollPose.Up -> face.pitch >= config.enrollUpPitchDeg && abs(face.yaw) <= config.maxFrontYawDeg
    }

    /** Frames used for embeddings must be [FaceConfig.captureSpacingMs] apart, so they aren't near-duplicates. */
    private fun tooSoon(timestampMs: Long): Boolean {
        val last = lastCropAtMs ?: return false
        return timestampMs - last < config.captureSpacingMs
    }
}
