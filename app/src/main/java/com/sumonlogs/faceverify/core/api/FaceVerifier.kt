package com.sumonlogs.faceverify.core.api

import com.sumonlogs.faceverify.core.engine.FaceTemplates
import kotlinx.coroutines.flow.Flow

/** The face feature's public API. Every method is safe to call; verify() never throws. */
interface FaceVerifier {
    /**
     * Everyone enrolled on this device, oldest first, for every model. Only people enrolled with the
     * active model ([FaceModelSettings]) can be recognised. Updates after enroll and delete.
     */
    val enrolledFaces: Flow<List<EnrolledFace>>

    /** Opens the face-check camera flow and returns who was recognised among the active model's people. */
    suspend fun verify(): FaceMatchResult

    suspend fun deleteEnrollment(id: String)
}

data class EnrolledFace(
    val id: String,
    val name: String,
    val enrolledAtMillis: Long,
    val modelId: String,
    /** The face pipeline that made the template; see [FaceTemplates]. Defaults to the current one. */
    val pipelineVersion: Int = FaceTemplates.PIPELINE_VERSION,
) {
    /** Only a template of the active model made by the current pipeline can be matched. */
    fun isUsableWith(activeModelId: String?): Boolean =
        modelId == activeModelId && pipelineVersion == FaceTemplates.PIPELINE_VERSION
}

enum class FaceFailure {
    NoFace,
    MultipleFaces,
    PoorQuality,
    LivenessFailed,
    /** The face looked like a photo, a screen or a print rather than a real person. */
    SpoofSuspected,
    /** Two enrolled people scored too close to each other to tell them apart. */
    AmbiguousMatch,
    NotEnrolled,
    CameraUnavailable,
    PermissionDenied,
    Cancelled,
    Timeout,
    EngineError,
}

data class FaceMatchResult(
    val matched: Boolean,
    /** The recognised person when [matched]; null otherwise. */
    val face: EnrolledFace?,
    /** Similarity to the best-matching enrolled person, 0..1. */
    val score: Float,
    val livenessPassed: Boolean,
    /** JPEG of the frame used. */
    val selfie: ByteArray?,
    val failure: FaceFailure?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceMatchResult) return false
        return matched == other.matched &&
            face == other.face &&
            score == other.score &&
            livenessPassed == other.livenessPassed &&
            failure == other.failure &&
            (selfie?.contentEquals(other.selfie) ?: (other.selfie == null))
    }

    override fun hashCode(): Int {
        var result = matched.hashCode()
        result = 31 * result + (face?.hashCode() ?: 0)
        result = 31 * result + score.hashCode()
        result = 31 * result + livenessPassed.hashCode()
        result = 31 * result + (selfie?.contentHashCode() ?: 0)
        result = 31 * result + (failure?.hashCode() ?: 0)
        return result
    }

    /** Never print the selfie bytes or the person's name. */
    override fun toString(): String =
        "FaceMatchResult(matched=$matched, faceId=${face?.id}, score=$score, livenessPassed=$livenessPassed, " +
            "selfie=${selfie?.let { "${it.size} bytes" }}, failure=$failure)"

    companion object {
        fun failed(failure: FaceFailure, selfie: ByteArray? = null, livenessPassed: Boolean = false, score: Float = 0f) =
            FaceMatchResult(
                matched = false,
                face = null,
                score = score,
                livenessPassed = livenessPassed,
                selfie = selfie,
                failure = failure,
            )
    }
}
