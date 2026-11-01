package com.sumonlogs.faceverify.core.engine

/**
 * The same face cropped at the two enlargements the anti-spoof models were trained on, each as
 * [SpoofDetector.inputSize] × [SpoofDetector.inputSize] ARGB pixels. The wide context around the face
 * (screen bezel, paper edge, hand) is what gives most attacks away, so these are not aligned crops.
 */
class SpoofCrops(val near: IntArray, val far: IntArray)

/** Passive anti-spoofing: tells a live face from a photo, a screen replay or a print. */
interface SpoofDetector {
    /** Side of the square input crops, in pixels. */
    val inputSize: Int

    /** Probability 0..1 that the face is real. */
    fun realProbability(crops: SpoofCrops): Float
}
