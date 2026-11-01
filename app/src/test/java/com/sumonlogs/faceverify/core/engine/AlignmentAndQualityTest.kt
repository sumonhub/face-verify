package com.sumonlogs.faceverify.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class AlignmentAndQualityTest {

    @Test
    fun `least squares recovers a known rotation, scale and shift`() {
        val truth = Similarity(a = 0.8f * cos(0.3f), b = 0.8f * sin(0.3f), tx = 12f, ty = -5f)
        val src = listOf(Point(10f, 20f), Point(60f, 22f), Point(35f, 50f), Point(15f, 80f), Point(58f, 79f))
        val dst = src.map(truth::apply)
        val fit = FaceAlignment.leastSquares(src, dst)
        assertEquals(truth.a, fit.a, 1e-4f)
        assertEquals(truth.b, fit.b, 1e-4f)
        assertEquals(truth.tx, fit.tx, 1e-2f)
        assertEquals(truth.ty, fit.ty, 1e-2f)
    }

    @Test
    fun `eyes land on the template eye positions`() {
        val f = face().copy(rightEye = Point(200f, 300f), leftEye = Point(280f, 300f))
        val fit = requireNotNull(FaceAlignment.estimate(f, 112))
        val right = fit.apply(Point(200f, 300f))
        val left = fit.apply(Point(280f, 300f))
        assertEquals(38.29f, right.x, 0.5f)
        assertEquals(73.53f, left.x, 0.5f)
        assertTrue("eyes stay level", kotlin.math.abs(right.y - left.y) < 0.5f)
    }

    @Test
    fun `no alignment without both eyes`() {
        assertNull(FaceAlignment.estimate(face().copy(leftEye = null), 112))
    }

    @Test
    fun `crop quality flags dark, bright and flat crops`() {
        val config = FaceConfig()
        fun issue(gray: Int, contrast: Int) = CropQuality.issue(CropQuality.stats(fakeCrop(0, gray, contrast), FAKE_CROP), config)
        assertNull(issue(130, 40))
        assertEquals(QualityIssue.TooDark, issue(20, 10))
        assertNull("a dim but usable face is not turned away", issue(40, 40))
        assertEquals(QualityIssue.TooBright, issue(240, 10))
        assertEquals(QualityIssue.Blurry, issue(130, 0))
    }
}
