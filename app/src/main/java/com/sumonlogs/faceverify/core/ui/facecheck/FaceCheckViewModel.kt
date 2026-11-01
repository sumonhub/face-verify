package com.sumonlogs.faceverify.core.ui.facecheck

import com.sumonlogs.faceverify.core.api.FaceDiagnostics
import com.sumonlogs.faceverify.core.api.FaceFailure
import com.sumonlogs.faceverify.core.api.FaceMatchResult
import com.sumonlogs.faceverify.core.api.FaceModelSettings
import com.sumonlogs.faceverify.core.engine.FaceConfig
import com.sumonlogs.faceverify.core.engine.FaceFrame
import com.sumonlogs.faceverify.core.engine.FaceRecordStore
import com.sumonlogs.faceverify.core.engine.LivenessChallenge
import com.sumonlogs.faceverify.core.engine.QualityIssue
import com.sumonlogs.faceverify.core.engine.SpoofDetector
import com.sumonlogs.faceverify.core.engine.VerificationSession
import com.sumonlogs.faceverify.core.engine.VerifyStep
import com.sumonlogs.faceverify.core.engine.android.FaceEmbedders
import com.sumonlogs.faceverify.core.engine.android.FaceWarmUp
import com.sumonlogs.faceverify.core.mvi.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class FaceCheckViewModel(
    private val store: FaceRecordStore,
    private val settings: FaceModelSettings,
    private val embedders: FaceEmbedders,
    private val config: FaceConfig,
    private val diagnostics: FaceDiagnostics,
    private val spoofDetector: SpoofDetector,
    private val warmUp: FaceWarmUp,
) : BaseViewModel<FaceCheckIntent, FaceCheckState, FaceCheckEffect>(FaceCheckState()) {

    @Volatile
    private var session: VerificationSession? = null

    @Volatile
    private var done = false

    /** Set once the capture frames are in and the check runs on a worker; later frames are ignored. */
    @Volatile
    private var deciding = false
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
        // Models load while the camera starts; the first frames and the final match then don't wait for it.
        launchSafely { warmUp.run(includeSpoof = true) }
        launchSafely(onError = { finish(FaceMatchResult.failed(FaceFailure.EngineError), FacePrompt.Failed) }) {
            val active = settings.current()
            val people = store.load().filter { it.modelId == active.model.id }
            if (people.isEmpty()) {
                finish(FaceMatchResult.failed(FaceFailure.NotEnrolled), null)
                return@launchSafely
            }
            val challenge = LivenessChallenge.entries.random()
            diagnostics.update {
                copy(
                    modelId = active.model.id, threshold = active.threshold,
                    livenessChallenge = challenge.name, livenessPassed = null, bestFaceId = null,
                    lastScore = null, runnerUpScore = null, spoofScore = null, lastMatched = null, lastFailure = null, embedMs = null,
                )
            }
            session = VerificationSession(config, active.threshold, challenge, people, embedders.forModel(active.model), spoofDetector)
        }
    }

    /** Runs on the analysis thread. */
    private fun onFrame(frame: FaceFrame) {
        if (done || deciding) return
        val current = session ?: return
        val step = current.onFrame(frame)
        current.lastAligned?.let { diagnostics.showAlignedFace(it, ALIGNED_SIZE) }
        val issue = when (step) {
            is VerifyStep.Align -> step.issue
            is VerifyStep.Liveness -> step.issue
            is VerifyStep.Capture -> step.issue
            is VerifyStep.Deciding, is VerifyStep.Finished -> null
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
                copy(step = 3, prompt = step.issue.toPrompt(FacePrompt.HoldStill), ringProgress = 0.7f + 0.25f * step.progress)
            }
            is VerifyStep.Deciding -> decide(current, step)
            is VerifyStep.Finished -> finish(step.result, step.result.toPrompt())
        }
    }

    /**
     * Runs the slow part (anti-spoofing, embedding, matching) on a worker so the camera thread isn't
     * blocked, and shows "checking" at once. The crops were copied out of the frames already. If the
     * screen is left meanwhile, the scope is cancelled and [finish] ignores a late result.
     */
    private fun decide(current: VerificationSession, step: VerifyStep.Deciding) {
        if (deciding) return
        deciding = true
        setState { copy(step = totalSteps, prompt = FacePrompt.Checking, ringProgress = CHECKING_PROGRESS) }
        launchSafely(onError = { finish(FaceMatchResult.failed(FaceFailure.EngineError), FacePrompt.Failed) }) {
            val result = withContext(Dispatchers.Default) { step.check.run() }
            reportDiagnostics(current, null)
            finish(result, result.toPrompt())
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
                matchedName = result.face?.name,
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
                runnerUpScore = session.identification?.runnerUpScore ?: runnerUpScore,
                spoofScore = session.lastSpoofScore ?: spoofScore,
                bestFaceId = session.bestFaceId ?: bestFaceId,
                cropBrightness = session.lastCrop?.brightness ?: cropBrightness,
                cropSharpness = session.lastCrop?.sharpness ?: cropSharpness,
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
        QualityIssue.TooDark -> FacePrompt.TooDark
        QualityIssue.TooBright -> FacePrompt.TooBright
        QualityIssue.Blurry -> FacePrompt.Blurry
        QualityIssue.FaceChanged -> fallback
    }

    private fun LivenessChallenge.toPrompt(): FacePrompt = when (this) {
        LivenessChallenge.Blink -> FacePrompt.Blink
        LivenessChallenge.TurnLeft -> FacePrompt.TurnLeft
        LivenessChallenge.TurnRight -> FacePrompt.TurnRight
    }

    private fun FaceMatchResult.toPrompt(): FacePrompt = when {
        matched -> FacePrompt.Matched
        failure == null -> FacePrompt.Mismatch
        failure == FaceFailure.AmbiguousMatch -> FacePrompt.Ambiguous
        failure == FaceFailure.LivenessFailed -> FacePrompt.LivenessFailed
        failure == FaceFailure.SpoofSuspected -> FacePrompt.SpoofSuspected
        failure == FaceFailure.NoFace -> FacePrompt.NoFaceFailed
        else -> FacePrompt.Failed
    }

    private companion object {
        const val ALIGNED_SIZE = 112
        const val CHECKING_PROGRESS = 0.95f
        const val RESULT_HOLD_MS = 900L
        const val FAILURE_HOLD_MS = 1_600L
    }
}
