package com.sumonlogs.faceverify.core.engine

/** Brightness is mean luma 0..255; sharpness is the variance of the Laplacian (higher = sharper). */
data class CropStats(val brightness: Float, val sharpness: Float)

/**
 * Quality of an aligned face crop, measured on its central part (cheeks, eyes, nose) so background
 * and hair don't count. Dark or blurry crops give vague embeddings that look alike across people.
 */
object CropQuality {

    fun stats(argb: IntArray, size: Int): CropStats {
        require(argb.size == size * size) { "Expected $size×$size pixels" }
        val luma = FloatArray(argb.size) { i ->
            val p = argb[i]
            0.299f * (p shr 16 and 0xFF) + 0.587f * (p shr 8 and 0xFF) + 0.114f * (p and 0xFF)
        }
        val from = size / 6
        val to = size - size / 6
        var sum = 0.0
        var count = 0
        var lapSum = 0.0
        var lapSq = 0.0
        var lapCount = 0
        for (y in from until to) {
            for (x in from until to) {
                val i = y * size + x
                sum += luma[i]
                count++
                if (x in 1 until size - 1 && y in 1 until size - 1) {
                    val lap = (luma[i - 1] + luma[i + 1] + luma[i - size] + luma[i + size] - 4f * luma[i]).toDouble()
                    lapSum += lap
                    lapSq += lap * lap
                    lapCount++
                }
            }
        }
        val mean = if (count == 0) 0.0 else sum / count
        val lapMean = if (lapCount == 0) 0.0 else lapSum / lapCount
        val variance = if (lapCount == 0) 0.0 else lapSq / lapCount - lapMean * lapMean
        return CropStats(brightness = mean.toFloat(), sharpness = variance.toFloat())
    }

    fun issue(stats: CropStats, config: FaceConfig): QualityIssue? = when {
        stats.brightness < config.minBrightness -> QualityIssue.TooDark
        stats.brightness > config.maxBrightness -> QualityIssue.TooBright
        stats.sharpness < config.minSharpness -> QualityIssue.Blurry
        else -> null
    }
}
