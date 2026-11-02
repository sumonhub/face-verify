package com.sumonlogs.faceverify.core.engine.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.sumonlogs.faceverify.core.engine.FaceAlignment
import com.sumonlogs.faceverify.core.engine.FaceObservation
import com.sumonlogs.faceverify.core.engine.FrameGeometry
import com.sumonlogs.faceverify.core.engine.Similarity
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Bitmap work for a frame: face alignment, anti-spoof crops and the evidence JPEG. */
class FaceCropper {

    /**
     * A camera frame as the sensor delivered it, plus how far it must turn to be upright. Face
     * coordinates are upright, so crops fold that turn into their own transform and the full frame is
     * never rotated (only the small evidence JPEG needs an upright copy).
     */
    class RawFrame(val bitmap: Bitmap, val rotationDegrees: Int) {
        val uprightWidth: Int get() = if (rotationDegrees % 180 == 0) bitmap.width else bitmap.height
        val uprightHeight: Int get() = if (rotationDegrees % 180 == 0) bitmap.height else bitmap.width

        /** Raw pixel coordinates to upright coordinates. */
        internal val toUpright: Matrix = FrameGeometry.rawToUpright(rotationDegrees, bitmap.width, bitmap.height).toMatrix()

        fun recycle() = bitmap.recycle()
    }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    fun raw(proxy: ImageProxy): RawFrame = RawFrame(proxy.toBitmap(), proxy.imageInfo.rotationDegrees)

    /**
     * Aligns the face onto the ArcFace 112×112 template with a least-squares fit over every landmark
     * ML Kit found (eyes and mouth corners), as both models were trained. Falls back to a square
     * box crop when the eyes are missing.
     */
    fun align(frame: RawFrame, face: FaceObservation, size: Int): IntArray {
        val similarity = FaceAlignment.estimate(face, size)
        val matrix = if (similarity != null) {
            similarity.toMatrix()
        } else {
            val side = max(face.width, face.bottom - face.top)
            val s = size / side
            Matrix().apply {
                setScale(s, s)
                preTranslate(-(face.centerX - side / 2f), -(face.centerY - side / 2f))
            }
        }
        return render(frame, matrix, size)
    }

    /**
     * The face box enlarged [scale] times around its centre and resized to [size]×[size], as the
     * anti-spoof models were trained (see [FrameGeometry.enlargedBox]).
     */
    fun spoofCrop(frame: RawFrame, face: FaceObservation, scale: Float, size: Int): IntArray {
        val box = FrameGeometry.enlargedBox(face, scale, frame.uprightWidth, frame.uprightHeight)
        val matrix = Matrix().apply {
            setScale(size / box.width, size / box.height)
            preTranslate(-box.left, -box.top)
        }
        return render(frame, matrix, size)
    }

    /** Evidence photo: the whole frame, upright, downscaled to [maxSide]. */
    fun jpeg(frame: RawFrame, maxSide: Int, quality: Int): ByteArray {
        val upright = if (frame.rotationDegrees == 0) {
            frame.bitmap
        } else {
            val turn = Matrix().apply { postRotate(frame.rotationDegrees.toFloat()) }
            Bitmap.createBitmap(frame.bitmap, 0, 0, frame.bitmap.width, frame.bitmap.height, turn, true)
        }
        val longest = max(upright.width, upright.height)
        val scaled = if (longest > maxSide) {
            val f = maxSide.toFloat() / longest
            upright.scale((upright.width * f).roundToInt(), (upright.height * f).roundToInt())
        } else {
            upright
        }
        val bytes = ByteArrayOutputStream().use { stream ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            stream.toByteArray()
        }
        if (scaled !== upright) scaled.recycle()
        if (upright !== frame.bitmap) upright.recycle()
        return bytes
    }

    /** Draws the raw frame through [uprightToOutput] (upright coordinates to output pixels). */
    private fun render(frame: RawFrame, uprightToOutput: Matrix, size: Int): IntArray {
        val matrix = Matrix(uprightToOutput).apply { preConcat(frame.toUpright) }
        val out = createBitmap(size, size)
        Canvas(out).drawBitmap(frame.bitmap, matrix, paint)
        val pixels = IntArray(size * size)
        out.getPixels(pixels, 0, size, 0, 0, size, size)
        out.recycle()
        return pixels
    }
}

private fun Similarity.toMatrix(): Matrix = Matrix().apply {
    setValues(floatArrayOf(a, -b, tx, b, a, ty, 0f, 0f, 1f))
}
