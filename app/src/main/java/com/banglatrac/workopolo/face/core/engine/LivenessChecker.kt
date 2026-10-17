package com.banglatrac.workopolo.face.core.engine

import kotlin.math.abs

enum class LivenessChallenge { Blink, TurnLeft, TurnRight }

sealed interface LivenessStatus {
    data class InProgress(val progress: Float) : LivenessStatus
    data object Passed : LivenessStatus
    data object TimedOut : LivenessStatus
}

/**
 * Active liveness: the person must blink, or turn their head, within [FaceConfig.livenessTimeoutMs].
 * A printed photo can't blink or change its head angle, so it never passes.
 */
class LivenessChecker(
    val challenge: LivenessChallenge,
    private val config: FaceConfig,
) {
    private enum class Phase { NeedBaseline, NeedAction, NeedRecovery, Done }

    private var phase = Phase.NeedBaseline
    private var startedAtMs: Long? = null

    fun onFace(face: FaceObservation, timestampMs: Long): LivenessStatus {
        if (phase == Phase.Done) return LivenessStatus.Passed
        val start = startedAtMs ?: timestampMs.also { startedAtMs = it }
        if (timestampMs - start > config.livenessTimeoutMs) return LivenessStatus.TimedOut

        return when (challenge) {
            LivenessChallenge.Blink -> blink(face)
            LivenessChallenge.TurnLeft -> turn(face, direction = 1f)
            LivenessChallenge.TurnRight -> turn(face, direction = -1f)
        }
    }

    /** Open → closed → open again. Frames without eye data are ignored. */
    private fun blink(face: FaceObservation): LivenessStatus {
        val left = face.leftEyeOpen
        val right = face.rightEyeOpen
        if (left == null || right == null) return progress()
        val open = left >= config.eyeOpenMin && right >= config.eyeOpenMin
        val closed = left <= config.eyeClosedMax && right <= config.eyeClosedMax
        phase = when (phase) {
            Phase.NeedBaseline -> if (open) Phase.NeedAction else phase
            Phase.NeedAction -> if (closed) Phase.NeedRecovery else phase
            Phase.NeedRecovery -> if (open) Phase.Done else phase
            Phase.Done -> phase
        }
        return progress()
    }

    /** Facing the camera first, then turned at least [FaceConfig.turnYawDeg] the asked way. */
    private fun turn(face: FaceObservation, direction: Float): LivenessStatus {
        val yaw = face.yaw * direction
        phase = when (phase) {
            Phase.NeedBaseline -> if (abs(face.yaw) <= config.maxFrontYawDeg) Phase.NeedAction else phase
            Phase.NeedAction, Phase.NeedRecovery -> if (yaw >= config.turnYawDeg) Phase.Done else Phase.NeedAction
            Phase.Done -> phase
        }
        if (phase == Phase.NeedAction) {
            return LivenessStatus.InProgress((yaw / config.turnYawDeg).coerceIn(0f, 0.95f))
        }
        return progress()
    }

    private fun progress(): LivenessStatus = when (phase) {
        Phase.NeedBaseline -> LivenessStatus.InProgress(0f)
        Phase.NeedAction -> LivenessStatus.InProgress(0.33f)
        Phase.NeedRecovery -> LivenessStatus.InProgress(0.66f)
        Phase.Done -> LivenessStatus.Passed
    }
}
