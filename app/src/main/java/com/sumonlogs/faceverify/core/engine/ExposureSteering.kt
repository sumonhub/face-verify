package com.sumonlogs.faceverify.core.engine

/**
 * Brightens or darkens the camera until the face itself is well exposed. Auto-exposure aims at the
 * whole picture (and many front cameras ignore a metering point), so under a ceiling light or with a
 * window behind, an ordinary room gives a face too dark to use. Exposure compensation (+EV) works on
 * almost every camera, so this steers it from the measured face brightness. Pure: says how many EV to
 * move, the camera layer applies it, and only between the camera's own exposure and
 * [FaceConfig.maxExposureBoostEv] above it, so a negative step only takes back an earlier boost.
 */
class ExposureSteering(private val config: FaceConfig) {
    private var lastAtMs: Long? = null

    /** EV to add (negative to darken) for a face of mean luma [faceLuma] (0..255), or 0 to leave it. */
    fun step(faceLuma: Float, nowMs: Long): Float {
        val last = lastAtMs
        // Each change takes a few frames to show; wait for it before judging again.
        if (last != null && nowMs - last < config.exposureStepIntervalMs) return 0f
        val ev = when {
            faceLuma < config.faceLumaLow / 2f -> 1f
            faceLuma < config.faceLumaLow -> 0.5f
            faceLuma > config.faceLumaHigh -> -0.5f
            else -> 0f
        }
        if (ev != 0f) lastAtMs = nowMs
        return ev
    }
}
