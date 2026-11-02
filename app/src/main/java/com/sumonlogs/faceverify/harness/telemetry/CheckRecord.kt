package com.sumonlogs.faceverify.harness.telemetry

/**
 * One face check, as sent to Firestore. Numbers and short codes only: no name, no image, no
 * embedding. Face ids are random ids made on the phone and mean nothing elsewhere. The keys of
 * [toFields] must match the allowed list in firestore.rules (a test checks this).
 */
data class CheckRecord(
    val installId: String,
    val appVersion: String,
    val device: String,
    val sdk: Int,
    /** [com.sumonlogs.faceverify.core.engine.FaceTemplates.PIPELINE_VERSION]: tells data from different face pipelines apart. */
    val pipelineVersion: Int,
    val takenAtMillis: Long,

    val modelId: String?,
    val threshold: Float,

    val scenario: String,
    val lighting: String,
    val glasses: Boolean,
    val faceCovered: Boolean,
    /** The enrolled person the tester said they were, when the scenario is "me". */
    val expectedFaceId: String?,
    val matchedFaceId: String?,
    val enrolledCount: Int,

    val matched: Boolean,
    val failure: String?,
    val score: Float,
    val runnerUp: Float?,
    val spoofScore: Float?,
    val challenge: String?,
    val livenessPassed: Boolean,

    val faceBrightness: Float?,
    val faceSharpness: Float?,
    val qualityIssue: String?,

    val detectMs: Long?,
    val embedMs: Long?,
    val totalMs: Long?,
    val timingAvgMs: Map<String, Float>,
    val timingP95Ms: Map<String, Float>,
) {
    /** The Firestore document, without empty values. The server adds `receivedAt` itself. */
    fun toFields(): Map<String, Any?> = mapOf(
        "schema" to SCHEMA,
        "installId" to installId,
        "appVersion" to appVersion,
        "device" to device,
        "sdk" to sdk,
        "pipelineVersion" to pipelineVersion,
        "takenAtMillis" to takenAtMillis,
        "modelId" to modelId,
        "threshold" to threshold,
        "scenario" to scenario,
        "lighting" to lighting,
        "glasses" to glasses,
        "faceCovered" to faceCovered,
        "expectedFaceId" to expectedFaceId,
        "matchedFaceId" to matchedFaceId,
        "enrolledCount" to enrolledCount,
        "matched" to matched,
        "failure" to failure,
        "score" to score,
        "runnerUp" to runnerUp,
        "spoofScore" to spoofScore,
        "challenge" to challenge,
        "livenessPassed" to livenessPassed,
        "faceBrightness" to faceBrightness,
        "faceSharpness" to faceSharpness,
        "qualityIssue" to qualityIssue,
        "detectMs" to detectMs,
        "embedMs" to embedMs,
        "totalMs" to totalMs,
        "timingAvgMs" to timingAvgMs.takeIf { it.isNotEmpty() },
        "timingP95Ms" to timingP95Ms.takeIf { it.isNotEmpty() },
    ).filterValues { it != null }

    companion object {
        /** Bump when a field changes meaning, so the analysis scripts can tell old and new rows apart. */
        const val SCHEMA = 1
    }
}
