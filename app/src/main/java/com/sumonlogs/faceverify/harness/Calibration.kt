package com.sumonlogs.faceverify.harness

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Locale

/**
 * One labelled face check. [genuine] is true when the best-scoring enrolled person really was the
 * one in front of the camera; false when it was someone else or nobody enrolled. Numbers only.
 */
data class CalibrationSample(val modelId: String, val genuine: Boolean, val score: Float)

/** Score distributions for one model and what they mean for a threshold. Pure. */
data class CalibrationStats(val genuine: List<Float>, val impostor: List<Float>) {

    fun falseAccepts(threshold: Float): Int = impostor.count { it >= threshold }

    fun falseRejects(threshold: Float): Int = genuine.count { it < threshold }

    /** True when some stranger scored at least as high as some right person. */
    val overlaps: Boolean
        get() = genuine.isNotEmpty() && impostor.isNotEmpty() && (impostor.maxOrNull() ?: 0f) >= (genuine.minOrNull() ?: 1f)

    /**
     * Lowest threshold that would have kept every labelled stranger out, placed midway into the gap
     * when there is one. Security first: with an overlap it still sits just above the worst stranger.
     */
    val suggestedThreshold: Float?
        get() {
            val worstStranger = impostor.maxOrNull() ?: return null
            val weakestGenuine = genuine.minOrNull()
            return if (weakestGenuine != null && weakestGenuine > worstStranger) {
                (worstStranger + weakestGenuine) / 2f
            } else {
                (worstStranger + MARGIN).coerceAtMost(1f)
            }
        }

    companion object {
        private const val MARGIN = 0.02f

        fun of(samples: List<CalibrationSample>, modelId: String): CalibrationStats {
            val forModel = samples.filter { it.modelId == modelId }
            return CalibrationStats(
                genuine = forModel.filter { it.genuine }.map { it.score },
                impostor = forModel.filterNot { it.genuine }.map { it.score },
            )
        }
    }
}

private val Context.harnessDataStore by preferencesDataStore(name = "harness")

/** Keeps the labelled samples on this phone (harness only). */
class CalibrationLog(private val context: Context) {
    private val key = stringPreferencesKey("calibration_samples")

    val samples: Flow<List<CalibrationSample>> = context.harnessDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[key].orEmpty().split('|').mapNotNull(::parse) }

    suspend fun add(sample: CalibrationSample) {
        context.harnessDataStore.edit { prefs ->
            val kept = prefs[key].orEmpty().split('|').filter { it.isNotBlank() }.takeLast(MAX_SAMPLES - 1)
            prefs[key] = (kept + format(sample)).joinToString("|")
        }
    }

    suspend fun clear(modelId: String) {
        context.harnessDataStore.edit { prefs ->
            prefs[key] = prefs[key].orEmpty().split('|').mapNotNull(::parse)
                .filterNot { it.modelId == modelId }
                .joinToString("|", transform = ::format)
        }
    }

    private fun format(s: CalibrationSample) =
        "${s.modelId};${if (s.genuine) 1 else 0};${String.format(Locale.US, "%.5f", s.score)}"

    private fun parse(line: String): CalibrationSample? {
        val parts = line.split(';')
        if (parts.size != 3) return null
        val score = parts[2].toFloatOrNull() ?: return null
        return CalibrationSample(parts[0], parts[1] == "1", score)
    }

    private companion object {
        const val MAX_SAMPLES = 500
    }
}
