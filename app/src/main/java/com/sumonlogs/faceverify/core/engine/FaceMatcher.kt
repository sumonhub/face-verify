package com.sumonlogs.faceverify.core.engine

import kotlin.math.sqrt

/**
 * Result of comparing a probe against every enrolled person.
 * [bestIndex] is the index of the best-scoring person, or null when nobody is enrolled.
 */
data class Identification(
    val bestIndex: Int?,
    val score: Float,
    val runnerUpScore: Float,
    val threshold: Float,
    val margin: Float,
) {
    private val aboveThreshold: Boolean get() = bestIndex != null && score >= threshold

    val matched: Boolean get() = aboveThreshold && score - runnerUpScore >= margin

    /** Someone passed the threshold, but a second person was too close to be sure who. */
    val ambiguous: Boolean get() = aboveThreshold && score - runnerUpScore < margin
}

/** Cosine similarity between embeddings. Pure: FloatArray in, numbers out. */
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

    /** Normalised mean of normalised embeddings: one stable vector for several frames. */
    fun centroid(embeddings: List<FloatArray>): FloatArray {
        require(embeddings.isNotEmpty()) { "No embeddings" }
        val dim = embeddings.first().size
        val sum = FloatArray(dim)
        embeddings.forEach { e ->
            val n = l2Normalize(e)
            for (i in 0 until dim) sum[i] += n[i]
        }
        return l2Normalize(sum)
    }

    /**
     * Similarity of [probe] to one person, clamped to 0..1: against the average of their templates,
     * not the best single one, so one odd template can't let a stranger in.
     */
    fun score(probe: FloatArray, templates: List<FloatArray>): Float =
        if (templates.isEmpty()) 0f else cosine(probe, centroid(templates)).coerceIn(0f, 1f)

    /** 1:N identification: which of [people] (each a list of templates) the probe is, if any. */
    fun identify(probe: FloatArray, people: List<List<FloatArray>>, threshold: Float, margin: Float): Identification {
        val scores = people.map { score(probe, it) }
        val best = scores.indices.maxByOrNull { scores[it] }
        val runnerUp = scores.filterIndexed { i, _ -> i != best }.maxOrNull() ?: 0f
        return Identification(
            bestIndex = best,
            score = best?.let { scores[it] } ?: 0f,
            runnerUpScore = runnerUp,
            threshold = threshold,
            margin = margin,
        )
    }
}
