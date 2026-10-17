package com.sumonlogs.faceverify.core.engine

data class Point(val x: Float, val y: Float)

/**
 * One detected face in upright frame coordinates (pixels).
 * Angles follow ML Kit: [yaw] > 0 means the person turned to their left, [pitch] > 0 means looking up.
 */
data class FaceObservation(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    val leftEyeOpen: Float?,
    val rightEyeOpen: Float?,
    /** The person's own left / right eye (so [leftEye] appears on the image's right). */
    val leftEye: Point?,
    val rightEye: Point?,
    val trackingId: Int?,
) {
    val width: Float get() = right - left
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/** A frame handed to a session. Pixel access is only valid while the session's onFrame call runs. */
interface FaceFrame {
    val timestampMs: Long
    val width: Int
    val height: Int
    val faces: List<FaceObservation>
    val detectMs: Long

    /** The face cropped and aligned to the model input, as ARGB pixels, or null if it can't be cropped. */
    fun alignedFace(face: FaceObservation): IntArray?

    /** The whole frame as a downscaled JPEG, kept as evidence of the check. */
    fun selfieJpeg(): ByteArray?
}
