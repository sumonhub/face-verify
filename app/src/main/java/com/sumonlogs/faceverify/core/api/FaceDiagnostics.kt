package com.sumonlogs.faceverify.core.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.ceil

/** Numbers only: never put embeddings, images or anything identifying in here. */
data class FaceDebugInfo(
    val modelId: String? = null,
    val threshold: Float = 0f,
    val lastScore: Float? = null,
    /** Score of the second-best enrolled person; a small gap means the result was close. */
    val runnerUpScore: Float? = null,
    /** Id of the best-scoring enrolled person, matched or not (an opaque id, not a name). */
    val bestFaceId: String? = null,
    val lastMatched: Boolean? = null,
    val livenessChallenge: String? = null,
    val livenessPassed: Boolean? = null,
    /** Latest real-face probability from the anti-spoof model. */
    val spoofScore: Float? = null,
    val lastFailure: FaceFailure? = null,
    val qualityIssue: String? = null,
    val yaw: Float? = null,
    val pitch: Float? = null,
    val roll: Float? = null,
    val leftEyeOpen: Float? = null,
    val rightEyeOpen: Float? = null,
    val cropBrightness: Float? = null,
    val cropSharpness: Float? = null,
    val detectMs: Long? = null,
    val embedMs: Long? = null,
    val totalMs: Long? = null,
)

/** Timing of one pipeline stage over the recent past. */
data class StageTiming(val stage: String, val count: Int, val avgMs: Float, val p95Ms: Float)

/** Where a stage reports how long one run took. */
typealias TimingSink = (stage: String, ms: Float) -> Unit

object TimingStats {
    /** Mean and nearest-rank 95th percentile of [samples] (milliseconds). */
    fun summarize(stage: String, samples: List<Float>): StageTiming {
        if (samples.isEmpty()) return StageTiming(stage, 0, 0f, 0f)
        val sorted = samples.sorted()
        val rank = ceil(0.95 * sorted.size).toInt().coerceIn(1, sorted.size)
        return StageTiming(stage, sorted.size, sorted.average().toFloat(), sorted[rank - 1])
    }
}

/** The aligned face crop the model last saw, for the debug panel. */
class AlignedPreview(val pixels: IntArray, val size: Int)

/** Live engine numbers for a debug panel. Not part of [FaceVerifier]; the main app can ignore it. */
class FaceDiagnostics(private val publishTimingsEveryMs: Long = 500L) {
    private val _info = MutableStateFlow(FaceDebugInfo())
    val info: StateFlow<FaceDebugInfo> = _info.asStateFlow()

    fun update(transform: FaceDebugInfo.() -> FaceDebugInfo) = _info.update(transform)

    // ---------------------------------------------------------------- Stage timings

    private val samples = LinkedHashMap<String, ArrayDeque<Float>>()
    private var lastPublishedAt = 0L
    private val _stageTimings = MutableStateFlow<List<StageTiming>>(emptyList())

    /** Per-stage average and p95 over the last [MAX_SAMPLES] runs, refreshed about twice a second. */
    val stageTimings: StateFlow<List<StageTiming>> = _stageTimings.asStateFlow()

    fun recordTiming(stage: String, ms: Float) {
        val now = System.nanoTime() / 1_000_000
        val publish = synchronized(samples) {
            val queue = samples.getOrPut(stage) { ArrayDeque() }
            queue.addLast(ms)
            if (queue.size > MAX_SAMPLES) queue.removeFirst()
            (now - lastPublishedAt >= publishTimingsEveryMs).also { if (it) lastPublishedAt = now }
        }
        if (publish) _stageTimings.value = snapshot()
    }

    fun clearTimings() {
        synchronized(samples) { samples.clear() }
        _stageTimings.value = emptyList()
    }

    private fun snapshot(): List<StageTiming> = synchronized(samples) {
        samples.map { (stage, values) -> TimingStats.summarize(stage, values.toList()) }
    }

    // ---------------------------------------------------------------- Aligned face preview

    /** Off by default: only a test harness turns it on, so the real app never holds a face image here. */
    @Volatile
    var captureAlignedFace = false

    private val _alignedFace = MutableStateFlow<AlignedPreview?>(null)
    val alignedFace: StateFlow<AlignedPreview?> = _alignedFace.asStateFlow()

    fun showAlignedFace(pixels: IntArray, size: Int) {
        if (captureAlignedFace) _alignedFace.value = AlignedPreview(pixels.copyOf(), size)
    }

    private companion object {
        const val MAX_SAMPLES = 200
    }
}
