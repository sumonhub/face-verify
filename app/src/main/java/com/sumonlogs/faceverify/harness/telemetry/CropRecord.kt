package com.sumonlogs.faceverify.harness.telemetry

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * One aligned 112×112 face crop, as sent to the `crops` collection for offline model evaluation
 * (tools/face_eval.py). A face image is biometric data, so this is a separate, opt-in choice from the
 * numbers in [CheckRecord]. Only the aligned crop is sent, never the camera frame or a name. [person]
 * is a random key (this install + the enrolled face's random id), [session] says when and under what light.
 * The keys of [toFields] must match the `crops` rule in firestore.rules (a test checks this).
 */
data class CropRecord(
    val installId: String,
    val appVersion: String,
    val device: String,
    val pipelineVersion: Int,
    val takenAtMillis: Long,
    val person: String,
    val session: String,
    val lighting: String,
    val glasses: Boolean,
    val faceCovered: Boolean,
    /** Whether the app accepted this face at the time: lets the analysis see which crops it rejected. */
    val matched: Boolean,
    val score: Float,
    /** Lossless PNG of the 112×112 crop, Base64. */
    val png: String,
) {
    fun toFields(): Map<String, Any?> = mapOf(
        "schema" to SCHEMA,
        "installId" to installId,
        "appVersion" to appVersion,
        "device" to device,
        "pipelineVersion" to pipelineVersion,
        "takenAtMillis" to takenAtMillis,
        "person" to person,
        "session" to session,
        "lighting" to lighting,
        "glasses" to glasses,
        "faceCovered" to faceCovered,
        "matched" to matched,
        "score" to score,
        "png" to png,
    )

    companion object {
        const val SCHEMA = 1

        /**
         * Only a genuine tester ("me") who got as far as a comparison. Other people in front of the
         * camera never agreed to share their face, and a spoof attempt would only pollute the data.
         */
        fun eligible(scenario: String, livenessPassed: Boolean, failure: String?): Boolean =
            scenario == TestScenario.Me.code && livenessPassed && (failure == null || failure == "AmbiguousMatch")

        fun personKey(installId: String, faceId: String): String = "${installId.take(8)}_${faceId.take(8)}"

        /**
         * Sorts by date, so the tester's first sitting (where they enrolled) sorts first and becomes the
         * enrollment session in the evaluation. Date is UTC to be the same wherever the phone is.
         */
        fun sessionKey(takenAtMillis: Long, lighting: String, glasses: Boolean, faceCovered: Boolean): String {
            val day = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date(takenAtMillis))
            return buildString {
                append(day).append('_').append(lighting)
                if (glasses) append("_glasses")
                if (faceCovered) append("_covered")
            }
        }
    }
}
