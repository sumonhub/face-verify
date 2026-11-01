package com.sumonlogs.faceverify.core.engine

/** The part of a camera frame a model should see, in upright frame pixels. */
data class CropBox(val left: Float, val top: Float, val width: Float, val height: Float)

/** Frame geometry shared by the crops. Pure: numbers in, numbers out. */
object FrameGeometry {

    /**
     * Maps pixels of the frame as the sensor delivered it ([rawWidth] × [rawHeight]) to upright pixels,
     * given the clockwise [rotationDegrees] CameraX asks for. Lets a crop read the raw bitmap directly
     * instead of first making a rotated copy of the whole frame.
     */
    fun rawToUpright(rotationDegrees: Int, rawWidth: Int, rawHeight: Int): Similarity = when (((rotationDegrees % 360) + 360) % 360) {
        90 -> Similarity(a = 0f, b = 1f, tx = rawHeight.toFloat(), ty = 0f)
        180 -> Similarity(a = -1f, b = 0f, tx = rawWidth.toFloat(), ty = rawHeight.toFloat())
        270 -> Similarity(a = 0f, b = -1f, tx = 0f, ty = rawWidth.toFloat())
        else -> Similarity(a = 1f, b = 0f, tx = 0f, ty = 0f)
    }

    /**
     * The face box enlarged [scale] times around its centre, as the anti-spoof models were trained.
     * The box is shrunk to fit the frame and then slid back inside it, never padded, so the crop only
     * holds real camera pixels.
     */
    fun enlargedBox(face: FaceObservation, scale: Float, frameWidth: Int, frameHeight: Int): CropBox {
        val maxX = frameWidth - 1f
        val maxY = frameHeight - 1f
        val w = face.width
        val h = face.bottom - face.top
        val k = minOf(maxY / h, maxX / w, scale)
        val newW = w * k
        val newH = h * k
        return CropBox(
            left = (face.centerX - newW / 2f).coerceIn(0f, maxX - newW),
            top = (face.centerY - newH / 2f).coerceIn(0f, maxY - newH),
            width = newW,
            height = newH,
        )
    }
}
