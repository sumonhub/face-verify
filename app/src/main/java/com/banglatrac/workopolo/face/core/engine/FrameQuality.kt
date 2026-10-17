package com.banglatrac.workopolo.face.core.engine

import kotlin.math.abs

enum class QualityIssue { NoFace, MultipleFaces, TooFar, TooClose, NotCentred, TurnedAway, Tilted, EyesClosed }

sealed interface QualityCheck {
    data class Good(val face: FaceObservation) : QualityCheck
    data class Bad(val issue: QualityIssue, val face: FaceObservation?) : QualityCheck
}

/** Decides whether a frame shows exactly one usable face. Pure: no Android types. */
object FrameQuality {

    /**
     * @param requireFront when false, head turn and eye checks are skipped (used for enrollment side poses).
     */
    fun check(
        faces: List<FaceObservation>,
        frameWidth: Int,
        frameHeight: Int,
        config: FaceConfig,
        requireFront: Boolean = true,
    ): QualityCheck {
        val people = faces.filter { it.width / frameWidth >= config.bystanderWidthRatio }
        val face = people.maxByOrNull { it.width } ?: return QualityCheck.Bad(QualityIssue.NoFace, null)
        if (people.size > 1) return QualityCheck.Bad(QualityIssue.MultipleFaces, face)

        val widthRatio = face.width / frameWidth
        val issue = when {
            widthRatio < config.minFaceWidthRatio -> QualityIssue.TooFar
            widthRatio > config.maxFaceWidthRatio -> QualityIssue.TooClose
            abs(face.centerX / frameWidth - 0.5f) > config.maxCentreOffset ||
                abs(face.centerY / frameHeight - 0.5f) > config.maxCentreOffset -> QualityIssue.NotCentred
            abs(face.roll) > config.maxRollDeg -> QualityIssue.Tilted
            requireFront && !isFront(face, config) -> QualityIssue.TurnedAway
            requireFront && !eyesOpen(face, config) -> QualityIssue.EyesClosed
            else -> null
        }
        return if (issue == null) QualityCheck.Good(face) else QualityCheck.Bad(issue, face)
    }

    fun isFront(face: FaceObservation, config: FaceConfig): Boolean =
        abs(face.yaw) <= config.maxFrontYawDeg && abs(face.pitch) <= config.maxFrontPitchDeg

    /** Unknown eye state counts as open: ML Kit omits it for some frames. */
    fun eyesOpen(face: FaceObservation, config: FaceConfig): Boolean =
        (face.leftEyeOpen ?: 1f) >= config.eyeOpenMin && (face.rightEyeOpen ?: 1f) >= config.eyeOpenMin
}
