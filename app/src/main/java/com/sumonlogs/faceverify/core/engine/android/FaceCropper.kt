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
     * Aligns the face onto the ArcFace 112×112 template with a least-squares fit over every landmark
     * ML Kit found (eyes, nose, mouth corners), as both models were trained. Falls back to a square
     * box crop when the eyes are missing.
     */
    fun align(upright: Bitmap, face: FaceObservation, size: Int): IntArray {
        val matrix = Matrix()
        val similarity = FaceAlignment.estimate(face, size)
        if (similarity != null) {
            matrix.setValues(
                floatArrayOf(
                    similarity.a, -similarity.b, similarity.tx,
                    similarity.b, similarity.a, similarity.ty,
                    0f, 0f, 1f,
                ),
            )
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
}
