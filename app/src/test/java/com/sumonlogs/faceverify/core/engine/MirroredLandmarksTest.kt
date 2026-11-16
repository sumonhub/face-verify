package com.sumonlogs.faceverify.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * Regression: on a OnePlus front camera ML Kit labelled the eyes and mouth corners mirrored (its
 * "right" eye on the image's right). Matched by label, the fit collapsed to scale 0.057 and the whole
 * frame was squeezed into a corner of the crop: "too dark" in good light and strangers scoring 0.9.
 * These are the landmarks logged on that phone.
 */
class MirroredLandmarksTest {
    private val logged = face().copy(
        rightEye = Point(310.78f, 306.42f), leftEye = Point(204.22f, 313.32f),
        mouthRight = Point(300.20f, 438.04f), mouthLeft = Point(217.22f, 445.23f),
    )

    @Test
    fun `mirrored labels still give a face-sized crop`() {
        val s = FaceAlignment.estimate(logged, 112)!!
        val scale = hypot(s.a, s.b)
        // Eye distance ~107 px onto the template's ~35 px.
        assertTrue("scale $scale", scale in 0.28f..0.38f)
        // The image-left eye lands on the template's left eye.
        val leftEye = s.apply(Point(204.22f, 313.32f))
        assertEquals(38.3f, leftEye.x, 3f)
        assertEquals(51.7f, leftEye.y, 3f)
    }

    @Test
    fun `label order does not change the fit`() {
        val swapped = logged.copy(
            rightEye = logged.leftEye, leftEye = logged.rightEye,
            mouthRight = logged.mouthLeft, mouthLeft = logged.mouthRight,
        )
        assertEquals(FaceAlignment.estimate(logged, 112), FaceAlignment.estimate(swapped, 112))
    }
}
