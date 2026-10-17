package com.sumonlogs.faceverify.core.engine.android

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.sumonlogs.faceverify.core.engine.FaceConfig
import com.sumonlogs.faceverify.core.engine.FaceFrame
import com.sumonlogs.faceverify.core.engine.FaceObservation

/**
 * CameraX analyzer: detects faces, then hands a [FaceFrame] to [onFrame] on the analysis thread.
 * The frame's pixels are only valid during that call; the image is closed right after it.
 */
class FaceCameraAnalyzer(
    private val detector: MlKitFaceDetector,
    private val cropper: FaceCropper,
    private val config: FaceConfig,
    private val onFrame: (FaceFrame) -> Unit,
    private val onError: (Throwable) -> Unit,
) : ImageAnalysis.Analyzer {

    override fun analyze(proxy: ImageProxy) {
        var frame: ProxyFaceFrame? = null
        try {
            val t0 = SystemClock.elapsedRealtime()
            val faces = detector.detect(proxy)
            val detectMs = SystemClock.elapsedRealtime() - t0
            frame = ProxyFaceFrame(proxy, faces, detectMs, t0).also(onFrame)
        } catch (e: Exception) {
            onError(e)
        } finally {
            frame?.release()
            proxy.close()
        }
    }

    private inner class ProxyFaceFrame(
        private val proxy: ImageProxy,
        override val faces: List<FaceObservation>,
        override val detectMs: Long,
        override val timestampMs: Long,
    ) : FaceFrame {
        private val rotated = proxy.imageInfo.rotationDegrees % 180 != 0
        override val width: Int = if (rotated) proxy.height else proxy.width
        override val height: Int = if (rotated) proxy.width else proxy.height

        private var upright: Bitmap? = null

        private fun uprightBitmap(): Bitmap = upright ?: cropper.upright(proxy).also { upright = it }

        override fun alignedFace(face: FaceObservation): IntArray =
            cropper.align(uprightBitmap(), face, ALIGN_SIZE)

        override fun selfieJpeg(): ByteArray =
            cropper.jpeg(uprightBitmap(), config.selfieMaxSide, config.selfieJpegQuality)

        fun release() {
            upright?.recycle()
            upright = null
        }
    }

    private companion object {
        /** Both face models take 112×112 crops. */
        const val ALIGN_SIZE = 112
    }
}
