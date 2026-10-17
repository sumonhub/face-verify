package com.banglatrac.workopolo.face.core.ui.facecheck

import com.banglatrac.workopolo.face.core.api.FaceDiagnostics
import com.banglatrac.workopolo.face.core.api.FaceFailure
import com.banglatrac.workopolo.face.core.api.FaceMatchResult
import com.banglatrac.workopolo.face.core.engine.FaceConfig
import com.banglatrac.workopolo.face.core.engine.FaceEmbedder
import com.banglatrac.workopolo.face.core.engine.FaceFrame
import com.banglatrac.workopolo.face.core.engine.FaceTemplateStore
import com.banglatrac.workopolo.face.core.engine.LivenessChallenge
import com.banglatrac.workopolo.face.core.engine.QualityIssue
import com.banglatrac.workopolo.face.core.engine.VerificationSession
import com.banglatrac.workopolo.face.core.engine.VerifyStep
import com.banglatrac.workopolo.face.core.mvi.BaseViewModel
import kotlinx.coroutines.delay

class FaceCheckViewModel(
    private val store: FaceTemplateStore,
    private val embedder: FaceEmbedder,
    private val config: FaceConfig,
    private val diagnostics: FaceDiagnostics,
) : BaseViewModel<FaceCheckIntent, FaceCheckState, FaceCheckEffect>(FaceCheckState()) {

    @Volatile
    private var session: VerificationSession? = null

    @Volatile
    private var done = false
    private var started = false

    override fun onIntent(intent: FaceCheckIntent) {
        when (intent) {
            FaceCheckIntent.Start -> start()
            is FaceCheckIntent.FrameAnalyzed -> onFrame(intent.frame)
            FaceCheckIntent.CameraFailed -> finish(FaceMatchResult.failed(FaceFailure.CameraUnavailable), FacePrompt.CameraUnavailable)
            FaceCheckIntent.PermissionDenied -> finish(FaceMatchResult.failed(FaceFailure.PermissionDenied), null)
            FaceCheckIntent.Cancel -> finish(FaceMatchResult.failed(FaceFailure.Cancelled), null)
        }
    }

    private fun start() {
        if (started) return
        started = true
        launchSafely(onError = { finish(FaceMatchResult.failed(FaceFailure.EngineError), FacePrompt.Failed) }) {
            val templates = store.load()
            if (templates == null) {
                finish(FaceMatchResult.failed(FaceFailure.NotEnrolled), null)
                return@launchSafely
            }
            val challenge = LivenessChallenge.entries.random()
            diagnostics.update {
                copy(
                    threshold = config.matchThreshold, livenessChallenge = challenge.name, livenessPassed = null,
                    lastScore = null, lastMatched = null, lastFailure = null, embedMs = null,
                )
            }
            session = VerificationSession(config, challenge, templates, embedder)
        }
    }

    /** Runs on the analysis thread. */
    private fun onFrame(frame: FaceFrame) {
        if (done) return
        val current = session ?: return
        val step = current.onFrame(frame)
        val issue = when (step) {
            is VerifyStep.Align -> step.issue
            is VerifyStep.Liveness -> step.issue
            is VerifyStep.Capture -> step.issue
            is VerifyStep.Finished -> null
        }
        reportDiagnostics(current, issue)
        when (step) {
            is VerifyStep.Align -> setState {
                copy(step = 1, prompt = step.issue.toPrompt(FacePrompt.Look), ringProgress = if (step.issue == null) 0.2f else 0.08f)
            }
            is VerifyStep.Liveness -> setState {
                copy(
                    step = 2,
                    prompt = step.issue?.toPrompt(FacePrompt.Look) ?: step.challenge.toPrompt(),
                    ringProgress = 0.33f + 0.33f * step.progress,
                )
            }
            is VerifyStep.Capture -> setState {
                copy(step = 3, prompt = step.issue.toPrompt(FacePrompt.HoldStill), ringProgress = 0.85f)
            }
            is VerifyStep.Finished -> finish(step.result, step.result.toPrompt())
        }
    }

    /** Shows the outcome briefly (if [prompt] is set), then hands the result back. */
    private fun finish(result: FaceMatchResult, prompt: FacePrompt?) {
        if (done) return
        done = true
        diagnostics.update {
            copy(
                lastScore = result.score, lastMatched = result.matched,
                livenessPassed = result.livenessPassed, lastFailure = result.failure,
            )
        }
        if (prompt == null) {
            sendEffect(FaceCheckEffect.Finish(result))
            return
        }
        setState {
            copy(
                step = totalSteps,
                prompt = prompt,
                ringProgress = 1f,
                ringTone = if (result.matched) RingTone.Success else RingTone.Failure,
                finished = true,
            )
        }
        launchSafely(onError = { sendEffect(FaceCheckEffect.Finish(result)) }) {
            delay(if (result.matched) RESULT_HOLD_MS else FAILURE_HOLD_MS)
            sendEffect(FaceCheckEffect.Finish(result))
        }
    }

    private fun reportDiagnostics(session: VerificationSession, issue: QualityIssue?) {
        val face = session.lastFace
        val timings = session.timings
        diagnostics.update {
            copy(
                yaw = face?.yaw, pitch = face?.pitch, roll = face?.roll,
                leftEyeOpen = face?.leftEyeOpen, rightEyeOpen = face?.rightEyeOpen,
                detectMs = timings?.detectMs, embedMs = timings?.embedMs ?: embedMs, totalMs = timings?.totalMs,
                qualityIssue = issue?.name,
            )
        }
    }

    private fun QualityIssue?.toPrompt(fallback: FacePrompt): FacePrompt = when (this) {
        null -> fallback
        QualityIssue.NoFace -> FacePrompt.NoFace
        QualityIssue.MultipleFaces -> FacePrompt.MultipleFaces
        QualityIssue.TooFar -> FacePrompt.TooFar
        QualityIssue.TooClose -> FacePrompt.TooClose
        QualityIssue.NotCentred -> FacePrompt.NotCentred
        QualityIssue.TurnedAway -> FacePrompt.TurnedAway
        QualityIssue.Tilted -> FacePrompt.Tilted
        QualityIssue.EyesClosed -> FacePrompt.EyesClosed
    }

    private fun LivenessChallenge.toPrompt(): FacePrompt = when (this) {
        LivenessChallenge.Blink -> FacePrompt.Blink
        LivenessChallenge.TurnLeft -> FacePrompt.TurnLeft
        LivenessChallenge.TurnRight -> FacePrompt.TurnRight
    }

    private fun FaceMatchResult.toPrompt(): FacePrompt = when {
        matched -> FacePrompt.Matched
        failure == null -> FacePrompt.Mismatch
        failure == FaceFailure.LivenessFailed -> FacePrompt.LivenessFailed
        failure == FaceFailure.NoFace -> FacePrompt.NoFaceFailed
        else -> FacePrompt.Failed
    }

    private companion object {
        const val RESULT_HOLD_MS = 900L
        const val FAILURE_HOLD_MS = 1_600L
    }
}
