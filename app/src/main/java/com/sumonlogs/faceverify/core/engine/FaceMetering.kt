package com.sumonlogs.faceverify.core.engine

import kotlin.math.abs

/**
 * Where the camera's auto-exposure should look, in upright frame pixels: the middle of the face and
 * a square of side [size] around it.
 */
data class MeteringSpot(val x: Float, val y: Float, val size: Float)

/**
 * Points the camera's auto-exposure at the face. Left alone, the camera exposes for the whole frame,
 * so a window, a ceiling lamp or a white wall behind the person darkens the face until the crop reads
 * "too dark" in a room that is bright enough. Pure: decides when to re-aim, the camera layer does it.
 *
 * Re-aims when a face first appears and when it moves noticeably, but never more often than
 * [FaceConfig.meteringMinIntervalMs]: each new metering request restarts the exposure search.
 */
class FaceMetering(private val config: FaceConfig) {
    private var last: MeteringSpot? = null
    private var lastAtMs: Long? = null

    fun next(faces: List<FaceObservation>, frameWidth: Int, frameHeight: Int, nowMs: Long): MeteringSpot? {
        val face = faces.maxByOrNull { it.width } ?: return null
        // The middle of the face, a little narrower than the box, so it meters skin rather than hair or background.
        val spot = MeteringSpot(face.centerX, face.centerY, face.width * config.meteringFaceShare)
        val prev = last
        val since = lastAtMs?.let { nowMs - it }
        if (prev != null && since != null) {
            if (since < config.meteringMinIntervalMs) return null
            val limit = config.meteringMoveRatio * frameWidth.coerceAtLeast(frameHeight)
            val moved = abs(spot.x - prev.x) > limit || abs(spot.y - prev.y) > limit ||
                abs(spot.size - prev.size) > limit
            if (!moved) return null
        }
        last = spot
        lastAtMs = nowMs
        return spot
    }
}
