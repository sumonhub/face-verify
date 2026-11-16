package com.sumonlogs.faceverify.core.engine

/** x' = a·x − b·y + tx,  y' = b·x + a·y + ty (rotation + uniform scale + translation, no mirroring). */
data class Similarity(val a: Float, val b: Float, val tx: Float, val ty: Float) {
    fun apply(p: Point): Point = Point(a * p.x - b * p.y + tx, b * p.x + a * p.y + ty)
}

/**
 * Face alignment to the ArcFace 112×112 template that both face models were trained with.
 * Pure: points in, transform out.
 */
object FaceAlignment {

    /**
     * Image-left eye, image-right eye, image-left mouth corner, image-right mouth corner. The template
     * also has a nose point, (56.03, 71.74), but that is the nose TIP while ML Kit reports the nose BASE,
     * lower on the face. Fitting one to the other would pull every crop off by a pitch-dependent amount,
     * so the nose is left out and the four points that mean the same thing in both are used.
     */
    private val TEMPLATE = listOf(
        Point(38.2946f, 51.6963f),
        Point(73.5318f, 51.5014f),
        Point(41.5493f, 92.3655f),
        Point(70.7299f, 92.2041f),
    )
    private const val TEMPLATE_SIZE = 112f

    /**
     * How much each template point is trusted, in the same order. The eyes are rigid; the mouth corners
     * move with every smile, pout and word, and the template is a neutral mouth, so they pull the fit
     * around with the expression unless they count for less.
     */
    private val WEIGHTS = listOf(1f, 1f, 0.5f, 0.5f)

    /**
     * Weighted least-squares similarity from the face's landmarks to the template scaled to [size].
     * Uses the eyes and every mouth corner ML Kit found; needs at least both eyes, otherwise returns null.
     */
    fun estimate(face: FaceObservation, size: Int): Similarity? {
        val eyeA = face.rightEye ?: return null
        val eyeB = face.leftEye ?: return null
        // Matched to the template by where the points are in the image, not by ML Kit's left/right
        // labels: on a front camera they come out mirrored (the "right" eye on the image's right), and a
        // similarity can't mirror, so the fit collapsed to a tiny scale and the whole frame was squeezed
        // into a corner of the crop. Roll is limited to a few degrees, so x order is safe.
        val (imageLeftEye, imageRightEye) = if (eyeA.x <= eyeB.x) eyeA to eyeB else eyeB to eyeA
        val eyeMidX = (eyeA.x + eyeB.x) / 2f
        val mouthA = face.mouthRight
        val mouthB = face.mouthLeft
        val (imageLeftMouth, imageRightMouth) = when {
            mouthA != null && mouthB != null -> if (mouthA.x <= mouthB.x) mouthA to mouthB else mouthB to mouthA
            mouthA != null -> if (mouthA.x <= eyeMidX) mouthA to null else null to mouthA
            mouthB != null -> if (mouthB.x <= eyeMidX) mouthB to null else null to mouthB
            else -> null to null
        }
        val landmarks = listOf(imageLeftEye, imageRightEye, imageLeftMouth, imageRightMouth)
        val scale = size / TEMPLATE_SIZE
        val src = mutableListOf<Point>()
        val dst = mutableListOf<Point>()
        val weights = mutableListOf<Float>()
        landmarks.forEachIndexed { i, p ->
            if (p != null) {
                src += p
                dst += Point(TEMPLATE[i].x * scale, TEMPLATE[i].y * scale)
                weights += WEIGHTS[i]
            }
        }
        return leastSquares(src, dst, weights)
    }

    /**
     * Closed-form least-squares similarity mapping [src] onto [dst] (same length, at least 2 points),
     * each pair counting [weights] times as much (all equal by default).
     */
    fun leastSquares(src: List<Point>, dst: List<Point>, weights: List<Float> = List(src.size) { 1f }): Similarity {
        require(src.size == dst.size && src.size >= 2) { "Need at least two point pairs" }
        require(weights.size == src.size && weights.all { it > 0f }) { "One positive weight per point pair" }
        val n = src.size
        val total = weights.sumOf { it.toDouble() }
        val sx = src.indices.sumOf { weights[it] * src[it].x.toDouble() } / total
        val sy = src.indices.sumOf { weights[it] * src[it].y.toDouble() } / total
        val dx = dst.indices.sumOf { weights[it] * dst[it].x.toDouble() } / total
        val dy = dst.indices.sumOf { weights[it] * dst[it].y.toDouble() } / total
        var num1 = 0.0
        var num2 = 0.0
        var den = 0.0
        for (i in 0 until n) {
            val w = weights[i].toDouble()
            val px = src[i].x - sx
            val py = src[i].y - sy
            val qx = dst[i].x - dx
            val qy = dst[i].y - dy
            num1 += w * (px * qx + py * qy)
            num2 += w * (px * qy - py * qx)
            den += w * (px * px + py * py)
        }
        require(den > 1e-9) { "Source points coincide" }
        val a = num1 / den
        val b = num2 / den
        return Similarity(
            a = a.toFloat(),
            b = b.toFloat(),
            tx = (dx - (a * sx - b * sy)).toFloat(),
            ty = (dy - (b * sx + a * sy)).toFloat(),
        )
    }
}
