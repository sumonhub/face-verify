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

/** Who was really in front of the camera, as the tester says. */
sealed interface CheckLabel {
    data class Person(val id: String) : CheckLabel
    data object NotEnrolled : CheckLabel
}

/** The last check, with what's needed to label it for calibration. */
data class LastCheck(
    val result: FaceMatchResult,
    val modelId: String?,
    val bestFaceId: String?,
    val score: Float,
    val labelled: Boolean = false,
) {
    /** Only checks that produced a score (liveness passed, a comparison ran) can be labelled. */
    val labellable: Boolean
        get() = !labelled && bestFaceId != null && modelId != null &&
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

    init {
        // The test app shows the aligned face crop; the real app never turns this on.
        diagnostics.captureAlignedFace = true
    }

    // Tester data sharing (see harness/telemetry)
    val setup = telemetry.setup
    val consent = telemetry.consent
    val uploads = telemetry.uploads
    val installId = telemetry.installId
    val cropConsent = telemetry.cropConsent
    val sharingAvailable: Boolean get() = telemetry.available

    fun setCropConsent(granted: Boolean) = telemetry.setCropConsent(granted)

    fun updateSetup(setup: TesterSetup) = telemetry.updateSetup(setup)

    fun setConsent(granted: Boolean) = telemetry.setConsent(granted)

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

    fun label(label: CheckLabel) {
        val check = _lastCheck.value ?: return
        val modelId = check.modelId ?: return
        if (!check.labellable) return
        val genuine = label is CheckLabel.Person && label.id == check.bestFaceId
        _lastCheck.value = check.copy(labelled = true)
        scope.launch { calibration.add(CalibrationSample(modelId, genuine, check.score)) }
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
