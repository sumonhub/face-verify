package com.sumonlogs.faceverify.core.engine

import kotlin.math.abs

enum class EnrollPose { Front, Left, Right, Up }

enum class EnrollFailure { Inconsistent, EngineError }

sealed interface EnrollStep {
    data class Posing(
        val pose: EnrollPose,
        val captured: Set<EnrollPose>,
        val issue: QualityIssue?,
    ) : EnrollStep {
        val progress: Float get() = captured.size.toFloat() / EnrollPose.entries.size
    }

    /** One embedding per pose, in [EnrollPose] order. */
    data class Finished(val templates: List<FloatArray>) : EnrollStep

    data class Failed(val reason: EnrollFailure) : EnrollStep
}

/**
 * Captures one embedding per pose (front, left, right, up). Each pose must be held for
 * [FaceConfig.stableFrames] frames. At the end every pose must look like the same person as the front one.
 */
class EnrollmentSession(
    private val config: FaceConfig,
    private val embedder: FaceEmbedder,
) {
    private val poses = EnrollPose.entries
    private val templates = linkedMapOf<EnrollPose, FloatArray>()
    private var goodFrames = 0
    private var done: EnrollStep? = null

    var lastFace: FaceObservation? = null
        private set

    val currentPose: EnrollPose get() = poses.firstOrNull { it !in templates } ?: poses.last()

    fun onFrame(frame: FaceFrame): EnrollStep {
        done?.let { return it }
        val pose = currentPose
        val q = FrameQuality.check(frame.faces, frame.width, frame.height, config, requireFront = pose == EnrollPose.Front)
        lastFace = when (q) {
            is QualityCheck.Good -> q.face
            is QualityCheck.Bad -> q.face
        }
        val face = (q as? QualityCheck.Good)?.face
        if (face == null || !isInPose(face, pose)) {
            goodFrames = 0
            return EnrollStep.Posing(pose, templates.keys.toSet(), (q as? QualityCheck.Bad)?.issue)
        }
        if (++goodFrames < config.stableFrames) return EnrollStep.Posing(pose, templates.keys.toSet(), null)

        goodFrames = 0
        val pixels = frame.alignedFace(face) ?: return fail(EnrollFailure.EngineError)
        templates[pose] = try {
            embedder.embed(pixels)
        } catch (e: Exception) {
            return fail(EnrollFailure.EngineError)
        }
        if (templates.size < poses.size) return EnrollStep.Posing(currentPose, templates.keys.toSet(), null)
        return finish()
    }

    private fun finish(): EnrollStep {
        val front = templates.getValue(EnrollPose.Front)
        val consistent = templates.values.all { FaceMatcher.cosine(front, it) >= config.enrollConsistencyMin }
        if (!consistent) return fail(EnrollFailure.Inconsistent)
        return EnrollStep.Finished(poses.map { templates.getValue(it) }).also { done = it }
    }

    private fun fail(reason: EnrollFailure): EnrollStep = EnrollStep.Failed(reason).also { done = it }

    private fun isInPose(face: FaceObservation, pose: EnrollPose): Boolean = when (pose) {
        EnrollPose.Front -> FrameQuality.isFront(face, config)
        EnrollPose.Left -> face.yaw >= config.enrollTurnYawDeg && abs(face.pitch) <= config.maxFrontPitchDeg * 1.5f
        EnrollPose.Right -> face.yaw <= -config.enrollTurnYawDeg && abs(face.pitch) <= config.maxFrontPitchDeg * 1.5f
        EnrollPose.Up -> face.pitch >= config.enrollUpPitchDeg && abs(face.yaw) <= config.maxFrontYawDeg
    }
}
