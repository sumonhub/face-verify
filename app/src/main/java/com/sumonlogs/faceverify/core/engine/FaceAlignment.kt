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
     * Person's right eye (image left), left eye, right mouth corner, left mouth corner. The template
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
     * Least-squares similarity from the face's landmarks to the template scaled to [size].
     * Uses the eyes and every mouth corner ML Kit found; needs at least both eyes, otherwise returns null.
     */
    fun estimate(face: FaceObservation, size: Int): Similarity? {
        val landmarks = listOf(face.rightEye, face.leftEye, face.mouthRight, face.mouthLeft)
        if (landmarks[0] == null || landmarks[1] == null) return null
        val scale = size / TEMPLATE_SIZE
        val src = mutableListOf<Point>()
        val dst = mutableListOf<Point>()
        landmarks.forEachIndexed { i, p ->
            if (p != null) {
                src += p
                dst += Point(TEMPLATE[i].x * scale, TEMPLATE[i].y * scale)
            }
        }
        return leastSquares(src, dst)
    }

    /** Closed-form least-squares similarity mapping [src] onto [dst] (same length, at least 2 points). */
    fun leastSquares(src: List<Point>, dst: List<Point>): Similarity {
        require(src.size == dst.size && src.size >= 2) { "Need at least two point pairs" }
        val n = src.size
        val sx = src.sumOf { it.x.toDouble() } / n
        val sy = src.sumOf { it.y.toDouble() } / n
        val dx = dst.sumOf { it.x.toDouble() } / n
        val dy = dst.sumOf { it.y.toDouble() } / n
        var num1 = 0.0
        var num2 = 0.0
        var den = 0.0
        for (i in 0 until n) {
            val px = src[i].x - sx
            val py = src[i].y - sy
            val qx = dst[i].x - dx
            val qy = dst[i].y - dy
            num1 += px * qx + py * qy
            num2 += px * qy - py * qx
            den += px * px + py * py
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
