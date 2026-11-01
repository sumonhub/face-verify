package com.sumonlogs.faceverify.core.engine.android

import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.sumonlogs.faceverify.core.api.TimingSink
import com.sumonlogs.faceverify.core.engine.FaceConfig
import com.sumonlogs.faceverify.core.engine.FaceFrame
import com.sumonlogs.faceverify.core.engine.FaceObservation
import com.sumonlogs.faceverify.core.engine.SpoofCrops

/**
 * CameraX analyzer: detects faces, then hands a [FaceFrame] to [onFrame] on the analysis thread.
 * The frame's pixels are only valid during that call; the image is closed right after it. Crops are
 * copied into plain arrays, so a session can keep them and work on them after the call returns.
 */
class FaceCameraAnalyzer(
    private val detector: MlKitFaceDetector,
    private val cropper: FaceCropper,
    private val config: FaceConfig,
    private val onFrame: (FaceFrame) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val onTiming: TimingSink = { _, _ -> },
) : ImageAnalysis.Analyzer {

    override fun analyze(proxy: ImageProxy) {
        var frame: ProxyFaceFrame? = null
        try {
            val t0 = SystemClock.elapsedRealtime()
            val faces = detector.detect(proxy)
            val detectMs = SystemClock.elapsedRealtime() - t0
            onTiming("detect", detectMs.toFloat())
            frame = ProxyFaceFrame(proxy, faces, detectMs, t0).also(onFrame)
        } catch (e: Exception) {
            Log.w(TAG, "Frame analysis failed", e)
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

        private var raw: FaceCropper.RawFrame? = null

        private fun rawFrame(): FaceCropper.RawFrame =
            raw ?: timed("convert") { cropper.raw(proxy) }.also { raw = it }

        override fun alignedFace(face: FaceObservation): IntArray =
            timed("align") { cropper.align(rawFrame(), face, ALIGN_SIZE) }

        override fun spoofCrops(face: FaceObservation): SpoofCrops = timed("spoofCrop") {
            val frame = rawFrame()
            SpoofCrops(
                near = cropper.spoofCrop(frame, face, LiteRtSpoofDetector.NEAR_SCALE, SPOOF_SIZE),
                far = cropper.spoofCrop(frame, face, LiteRtSpoofDetector.FAR_SCALE, SPOOF_SIZE),
            )
        }

        override fun selfieJpeg(): ByteArray =
            timed("selfie") { cropper.jpeg(rawFrame(), config.selfieMaxSide, config.selfieJpegQuality) }

        fun release() {
            raw?.recycle()
            raw = null
        }

        private inline fun <T> timed(stage: String, block: () -> T): T {
            val t0 = SystemClock.elapsedRealtimeNanos()
            return block().also { onTiming(stage, (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000f) }
        }
    }

    private companion object {
        const val TAG = "FaceAnalyzer"

        /** Both face models take 112×112 crops. */
        const val ALIGN_SIZE = 112

        /** The anti-spoof models take 80×80 crops. */
        const val SPOOF_SIZE = LiteRtSpoofDetector.INPUT_SIZE
    }
}
