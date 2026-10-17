package com.banglatrac.workopolo.face.core.engine

const val FRAME_W = 480
const val FRAME_H = 640

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

class FakeFrame(
    override val timestampMs: Long,
    override val faces: List<FaceObservation>,
    private val pixelTag: Int = 0,
) : FaceFrame {
    override val width = FRAME_W
    override val height = FRAME_H
    override val detectMs = 5L
    override fun alignedFace(face: FaceObservation): IntArray = IntArray(4) { pixelTag }
    override fun selfieJpeg(): ByteArray = byteArrayOf(1, 2, 3)
}

/** Embeds by pixel tag: tag 0 is "the enrolled person", any other tag is somebody else. */
class FakeEmbedder : FaceEmbedder {
    override val inputSize = 2
    override fun embed(argb: IntArray): FloatArray =
        if (argb.first() == 0) floatArrayOf(1f, 0f, 0f) else floatArrayOf(0f, 1f, 0f)
}
