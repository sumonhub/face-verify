package com.sumonlogs.faceverify.core.engine

/** Turns aligned face crops into L2-normalised embeddings. */
interface FaceEmbedder {
    val model: FaceModelSpec

    /** Side of the square input crop, in pixels. */
    val inputSize: Int

    /** @param faces each [inputSize] × [inputSize] ARGB pixels, row by row. Returns one embedding per face. */
    fun embed(faces: List<IntArray>): List<FloatArray>
}
