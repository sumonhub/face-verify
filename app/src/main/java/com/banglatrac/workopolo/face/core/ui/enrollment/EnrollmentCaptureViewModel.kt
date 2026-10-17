package com.banglatrac.workopolo.face.core.ui.enrollment

import com.banglatrac.workopolo.face.core.api.FaceDiagnostics
import com.banglatrac.workopolo.face.core.engine.EnrollFailure
import com.banglatrac.workopolo.face.core.engine.EnrollPose
import com.banglatrac.workopolo.face.core.engine.EnrollStep
import com.banglatrac.workopolo.face.core.engine.EnrollmentSession
import com.banglatrac.workopolo.face.core.engine.FaceConfig
import com.banglatrac.workopolo.face.core.engine.FaceEmbedder
import com.banglatrac.workopolo.face.core.engine.FaceFrame
import com.banglatrac.workopolo.face.core.engine.FaceTemplateStore
import com.banglatrac.workopolo.face.core.mvi.BaseViewModel

class EnrollmentCaptureViewModel(
    private val store: FaceTemplateStore,
    private val embedder: FaceEmbedder,
    private val config: FaceConfig,
    private val diagnostics: FaceDiagnostics,
) : BaseViewModel<EnrollmentCaptureIntent, EnrollmentCaptureState, EnrollmentCaptureEffect>(EnrollmentCaptureState()) {

    @Volatile
    private var session: EnrollmentSession? = null

    override fun onIntent(intent: EnrollmentCaptureIntent) {
        when (intent) {
            EnrollmentCaptureIntent.Start -> if (session == null) session = EnrollmentSession(config, embedder)
            is EnrollmentCaptureIntent.FrameAnalyzed -> onFrame(intent.frame)
            EnrollmentCaptureIntent.CameraFailed -> {
                session = null
                setState { copy(cameraUnavailable = true) }
            }
            EnrollmentCaptureIntent.Retry -> {
                session = EnrollmentSession(config, embedder)
                setState { EnrollmentCaptureState(attempt = attempt + 1) }
            }
            EnrollmentCaptureIntent.ShowHelp -> setState { copy(showHelp = true) }
            EnrollmentCaptureIntent.HideHelp -> setState { copy(showHelp = false) }
        }
    }

    /** Runs on the analysis thread. */
    private fun onFrame(frame: FaceFrame) {
        val current = session ?: return
        if (currentState.saving || currentState.failure != null) return
        val step = current.onFrame(frame)
        val face = current.lastFace
        diagnostics.update {
            copy(
                yaw = face?.yaw, pitch = face?.pitch, roll = face?.roll,
                leftEyeOpen = face?.leftEyeOpen, rightEyeOpen = face?.rightEyeOpen,
                detectMs = frame.detectMs, qualityIssue = (step as? EnrollStep.Posing)?.issue?.name,
            )
        }
        when (step) {
            is EnrollStep.Posing -> setState { copy(pose = step.pose, captured = step.captured, issue = step.issue) }
            is EnrollStep.Failed -> {
                session = null
                setState { copy(failure = step.reason, issue = null) }
            }
            is EnrollStep.Finished -> {
                session = null
                setState { copy(captured = EnrollPose.entries.toSet(), saving = true, issue = null) }
                save(step.templates)
            }
        }
    }

    private fun save(templates: List<FloatArray>) {
        launchSafely(onError = { setState { copy(saving = false, failure = EnrollFailure.EngineError) } }) {
            store.save(templates)
            sendEffect(EnrollmentCaptureEffect.Enrolled)
        }
    }
}
