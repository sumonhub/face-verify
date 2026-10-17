package com.banglatrac.workopolo.face.core.api

/** The only face API the main app calls. Every method is safe to call; verify() never throws. */
interface FaceVerifier {
    suspend fun isEnrolled(): Boolean

    /** Opens the face-check camera flow and returns its outcome. */
    suspend fun verify(): FaceMatchResult

    suspend fun clearEnrollment()
}

enum class FaceFailure {
    NoFace,
    MultipleFaces,
    PoorQuality,
    LivenessFailed,
    NotEnrolled,
    CameraUnavailable,
    PermissionDenied,
    Cancelled,
    Timeout,
    EngineError,
}

data class FaceMatchResult(
    val matched: Boolean,
    /** Similarity, 0..1. */
    val score: Float,
    val livenessPassed: Boolean,
    /** JPEG of the frame used, for the supervisor's "View". */
    val selfie: ByteArray?,
    val failure: FaceFailure?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceMatchResult) return false
        return matched == other.matched &&
            score == other.score &&
            livenessPassed == other.livenessPassed &&
            failure == other.failure &&
            (selfie?.contentEquals(other.selfie) ?: (other.selfie == null))
    }

    override fun hashCode(): Int {
        var result = matched.hashCode()
        result = 31 * result + score.hashCode()
        result = 31 * result + livenessPassed.hashCode()
        result = 31 * result + (selfie?.contentHashCode() ?: 0)
        result = 31 * result + (failure?.hashCode() ?: 0)
        return result
    }

    /** Never print the selfie bytes. */
    override fun toString(): String =
        "FaceMatchResult(matched=$matched, score=$score, livenessPassed=$livenessPassed, " +
            "selfie=${selfie?.let { "${it.size} bytes" }}, failure=$failure)"

    companion object {
        fun failed(failure: FaceFailure, selfie: ByteArray? = null, livenessPassed: Boolean = false) =
            FaceMatchResult(matched = false, score = 0f, livenessPassed = livenessPassed, selfie = selfie, failure = failure)
    }
}
