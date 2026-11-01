package com.sumonlogs.faceverify.core.engine.android

import android.os.SystemClock
import com.sumonlogs.faceverify.core.api.FaceModelSettings
import com.sumonlogs.faceverify.core.api.TimingSink
import com.sumonlogs.faceverify.core.engine.SpoofCrops
import com.sumonlogs.faceverify.core.engine.SpoofDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Loads the models and runs one dummy inference through each, off the main and camera threads, so the
 * first real frame and the final match don't pay for model loading and first-run setup. Safe to call
 * from several screens: every model is warmed once, and a failure just leaves it to warm up lazily.
 */
class FaceWarmUp(
    private val detector: MlKitFaceDetector,
    private val embedders: FaceEmbedders,
    private val spoof: SpoofDetector,
    private val settings: FaceModelSettings,
    private val onTiming: TimingSink = { _, _ -> },
) {
    private val lock = Mutex()
    private var detectorWarm = false
    private var spoofWarm = false
    private val warmModels = mutableSetOf<String>()

    /** @param includeSpoof false for flows that never run the anti-spoof models (enrollment). */
    suspend fun run(includeSpoof: Boolean) = withContext(Dispatchers.Default) {
        lock.withLock {
            if (!detectorWarm) detectorWarm = attempt("warm.detector") { detector.warmUp() }
            val model = settings.current().model
            if (model.id !in warmModels) {
                val embedder = embedders.forModel(model)
                val blank = IntArray(embedder.inputSize * embedder.inputSize)
                if (attempt("warm.embed") { embedder.embed(listOf(blank)) }) warmModels += model.id
            }
            if (includeSpoof && !spoofWarm) {
                val blank = IntArray(spoof.inputSize * spoof.inputSize)
                spoofWarm = attempt("warm.spoof") { spoof.realProbability(SpoofCrops(blank, blank)) }
            }
        }
    }

    private inline fun attempt(stage: String, block: () -> Unit): Boolean {
        val t0 = SystemClock.elapsedRealtimeNanos()
        return try {
            block()
            onTiming(stage, (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000f)
            true
        } catch (e: Exception) {
            false
        }
    }
}
