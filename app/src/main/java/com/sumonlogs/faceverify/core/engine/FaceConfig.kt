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
    /** "Facing the camera" for the liveness baseline (back to front after a turn). */
    val maxFrontYawDeg: Float = 12f,
    /**
     * Frames used for matching and enrollment must be this close to straight on. Alignment fits a flat,
     * frontal template, and the further the head is turned the more the landmarks sit off it, so the
     * crop drifts with the pose. Tighter than [maxFrontYawDeg]; a few degrees of ML Kit's own yaw noise
     * fit inside it.
     */
    val maxCaptureYawDeg: Float = 8f,
    val maxFrontPitchDeg: Float = 12f,
    val eyeOpenMin: Float = 0.60f,
    val eyeClosedMax: Float = 0.25f,
    /** Consecutive good frames needed before a step counts as done. */
    val stableFrames: Int = 4,

    // Face crop quality (see CropQuality)
    /**
     * Mean brightness (0..255) of the face crop below which it is "too dark". Kept low on purpose: a
     * normally lit face can read 40-60 on a front camera, especially against a bright background or on
     * darker skin, and the model copes with that. Only a really dark crop is turned away.
     */
    val minBrightness: Float = 30f,
    val maxBrightness: Float = 215f,
    val minSharpness: Float = 15f,
    /**
     * Standard deviation of luma in the face crop below which the face is flat (washed out, fog, a screen
     * glare) and above which the check is let through. Deliberately loose until real data says otherwise.
     */
    val minContrast: Float = 18f,
    /**
     * How much brighter one half of the face may be than the other, as a fraction of the mean. Side
     * light or a window beside the face changes the embedding; only strong cases are turned away.
     */
    val maxLightImbalance: Float = 0.45f,
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
    val enrollFrontFrames: Int = 8,
    /**
     * Minimum time between two enrollment frames. Much longer than [captureSpacingMs]: a template built
     * from frames a fraction of a second apart is one photo taken eight times; a few seconds of natural
     * movement and expression is what it needs to cover the person.
     */
    val enrollSpacingMs: Long = 350L,
    /**
     * A frame less similar than this to the average of all frames is dropped as an outlier (a blink, a
     * blur, a different person walking in). Conservative until real data says otherwise.
     */
    val enrollConsistencyMin: Float = 0.6f,
    /** Enrollment fails when fewer front frames than this survive the outlier check. */
    val enrollMinKept: Int = 5,

    // Inference
    /** CPU threads for the face-embedding model. */
    val embedderThreads: Int = 2,
    /** CPU threads for each anti-spoof model. */
    val spoofThreads: Int = 2,

    // Camera
    /** The live preview is capped near this size (4:3): sharp enough for the oval, light on the GPU. */
    val previewWidth: Int = 960,
    val previewHeight: Int = 720,

    /** Auto-exposure meters this share of the face width around its centre (see [FaceMetering]). */
    val meteringFaceShare: Float = 0.6f,
    /** Exposure is re-aimed when the face moves more than this fraction of the frame. */
    val meteringMoveRatio: Float = 0.08f,
    /** Exposure is never re-aimed more often than this; each request restarts the exposure search. */
    val meteringMinIntervalMs: Long = 700L,
    /**
     * The camera is brightened (+EV) while the face's mean luma is below this, and darkened above
     * [faceLumaHigh] (see [ExposureSteering]). Well above [minBrightness], so the crop clears it with room to spare.
     */
    val faceLumaLow: Float = 80f,
    val faceLumaHigh: Float = 175f,
    /**
     * Time between two exposure changes. Auto-exposure plus the analysis pipeline take several hundred
     * ms to show a change; deciding faster than that overshoots and swings the face dark and bright.
     */
    val exposureStepIntervalMs: Long = 900L,
    /**
     * Most the camera is ever brightened. Steering never darkens below the camera's own exposure: it
     * only takes back its own boost, so it can't push a well lit face into "too dark".
     */
    val maxExposureBoostEv: Float = 2f,

    // Session
    val verifyTimeoutMs: Long = 25_000L,
    /** Front-facing frames embedded and averaged for each face check. */
    val verifyFrames: Int = 3,
    val selfieMaxSide: Int = 640,
    val selfieJpegQuality: Int = 80,
)
