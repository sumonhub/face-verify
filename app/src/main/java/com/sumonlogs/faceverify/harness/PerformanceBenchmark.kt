package com.sumonlogs.faceverify.harness

import android.content.Context
import com.sumonlogs.faceverify.core.api.StageTiming
import com.sumonlogs.faceverify.core.api.TimingStats
import com.sumonlogs.faceverify.core.engine.FaceModelSpec
import com.sumonlogs.faceverify.core.engine.SpoofCrops
import com.sumonlogs.faceverify.core.engine.android.LiteRtFaceEmbedder
import com.sumonlogs.faceverify.core.engine.android.LiteRtSpoofDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One benchmark line: a model run with [threads] CPU threads. */
data class BenchRow(val label: String, val threads: Int, val timing: StageTiming)

sealed interface BenchState {
    data object Idle : BenchState
    data object Running : BenchState
    data class Done(val rows: List<BenchRow>) : BenchState
}

/**
 * Times each model with 1, 2 and 4 CPU threads on this phone, so the thread setting in FaceConfig can
 * be chosen from numbers. Dummy input: how long a network takes does not depend on the picture.
 * Run it on a release build with nothing else going on.
 */
class PerformanceBenchmark(private val context: Context) {

    suspend fun run(model: FaceModelSpec): List<BenchRow> = withContext(Dispatchers.Default) {
        THREADS.flatMap { threads ->
            embedRow(model, threads) + spoofRows(threads)
        }
    }

    private fun embedRow(model: FaceModelSpec, threads: Int): List<BenchRow> =
        LiteRtFaceEmbedder(context, model, threads).use { embedder ->
            val blank = IntArray(embedder.inputSize * embedder.inputSize)
            listOf(measure("${model.displayName} ×1", threads) { embedder.embed(listOf(blank)) })
        }

    private fun spoofRows(threads: Int): List<BenchRow> =
        LiteRtSpoofDetector(context, threads).use { detector ->
            val blank = IntArray(detector.inputSize * detector.inputSize)
            val crops = SpoofCrops(blank, blank)
            listOf(measure("Anti-spoof (both)", threads) { detector.realProbability(crops) })
        }

    private fun measure(label: String, threads: Int, work: () -> Unit): BenchRow {
        repeat(WARM_UP_RUNS) { work() }
        val times = List(RUNS) {
            val t0 = System.nanoTime()
            work()
            (System.nanoTime() - t0) / 1_000_000f
        }
        return BenchRow(label, threads, TimingStats.summarize(label, times))
    }

    private companion object {
        val THREADS = listOf(1, 2, 4)
        const val WARM_UP_RUNS = 3
        const val RUNS = 20
    }
}
