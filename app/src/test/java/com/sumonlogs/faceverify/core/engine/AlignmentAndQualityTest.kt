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
    fun `the nose is not part of the fit`() {
        // ML Kit reports the nose base, the template holds the nose tip: they must not be fitted to each other.
        val base = face().copy(rightEye = Point(200f, 300f), leftEye = Point(280f, 300f),
            mouthRight = Point(210f, 380f), mouthLeft = Point(270f, 380f))
        val a = requireNotNull(FaceAlignment.estimate(base.copy(noseBase = Point(240f, 340f)), 112))
        val b = requireNotNull(FaceAlignment.estimate(base.copy(noseBase = Point(250f, 365f)), 112))
        assertEquals(a, b)
    }

    @Test
    fun `mouth corners steer the fit`() {
        val eyes = face().copy(rightEye = Point(200f, 300f), leftEye = Point(280f, 300f))
        val withMouth = eyes.copy(mouthRight = Point(210f, 380f), mouthLeft = Point(270f, 380f))
        val fit = requireNotNull(FaceAlignment.estimate(withMouth, 112))
        assertEquals(41.55f, fit.apply(Point(210f, 380f)).x, 1.5f)
        assertEquals(92.3f, fit.apply(Point(210f, 380f)).y, 1.5f)
    }

    @Test
    fun `weights do not change an exact fit`() {
        val truth = Similarity(a = 0.6f * cos(0.2f), b = 0.6f * sin(0.2f), tx = 3f, ty = 9f)
        val src = listOf(Point(10f, 20f), Point(60f, 22f), Point(35f, 50f), Point(15f, 80f))
        val fit = FaceAlignment.leastSquares(src, src.map(truth::apply), listOf(1f, 1f, 0.5f, 0.5f))
        assertEquals(truth.a, fit.a, 1e-4f)
        assertEquals(truth.b, fit.b, 1e-4f)
        assertEquals(truth.tx, fit.tx, 1e-2f)
        assertEquals(truth.ty, fit.ty, 1e-2f)
    }

    @Test
    fun `a smile moves the eyes less than it would with equal weights`() {
        // Perfect eyes, and mouth corners pulled 10 px outward each side (a broad smile).
        val eyes = listOf(Point(200f, 300f), Point(280f, 300f))
        val smile = eyes + listOf(Point(200f, 380f), Point(280f, 380f))
        val smiling = face().copy(rightEye = smile[0], leftEye = smile[1], mouthRight = smile[2], mouthLeft = smile[3])
        val weighted = requireNotNull(FaceAlignment.estimate(smiling, 112))
        val template = listOf(Point(38.2946f, 51.6963f), Point(73.5318f, 51.5014f), Point(41.5493f, 92.3655f), Point(70.7299f, 92.2041f))
        val equal = FaceAlignment.leastSquares(smile, template)

        fun eyeError(fit: Similarity) = eyes.indices.sumOf { i ->
            val p = fit.apply(eyes[i])
            val dx = (p.x - template[i].x).toDouble()
            val dy = (p.y - template[i].y).toDouble()
            kotlin.math.sqrt(dx * dx + dy * dy)
        }
        assertTrue("weighted ${eyeError(weighted)} vs equal ${eyeError(equal)}", eyeError(weighted) < eyeError(equal))
    }

    @Test
    fun `matching frames must be nearly straight on`() {
        val config = FaceConfig()
        assertTrue(FrameQuality.isFront(face(yaw = 7f), config))
        assertTrue(FrameQuality.isFront(face(yaw = -7f, pitch = 10f), config))
        assertTrue("past the capture limit", !FrameQuality.isFront(face(yaw = 10f), config))
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

    /** A crop that is dark on the left half and bright on the right, with texture so it is not "flat". */
    private fun sideLit(left: Int, right: Int): IntArray = IntArray(FAKE_CROP * FAKE_CROP) { i ->
        val x = i % FAKE_CROP
        val base = if (x < FAKE_CROP / 2) left else right
        val v = if ((i / FAKE_CROP + x) % 2 == 0) base + 40 else base - 40
        (v shl 16) or (v shl 8) or v
    }

    @Test
    fun `strongly one-sided light is turned away, mild shading is not`() {
        val config = FaceConfig()
        fun issue(left: Int, right: Int) = CropQuality.issue(CropQuality.stats(sideLit(left, right), FAKE_CROP), config)
        assertNull("even light", issue(130, 130))
        assertNull("mild shading is normal indoors", issue(115, 145))
        assertEquals(QualityIssue.BadLighting, issue(70, 190))
    }

    @Test
    fun `a washed out crop with some texture but little contrast is flagged`() {
        val config = FaceConfig()
        val stats = CropQuality.stats(fakeCrop(0, gray = 130, contrast = 12), FAKE_CROP)
        assertEquals(12f, stats.contrast, 0.5f)
        // Sharp enough to pass the blur check at this contrast only if the Laplacian is large; force it.
        assertEquals(QualityIssue.BadLighting, CropQuality.issue(stats.copy(sharpness = 100f), config))
    }
}
