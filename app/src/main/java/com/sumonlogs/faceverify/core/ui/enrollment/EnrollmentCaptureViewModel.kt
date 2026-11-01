package com.sumonlogs.faceverify.core.ui.enrollment

import com.sumonlogs.faceverify.core.api.ActiveFaceModel
import com.sumonlogs.faceverify.core.api.EnrolledFace
import com.sumonlogs.faceverify.core.api.FaceDiagnostics
import com.sumonlogs.faceverify.core.api.FaceModelSettings
import com.sumonlogs.faceverify.core.engine.EnrollFailure
import com.sumonlogs.faceverify.core.engine.EnrollPose
import com.sumonlogs.faceverify.core.engine.EnrollStep
import com.sumonlogs.faceverify.core.engine.EnrollmentSession
import com.sumonlogs.faceverify.core.engine.FaceConfig
import com.sumonlogs.faceverify.core.engine.FaceFrame
import com.sumonlogs.faceverify.core.engine.FaceMatcher
import com.sumonlogs.faceverify.core.engine.FaceRecord
import com.sumonlogs.faceverify.core.engine.FaceRecordStore
import com.sumonlogs.faceverify.core.engine.android.FaceEmbedders
import com.sumonlogs.faceverify.core.engine.android.FaceWarmUp
import com.sumonlogs.faceverify.core.mvi.BaseViewModel
import java.util.UUID

/** Captures one new person called [name] with the active model and saves them as a new enrollment. */
class EnrollmentCaptureViewModel(
    private val name: String,
    private val store: FaceRecordStore,
    private val settings: FaceModelSettings,
    private val embedders: FaceEmbedders,
    private val config: FaceConfig,
    private val diagnostics: FaceDiagnostics,
    private val warmUp: FaceWarmUp,
) : BaseViewModel<EnrollmentCaptureIntent, EnrollmentCaptureState, EnrollmentCaptureEffect>(EnrollmentCaptureState()) {

    @Volatile
    private var session: EnrollmentSession? = null

    @Volatile
    private var active: ActiveFaceModel? = null

    override fun onIntent(intent: EnrollmentCaptureIntent) {
        when (intent) {
            EnrollmentCaptureIntent.Start -> if (active == null) start()
            is EnrollmentCaptureIntent.FrameAnalyzed -> onFrame(intent.frame)
            EnrollmentCaptureIntent.CameraFailed -> {
                session = null
                setState { copy(cameraUnavailable = true) }
            }
            EnrollmentCaptureIntent.Retry -> {
                session = active?.let { EnrollmentSession(config, embedders.forModel(it.model)) }
                setState { EnrollmentCaptureState(attempt = attempt + 1) }
            }
            EnrollmentCaptureIntent.ShowHelp -> setState { copy(showHelp = true) }
            EnrollmentCaptureIntent.HideHelp -> setState { copy(showHelp = false) }
        }
    }

    private fun start() {
        launchSafely { warmUp.run(includeSpoof = false) }
        launchSafely(onError = { setState { copy(failure = EnrollFailure.EngineError) } }) {
            val model = settings.current()
            active = model
            diagnostics.update { copy(modelId = model.model.id, threshold = model.threshold) }
            session = EnrollmentSession(config, embedders.forModel(model.model))
        }
    }

    /** Runs on the analysis thread. */
    private fun onFrame(frame: FaceFrame) {
        val current = session ?: return
        if (currentState.saving || currentState.failure != null || currentState.duplicateOf != null) return
        val step = current.onFrame(frame)
        current.lastAligned?.let { diagnostics.showAlignedFace(it, ALIGNED_SIZE) }
        val face = current.lastFace
        diagnostics.update {
            copy(
                yaw = face?.yaw, pitch = face?.pitch, roll = face?.roll,
                leftEyeOpen = face?.leftEyeOpen, rightEyeOpen = face?.rightEyeOpen,
                cropBrightness = current.lastCrop?.brightness ?: cropBrightness,
                cropSharpness = current.lastCrop?.sharpness ?: cropSharpness,
                detectMs = frame.detectMs, qualityIssue = (step as? EnrollStep.Posing)?.issue?.name,
            )
        }
        when (step) {
            is EnrollStep.Posing -> setState {
                copy(pose = step.pose, captured = step.captured, issue = step.issue, progress = step.progress)
            }
            is EnrollStep.Failed -> {
                session = null
                setState { copy(failure = step.reason, issue = null) }
            }
            is EnrollStep.Finished -> {
                session = null
                setState { copy(captured = EnrollPose.entries.toSet(), progress = 1f, saving = true, issue = null) }
                save(step.templates)
            }
        }
    }

    private companion object {
        const val ALIGNED_SIZE = 112
    }

    private fun save(templates: List<FloatArray>) {
        launchSafely(onError = { setState { copy(saving = false, failure = EnrollFailure.EngineError) } }) {
            val model = active ?: settings.current()
            val probe = FaceMatcher.centroid(templates)
            val clash = store.load()
                .filter { it.modelId == model.model.id }
                .map { it to FaceMatcher.score(probe, it.templates) }
                .filter { (_, score) -> score >= model.threshold }
                .maxByOrNull { (_, score) -> score }
                ?.first
            if (clash != null) {
                setState { copy(saving = false, duplicateOf = clash.name) }
                return@launchSafely
            }
            val record = FaceRecord(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                enrolledAtMillis = System.currentTimeMillis(),
                modelId = model.model.id,
                templates = templates,
            )
            store.add(record)
            sendEffect(
                EnrollmentCaptureEffect.Enrolled(EnrolledFace(record.id, record.name, record.enrolledAtMillis, record.modelId)),
            )
        }
    }
}
