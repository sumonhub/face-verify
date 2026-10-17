package com.banglatrac.workopolo.face.core.ui.enrollment

import com.banglatrac.workopolo.face.core.engine.EnrollFailure
import com.banglatrac.workopolo.face.core.engine.EnrollPose
import com.banglatrac.workopolo.face.core.engine.FaceFrame
import com.banglatrac.workopolo.face.core.engine.QualityIssue
import com.banglatrac.workopolo.face.core.mvi.UiEffect
import com.banglatrac.workopolo.face.core.mvi.UiIntent
import com.banglatrac.workopolo.face.core.mvi.UiState

sealed interface EnrollmentCaptureIntent : UiIntent {
    data object Start : EnrollmentCaptureIntent

    /** Delivered on the camera analysis thread; handled synchronously while the frame is valid. */
    data class FrameAnalyzed(val frame: FaceFrame) : EnrollmentCaptureIntent

    data object CameraFailed : EnrollmentCaptureIntent
    data object Retry : EnrollmentCaptureIntent
    data object ShowHelp : EnrollmentCaptureIntent
    data object HideHelp : EnrollmentCaptureIntent
}

data class EnrollmentCaptureState(
    val pose: EnrollPose = EnrollPose.Front,
    val captured: Set<EnrollPose> = emptySet(),
    val issue: QualityIssue? = null,
    val saving: Boolean = false,
    val failure: EnrollFailure? = null,
    val cameraUnavailable: Boolean = false,
    val showHelp: Boolean = false,
    /** Bumped on retry so the camera is rebound. */
    val attempt: Int = 0,
) : UiState {
    val progress: Float get() = captured.size.toFloat() / EnrollPose.entries.size
}

sealed interface EnrollmentCaptureEffect : UiEffect {
    data object Enrolled : EnrollmentCaptureEffect
}
