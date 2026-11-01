package com.sumonlogs.faceverify.core.engine

/**
 * Every tunable of the face engine in one place. Angles are degrees, ratios are fractions of the
 * upright analysis frame. The match threshold belongs to the model: see [FaceModelSpec.defaultThreshold].
 */
data class FaceConfig(
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

    // Face crop quality (see CropQuality)
    val minBrightness: Float = 50f,
    val maxBrightness: Float = 215f,
    val minSharpness: Float = 15f,
    /** A dark, bright or blurry crop is rejected at once, but the hint only shows after this many frames in a row. */
    val cropHintAfterFrames: Int = 6,
    /** Minimum time between two frames used for embeddings, so they aren't near-duplicates. */
    val captureSpacingMs: Long = 120L,

    // Liveness
    val turnYawDeg: Float = 20f,
    val livenessTimeoutMs: Long = 8_000L,

    // Anti-spoofing
    /**
     * The face is rejected as fake when the average real-face probability over the capture frames is
     * below this. 0.5 is the model's own decision point; tune it on real attack data.
     */
    val spoofMinScore: Float = 0.5f,

    // Enrollment
    val enrollTurnYawDeg: Float = 18f,
    /** Front-facing frames embedded and averaged into a person's template. */
    val enrollFrontFrames: Int = 5,
    /** Every enrollment frame must be at least this similar to their average, or enrollment fails. */
    val enrollConsistencyMin: Float = 0.5f,

    // Inference
    /** CPU threads for the face-embedding model. */
    val embedderThreads: Int = 2,
    /** CPU threads for each anti-spoof model. */
    val spoofThreads: Int = 2,

    // Camera
    /** The live preview is capped near this size (4:3): sharp enough for the oval, light on the GPU. */
    val previewWidth: Int = 960,
    val previewHeight: Int = 720,

    // Session
    val verifyTimeoutMs: Long = 25_000L,
    /** Front-facing frames embedded and averaged for each face check. */
    val verifyFrames: Int = 3,
    val selfieMaxSide: Int = 640,
    val selfieJpegQuality: Int = 80,
)
