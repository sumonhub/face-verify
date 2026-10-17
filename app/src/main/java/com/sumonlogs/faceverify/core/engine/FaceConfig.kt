package com.sumonlogs.faceverify.core.engine

/**
 * Every tunable of the face engine in one place. Angles are degrees, ratios are fractions of the
 * upright analysis frame. [matchThreshold] is the single value that decides a match.
 */
data class FaceConfig(
    /** Cosine similarity (clamped to 0..1) at or above which a probe matches the enrolled face. */
    val matchThreshold: Float = 0.60f,

    /** The best person must beat the runner-up by at least this much, or the result is ambiguous. */
    val identifyMargin: Float = 0.05f,

    // Frame quality
    val minFaceWidthRatio: Float = 0.28f,
    val maxFaceWidthRatio: Float = 0.85f,
    /** Max distance of the face centre from the frame centre, as a fraction of frame width/height. */
    val maxCentreOffset: Float = 0.18f,
    /** Faces narrower than this are background people and are ignored when counting faces. */
    val bystanderWidthRatio: Float = 0.12f,
    val maxRollDeg: Float = 12f,
    val maxFrontYawDeg: Float = 12f,
    val maxFrontPitchDeg: Float = 12f,
    val eyeOpenMin: Float = 0.60f,
    val eyeClosedMax: Float = 0.25f,
    /** Consecutive good frames needed before a step counts as done. */
    val stableFrames: Int = 4,

    // Liveness
    val turnYawDeg: Float = 20f,
    val livenessTimeoutMs: Long = 8_000L,

    // Enrollment
    val enrollTurnYawDeg: Float = 18f,
    val enrollUpPitchDeg: Float = 12f,
    /** Every pose embedding must be at least this similar to the front one, or enrollment restarts. */
    val enrollConsistencyMin: Float = 0.45f,

    // Session
    val verifyTimeoutMs: Long = 25_000L,
    val selfieMaxSide: Int = 640,
    val selfieJpegQuality: Int = 80,
)
