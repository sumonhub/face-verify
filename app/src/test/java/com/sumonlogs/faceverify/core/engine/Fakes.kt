package com.sumonlogs.faceverify.core.engine

const val FRAME_W = 480
const val FRAME_H = 640
const val FAKE_CROP = 12

fun face(
    yaw: Float = 0f,
    pitch: Float = 0f,
    roll: Float = 0f,
    eyes: Float? = 0.95f,
    width: Float = 220f,
    cx: Float = FRAME_W / 2f,
    cy: Float = FRAME_H / 2f,
    trackingId: Int? = 1,
) = FaceObservation(
    left = cx - width / 2, top = cy - width * 0.65f, right = cx + width / 2, bottom = cy + width * 0.65f,
    yaw = yaw, pitch = pitch, roll = roll,
    leftEyeOpen = eyes, rightEyeOpen = eyes,
    leftEye = Point(cx + 40, cy - 30), rightEye = Point(cx - 40, cy - 30),
    trackingId = trackingId,
)

/**
 * A crop with real texture (so it passes the brightness and sharpness checks). The person is encoded
 * in the alpha byte, which CropQuality ignores: tag 0 is "Alex", any other tag is somebody else.
 */
fun fakeCrop(tag: Int, gray: Int = 130, contrast: Int = 40): IntArray = IntArray(FAKE_CROP * FAKE_CROP) { i ->
    val v = if ((i / FAKE_CROP + i % FAKE_CROP) % 2 == 0) gray + contrast else gray - contrast
    (tag shl 24) or (v shl 16) or (v shl 8) or v
}

class FakeFrame(
    override val timestampMs: Long,
    override val faces: List<FaceObservation>,
    private val pixelTag: Int = 0,
    private val gray: Int = 130,
    private val contrast: Int = 40,
) : FaceFrame {
    override val width = FRAME_W
    override val height = FRAME_H
    override val detectMs = 5L
    override fun alignedFace(face: FaceObservation): IntArray = fakeCrop(pixelTag, gray, contrast)
    override fun selfieJpeg(): ByteArray = byteArrayOf(1, 2, 3)
}

class FakeEmbedder : FaceEmbedder {
    override val model = FaceModels.SFace
    override val inputSize = FAKE_CROP
    var calls = 0
        private set

    override fun embed(faces: List<IntArray>): List<FloatArray> {
        calls++
        return faces.map { if (it.first() ushr 24 == 0) floatArrayOf(1f, 0f, 0f) else floatArrayOf(0f, 1f, 0f) }
    }
}
