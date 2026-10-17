package com.sumonlogs.faceverify.core.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Numbers only: never put embeddings, images or anything identifying in here. */
data class FaceDebugInfo(
    val threshold: Float = 0f,
    val lastScore: Float? = null,
    /** Score of the second-best enrolled person; a small gap means the result was close. */
    val runnerUpScore: Float? = null,
    val lastMatched: Boolean? = null,
    val livenessChallenge: String? = null,
    val livenessPassed: Boolean? = null,
    val lastFailure: FaceFailure? = null,
    val qualityIssue: String? = null,
    val yaw: Float? = null,
    val pitch: Float? = null,
    val roll: Float? = null,
    val leftEyeOpen: Float? = null,
    val rightEyeOpen: Float? = null,
    val detectMs: Long? = null,
    val embedMs: Long? = null,
    val totalMs: Long? = null,
)

/** Live engine numbers for a debug panel. Not part of [FaceVerifier]; the main app can ignore it. */
class FaceDiagnostics(threshold: Float) {
    private val _info = MutableStateFlow(FaceDebugInfo(threshold = threshold))
    val info: StateFlow<FaceDebugInfo> = _info.asStateFlow()

    fun update(transform: FaceDebugInfo.() -> FaceDebugInfo) = _info.update(transform)
}
