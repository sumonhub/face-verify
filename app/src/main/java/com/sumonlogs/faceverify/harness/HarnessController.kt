package com.sumonlogs.faceverify.harness

import com.sumonlogs.faceverify.core.api.ActiveFaceModel
import com.sumonlogs.faceverify.core.api.EnrolledFace
import com.sumonlogs.faceverify.core.api.FaceDiagnostics
import com.sumonlogs.faceverify.core.api.FaceFailure
import com.sumonlogs.faceverify.core.api.FaceMatchResult
import com.sumonlogs.faceverify.core.api.FaceModelSettings
import com.sumonlogs.faceverify.core.api.FaceVerifier
import com.sumonlogs.faceverify.harness.telemetry.TesterSetup
import com.sumonlogs.faceverify.harness.telemetry.TesterTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The last check. Until the tester says whether the result was right ([answered]) no new check can
 * start, so every check ends up counted.
 */
data class LastCheck(
    val result: FaceMatchResult,
    val modelId: String?,
    val bestFaceId: String?,
    val score: Float,
    val answered: Boolean = false,
) {
    /** Cancelled, no permission and nobody enrolled say nothing about the face, so nothing to judge. */
    val needsVerdict: Boolean get() = !answered && result.failure !in NOT_A_CHECK

    /** Only checks that produced a score (liveness passed, a comparison ran) can feed the calibration numbers. */
    val scored: Boolean
        get() = bestFaceId != null && modelId != null &&
            result.livenessPassed && (result.failure == null || result.failure == FaceFailure.AmbiguousMatch)
}

/** Results that say nothing about the face: the tester backed out, or there was nobody to check. */
private val NOT_A_CHECK = setOf(FaceFailure.Cancelled, FaceFailure.PermissionDenied, FaceFailure.NotEnrolled)

/** Plain harness state holder: runs FaceVerifier calls, model settings and calibration. */
class HarnessController(
    private val verifier: FaceVerifier,
    private val settings: FaceModelSettings,
    private val diagnostics: FaceDiagnostics,
    private val calibration: CalibrationLog,
    private val verdicts: VerdictLog,
    private val benchmark: PerformanceBenchmark,
    private val telemetry: TesterTelemetry,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Null until the first read from storage. */
    val faces: StateFlow<List<EnrolledFace>?> = verifier.enrolledFaces
        .stateIn(scope, SharingStarted.Eagerly, null)

    val active: StateFlow<ActiveFaceModel?> = settings.active
        .stateIn(scope, SharingStarted.Eagerly, null)

    val stats: StateFlow<CalibrationStats> = combine(calibration.samples, settings.active) { samples, model ->
        CalibrationStats.of(samples, model.model.id)
    }.stateIn(scope, SharingStarted.Eagerly, CalibrationStats(emptyList(), emptyList()))

    /** Right / wrong answers for the active model. */
    val tally: StateFlow<VerdictTally> = combine(verdicts.verdicts, settings.active) { all, model ->
        VerdictTally.of(all, model.model.id)
    }.stateIn(scope, SharingStarted.Eagerly, VerdictTally())

    init {
        // The test app shows the aligned face crop; the real app never turns this on.
        diagnostics.captureAlignedFace = true
    }

    // Tester data sharing (see harness/telemetry)
    val setup = telemetry.setup
    val uploads = telemetry.uploads
    val installId = telemetry.installId
    val sharingAvailable: Boolean get() = telemetry.available

    fun updateSetup(setup: TesterSetup) = telemetry.updateSetup(setup)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _lastCheck = MutableStateFlow<LastCheck?>(null)
    val lastCheck: StateFlow<LastCheck?> = _lastCheck.asStateFlow()

    fun verify() {
        if (_busy.value) return
        _busy.value = true
        scope.launch {
            try {
                val result = verifier.verify()
                val info = diagnostics.info.value
                _lastCheck.value = LastCheck(result, info.modelId, info.bestFaceId, info.lastScore ?: result.score)
                if (result.failure !in NOT_A_CHECK) {
                    val usable = faces.value.orEmpty().filter { it.isUsableWith(info.modelId) }
                    telemetry.record(result, info, diagnostics.stageTimings.value, usable, diagnostics.alignedFace.value)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * The tester's verdict on the last check: was what the app did right? Counted for the active
     * model, and when the check produced a score it also becomes a calibration sample: a right
     * accept or a wrong refusal means the best person's score was a genuine one, anything else a stranger's.
     */
    fun answer(correct: Boolean) {
        val check = _lastCheck.value ?: return
        if (!check.needsVerdict) return
        val modelId = check.modelId ?: active.value?.model?.id ?: return
        val matched = check.result.matched
        _lastCheck.value = check.copy(answered = true)
        scope.launch {
            verdicts.add(Verdict(modelId, Outcome.of(matched, correct)))
            if (check.scored) calibration.add(CalibrationSample(modelId, genuine = matched == correct, score = check.score))
        }
    }

    fun clearVerdicts() {
        val modelId = active.value?.model?.id ?: return
        scope.launch { verdicts.clear(modelId) }
    }

    private val _bench = MutableStateFlow<BenchState>(BenchState.Idle)
    val bench: StateFlow<BenchState> = _bench.asStateFlow()

    fun runBenchmark() {
        val model = active.value?.model ?: return
        if (_busy.value || _bench.value == BenchState.Running) return
        _bench.value = BenchState.Running
        scope.launch {
            _bench.value = try {
                BenchState.Done(benchmark.run(model))
            } catch (e: Exception) {
                BenchState.Idle
            }
        }
    }

    fun clearTimings() = diagnostics.clearTimings()

    fun selectModel(modelId: String) {
        scope.launch {
            settings.selectModel(modelId)
            _lastCheck.value = null
        }
    }

    fun setThreshold(threshold: Float?) {
        val modelId = active.value?.model?.id ?: return
        scope.launch { settings.setThreshold(modelId, threshold) }
    }

    fun clearCalibration() {
        val modelId = active.value?.model?.id ?: return
        scope.launch { calibration.clear(modelId) }
    }

    fun delete(face: EnrolledFace) {
        scope.launch {
            verifier.deleteEnrollment(face.id)
            if (_lastCheck.value?.result?.face?.id == face.id) _lastCheck.value = null
        }
    }
}
