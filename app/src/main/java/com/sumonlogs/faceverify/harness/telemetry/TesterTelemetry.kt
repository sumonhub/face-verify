package com.sumonlogs.faceverify.harness.telemetry

import com.sumonlogs.faceverify.core.api.AlignedPreview
import com.sumonlogs.faceverify.core.api.EnrolledFace
import com.sumonlogs.faceverify.core.api.FaceDebugInfo
import com.sumonlogs.faceverify.core.api.FaceMatchResult
import com.sumonlogs.faceverify.core.api.StageTiming
import com.sumonlogs.faceverify.core.engine.FaceTemplates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Sends one anonymous record per face check, and crash reports, but only after the tester agreed.
 * Test app only: the reusable face feature never sees this.
 */
class TesterTelemetry(
    private val prefs: TesterPrefs,
    private val uploader: CheckUploader,
    private val appVersion: String,
    private val device: String,
    private val sdk: Int,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** False when this build has no Firebase project: the screens hide everything about sharing. */
    val available: Boolean get() = uploader.available

    val consent: StateFlow<ConsentState> = prefs.consent.stateIn(scope, SharingStarted.Eagerly, ConsentState.Loading)
    val cropConsent: StateFlow<ConsentState> = prefs.cropConsent.stateIn(scope, SharingStarted.Eagerly, ConsentState.Loading)
    val installId: StateFlow<String?> = prefs.installId.stateIn(scope, SharingStarted.Eagerly, null)
    val uploads: StateFlow<UploadStats> = uploader.stats

    private val _setup = MutableStateFlow(TesterSetup())
    val setup: StateFlow<TesterSetup> = _setup.asStateFlow()

    init {
        // Crash reports follow the tester's choice, including at the next app start.
        scope.launch { consent.collect { uploader.setCrashReporting(it == ConsentState.Granted) } }
    }

    fun updateSetup(setup: TesterSetup) {
        _setup.value = setup
    }

    fun setConsent(granted: Boolean) {
        scope.launch {
            prefs.setConsent(granted)
            if (granted) prefs.installId()
        }
    }

    fun setCropConsent(granted: Boolean) {
        scope.launch { prefs.setCropConsent(granted) }
    }

    /**
     * @param usable the enrolled people of the model that was used; if there is exactly one, the "me"
     * scenario means them without the tester having to pick.
     * @param crop the aligned face the model last saw; sent only if the tester also agreed to share crops.
     */
    fun record(
        result: FaceMatchResult,
        info: FaceDebugInfo,
        timings: List<StageTiming>,
        usable: List<EnrolledFace>,
        crop: AlignedPreview? = null,
    ) {
        if (!uploader.available || consent.value != ConsentState.Granted) return
        val setup = _setup.value
        val expected = if (setup.scenario == TestScenario.Me) setup.expectedFaceId ?: usable.singleOrNull()?.id else null
        scope.launch {
            val record = CheckRecord(
                installId = prefs.installId(),
                appVersion = appVersion,
                device = device,
                sdk = sdk,
                pipelineVersion = FaceTemplates.PIPELINE_VERSION,
                takenAtMillis = clock(),
                modelId = info.modelId,
                threshold = info.threshold,
                scenario = setup.scenario.code,
                lighting = setup.lighting.code,
                glasses = setup.glasses,
                faceCovered = setup.faceCovered,
                expectedFaceId = expected,
                matchedFaceId = result.face?.id,
                enrolledCount = usable.size,
                matched = result.matched,
                failure = result.failure?.name,
                score = result.score,
                runnerUp = info.runnerUpScore,
                spoofScore = info.spoofScore,
                challenge = info.livenessChallenge,
                livenessPassed = result.livenessPassed,
                faceBrightness = info.cropBrightness,
                faceSharpness = info.cropSharpness,
                qualityIssue = info.qualityIssue,
                detectMs = info.detectMs,
                embedMs = info.embedMs,
                totalMs = info.totalMs,
                timingAvgMs = timings.associate { it.stage to it.avgMs },
                timingP95Ms = timings.associate { it.stage to it.p95Ms },
            )
            uploader.upload(record.toFields())
            if (crop != null && expected != null && cropConsent.value == ConsentState.Granted &&
                CropRecord.eligible(record.scenario, record.livenessPassed, record.failure)
            ) {
                uploader.uploadCrop(
                    CropRecord(
                        installId = record.installId,
                        appVersion = appVersion,
                        device = device,
                        pipelineVersion = FaceTemplates.PIPELINE_VERSION,
                        takenAtMillis = record.takenAtMillis,
                        person = CropRecord.personKey(record.installId, expected),
                        session = CropRecord.sessionKey(record.takenAtMillis, record.lighting, record.glasses, record.faceCovered),
                        lighting = record.lighting,
                        glasses = record.glasses,
                        faceCovered = record.faceCovered,
                        matched = record.matched,
                        score = record.score,
                        png = CropEncoder.png(crop),
                    ).toFields(),
                )
            }
        }
    }
}
