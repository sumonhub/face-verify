package com.sumonlogs.faceverify.core.ui.enrollment

import com.sumonlogs.faceverify.core.api.EnrolledFace
import com.sumonlogs.faceverify.core.api.FaceDiagnostics
import com.sumonlogs.faceverify.core.engine.EnrollFailure
import com.sumonlogs.faceverify.core.engine.EnrollPose
import com.sumonlogs.faceverify.core.engine.EnrollStep
import com.sumonlogs.faceverify.core.engine.EnrollmentSession
import com.sumonlogs.faceverify.core.engine.FaceConfig
import com.sumonlogs.faceverify.core.engine.FaceEmbedder
import com.sumonlogs.faceverify.core.engine.FaceFrame
import com.sumonlogs.faceverify.core.engine.FaceMatcher
import com.sumonlogs.faceverify.core.engine.FaceRecord
import com.sumonlogs.faceverify.core.engine.FaceRecordStore
import com.sumonlogs.faceverify.core.mvi.BaseViewModel
import java.util.UUID

/** Captures the poses for one new person called [name] and saves them as a new enrollment. */
class EnrollmentCaptureViewModel(
    private val name: String,
    private val store: FaceRecordStore,
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
        if (currentState.saving || currentState.failure != null || currentState.duplicateOf != null) return
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
            val existing = store.load()
            val front = templates.first()
            val clash = existing.maxByOrNull { FaceMatcher.score(front, it.templates) }
                ?.takeIf { FaceMatcher.score(front, it.templates) >= config.matchThreshold }
            if (clash != null) {
                setState { copy(saving = false, duplicateOf = clash.name) }
                return@launchSafely
            }
            val record = FaceRecord(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                enrolledAtMillis = System.currentTimeMillis(),
                templates = templates,
            )
            store.add(record)
            sendEffect(EnrollmentCaptureEffect.Enrolled(EnrolledFace(record.id, record.name, record.enrolledAtMillis)))
        }
    }
}
