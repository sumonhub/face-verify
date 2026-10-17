package com.sumonlogs.faceverify.core.engine.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.sumonlogs.faceverify.core.engine.FaceObservation
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Bitmap work for a frame: upright conversion, face alignment and the evidence JPEG. */
class FaceCropper {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    fun upright(proxy: ImageProxy): Bitmap {
        val raw = proxy.toBitmap()
        val rotation = proxy.imageInfo.rotationDegrees
        if (rotation == 0) return raw
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also { raw.recycle() }
    }

    /**
     * Similarity-aligns the face so the eyes land on the ArcFace 112×112 template points, which is
     * how MobileFaceNet's training data was aligned. Falls back to a square box crop without landmarks.
     */
    fun align(upright: Bitmap, face: FaceObservation, size: Int): IntArray {
        val scale = size / TEMPLATE_SIZE
        val matrix = Matrix()
        val leftEye = face.leftEye
        val rightEye = face.rightEye
        if (leftEye != null && rightEye != null) {
            // The person's right eye appears on the image's left.
            val src = floatArrayOf(rightEye.x, rightEye.y, leftEye.x, leftEye.y)
            val dst = floatArrayOf(
                TEMPLATE_IMAGE_LEFT_EYE_X * scale, TEMPLATE_EYE_Y * scale,
                TEMPLATE_IMAGE_RIGHT_EYE_X * scale, TEMPLATE_EYE_Y * scale,
            )
            matrix.setPolyToPoly(src, 0, dst, 0, 2)
        } else {
            val side = max(face.width, face.bottom - face.top)
            val s = size / side
            matrix.setScale(s, s)
            matrix.preTranslate(-(face.centerX - side / 2f), -(face.centerY - side / 2f))
        }
        val out = createBitmap(size, size)
        Canvas(out).drawBitmap(upright, matrix, paint)
        val pixels = IntArray(size * size)
        out.getPixels(pixels, 0, size, 0, 0, size, size)
        out.recycle()
        return pixels
    }

    fun jpeg(upright: Bitmap, maxSide: Int, quality: Int): ByteArray {
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
        return bytes
    }

    private companion object {
        const val TEMPLATE_SIZE = 112f
        const val TEMPLATE_IMAGE_LEFT_EYE_X = 38.2946f
        const val TEMPLATE_IMAGE_RIGHT_EYE_X = 73.5318f
        const val TEMPLATE_EYE_Y = 51.6f
    }
}
