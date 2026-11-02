package com.sumonlogs.faceverify.core.engine

enum class PixelNormalization {
    /** (x − 127.5) / 128, as MobileFaceNet was trained. */
    MinusOneToOne,

    /** 0..255 as is; the model normalises internally. */
    Raw,
}

/**
 * A face-embedding model shipped in assets. Embeddings from different models can't be compared,
 * so every enrollment records which model made it.
 */
data class FaceModelSpec(
    val id: String,
    val displayName: String,
    val assetFile: String,
    val normalization: PixelNormalization,
    /** Starting cosine threshold. Strict on purpose; calibrate on real people and override it in the settings. */
    val defaultThreshold: Float,
)

object FaceModels {
    /** OpenCV SFace, Apache-2.0. OpenCV's own reference threshold is 0.363; we start far stricter, at 0.75. */
    val SFace = FaceModelSpec(
        id = "sface-2021dec",
        displayName = "SFace",
        assetFile = "SFace.tflite",
        normalization = PixelNormalization.Raw,
        defaultThreshold = 0.75f,
    )

    /** MobileFaceNet converted by a third party; licence of the weights unclear (see its NOTICE). */
    val MobileFaceNet = FaceModelSpec(
        id = "mobilefacenet",
        displayName = "MobileFaceNet",
        assetFile = "MobileFaceNet.tflite",
        normalization = PixelNormalization.MinusOneToOne,
        defaultThreshold = 0.75f,
    )

    val all: List<FaceModelSpec> = listOf(SFace, MobileFaceNet)

    val default: FaceModelSpec = SFace

    fun byId(id: String?): FaceModelSpec? = all.firstOrNull { it.id == id }
}
