package com.sumonlogs.faceverify.core.engine

/** Turns an aligned face crop into an L2-normalised embedding. */
interface FaceEmbedder {
    /** Side of the square input crop, in pixels. */
    val inputSize: Int

    /** @param argb [inputSize] × [inputSize] ARGB pixels, row by row. */
    fun embed(argb: IntArray): FloatArray
}
