package com.sumonlogs.faceverify.core.ui.facecheck

import com.sumonlogs.faceverify.core.api.FaceMatchResult
import com.sumonlogs.faceverify.core.engine.FaceFrame
import com.sumonlogs.faceverify.core.mvi.UiEffect
import com.sumonlogs.faceverify.core.mvi.UiIntent
import com.sumonlogs.faceverify.core.mvi.UiState

sealed interface FaceCheckIntent : UiIntent {
    data object Start : FaceCheckIntent

    /** Delivered on the camera analysis thread; handled synchronously while the frame is valid. */
    data class FrameAnalyzed(val frame: FaceFrame) : FaceCheckIntent

    data object CameraFailed : FaceCheckIntent
    data object PermissionDenied : FaceCheckIntent
    data object Cancel : FaceCheckIntent
}

/** What the headline and sub-line say. Each maps to a string pair in the screen. */
enum class FacePrompt {
    Look, NoFace, MultipleFaces, TooFar, TooClose, NotCentred, TurnedAway, Tilted, EyesClosed,
    Blink, TurnLeft, TurnRight,
    HoldStill,
    Matched, Mismatch, Ambiguous, LivenessFailed, NoFaceFailed, Failed, CameraUnavailable,
}

enum class RingTone { Progress, Success, Failure }

data class FaceCheckState(
    val step: Int = 1,
    val totalSteps: Int = 3,
    val prompt: FacePrompt = FacePrompt.Look,
    val ringProgress: Float = 0f,
    val ringTone: RingTone = RingTone.Progress,
    val finished: Boolean = false,
    /** Who was recognised, shown on the result; only set when [prompt] is Matched. */
    val matchedName: String? = null,
) : UiState

sealed interface FaceCheckEffect : UiEffect {
    data class Finish(val result: FaceMatchResult) : FaceCheckEffect
}
