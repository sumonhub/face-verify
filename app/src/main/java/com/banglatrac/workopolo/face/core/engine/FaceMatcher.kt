package com.banglatrac.workopolo.face.core.engine

import kotlin.math.sqrt

data class MatchScore(val score: Float, val threshold: Float) {
    val matched: Boolean get() = score >= threshold
}

/** Cosine similarity between L2-normalised embeddings. Pure: FloatArray in, numbers out. */
object FaceMatcher {

    fun l2Normalize(v: FloatArray): FloatArray {
        var sum = 0.0
        for (x in v) sum += x * x
        val norm = sqrt(sum).toFloat()
        if (norm < 1e-10f) return v.copyOf()
        return FloatArray(v.size) { v[it] / norm }
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embedding sizes differ: ${a.size} vs ${b.size}" }
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = sqrt(na) * sqrt(nb)
        return if (denom < 1e-10) 0f else (dot / denom).toFloat()
    }

    /** Best similarity of [probe] against any enrolled template, clamped to 0..1. */
    fun match(probe: FloatArray, templates: List<FloatArray>, threshold: Float): MatchScore {
        val best = templates.maxOfOrNull { cosine(probe, it) } ?: 0f
        return MatchScore(score = best.coerceIn(0f, 1f), threshold = threshold)
    }
}
