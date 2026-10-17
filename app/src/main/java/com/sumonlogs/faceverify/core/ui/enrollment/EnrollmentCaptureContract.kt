package com.sumonlogs.faceverify.core.ui.enrollment

import com.sumonlogs.faceverify.core.api.EnrolledFace
import com.sumonlogs.faceverify.core.engine.EnrollFailure
import com.sumonlogs.faceverify.core.engine.EnrollPose
import com.sumonlogs.faceverify.core.engine.FaceFrame
import com.sumonlogs.faceverify.core.engine.QualityIssue
import com.sumonlogs.faceverify.core.mvi.UiEffect
import com.sumonlogs.faceverify.core.mvi.UiIntent
import com.sumonlogs.faceverify.core.mvi.UiState

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
    /** Name of the enrolled person this face already matches; enrollment is refused. */
    val duplicateOf: String? = null,
    val cameraUnavailable: Boolean = false,
    val showHelp: Boolean = false,
    /** Bumped on retry so the camera is rebound. */
    val attempt: Int = 0,
) : UiState {
    val progress: Float get() = captured.size.toFloat() / EnrollPose.entries.size
}

sealed interface EnrollmentCaptureEffect : UiEffect {
    data class Enrolled(val face: EnrolledFace) : EnrollmentCaptureEffect
}
