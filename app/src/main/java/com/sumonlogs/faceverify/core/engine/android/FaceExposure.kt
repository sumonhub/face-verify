package com.sumonlogs.faceverify.core.engine.android

import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import com.sumonlogs.faceverify.core.engine.ExposureSteering
import com.sumonlogs.faceverify.core.engine.FaceConfig
import com.sumonlogs.faceverify.core.engine.FaceMetering
import com.sumonlogs.faceverify.core.engine.FaceObservation
import com.sumonlogs.faceverify.core.engine.FrameGeometry
import com.sumonlogs.faceverify.core.engine.Point
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Keeps the face well exposed, not the whole frame, so a normal room doesn't leave it too dark to
 * enroll or check. Two layers: auto-exposure is pointed at the face where the camera supports it
 * ([FaceMetering]), and exposure compensation is steered from the face's measured brightness
 * ([ExposureSteering]), which works even on front cameras that ignore metering points.
 * [onFaces] runs on the analysis thread; [attach] and [detach] on the main thread.
 */
class FaceExposure(private val config: FaceConfig) {

    @Volatile
    private var target: Bound? = null

    private inner class Bound(val camera: Camera, val analysis: ImageAnalysis) {
        val metering = FaceMetering(config)
        val steering = ExposureSteering(config)
        var index = 0
    }

    fun attach(camera: Camera, analysis: ImageAnalysis) {
        val bound = Bound(camera, analysis)
        if (camera.cameraInfo.exposureState.isExposureCompensationSupported) {
            camera.cameraControl.setExposureCompensationIndex(0)
        }
        target = bound
    }

    fun detach() {
        target = null
    }

    fun onFaces(proxy: ImageProxy, faces: List<FaceObservation>, uprightWidth: Int, uprightHeight: Int, nowMs: Long) {
        val t = target ?: return
        try {
            aim(t, proxy, faces, uprightWidth, uprightHeight, nowMs)
            steer(t, proxy, faces, nowMs)
        } catch (e: Exception) {
            Log.w(TAG, "Face exposure failed", e)
        }
    }

    private fun aim(t: Bound, proxy: ImageProxy, faces: List<FaceObservation>, uprightWidth: Int, uprightHeight: Int, nowMs: Long) {
        val spot = t.metering.next(faces, uprightWidth, uprightHeight, nowMs) ?: return
        // Metering points are in the analysis image as the sensor delivered it, not upright, and
        // not mirrored: the front camera's mirror is only applied to the preview.
        val raw = FrameGeometry.uprightToRaw(proxy.imageInfo.rotationDegrees, proxy.width, proxy.height, Point(spot.x, spot.y))
        val factory = SurfaceOrientedMeteringPointFactory(proxy.width.toFloat(), proxy.height.toFloat(), t.analysis)
        val size = (spot.size / max(proxy.width, proxy.height)).coerceIn(MIN_SIZE, MAX_SIZE)
        val action = FocusMeteringAction.Builder(
            factory.createPoint(raw.x, raw.y, size),
            FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB,
        ).disableAutoCancel().build()
        // Cameras without metering regions fail the future; exposure compensation still steers then.
        t.camera.cameraControl.startFocusAndMetering(action)
    }

    private fun steer(t: Bound, proxy: ImageProxy, faces: List<FaceObservation>, nowMs: Long) {
        val state = t.camera.cameraInfo.exposureState
        if (!state.isExposureCompensationSupported) return
        val face = faces.maxByOrNull { it.width } ?: return
        val luma = faceLuma(proxy, face) ?: return
        val ev = t.steering.step(luma, nowMs)
        if (ev == 0f) return
        val evPerIndex = state.exposureCompensationStep.toFloat()
        if (evPerIndex <= 0f) return
        val delta = (ev / evPerIndex).roundToInt().let { if (it == 0) (if (ev > 0) 1 else -1) else it }
        val range = state.exposureCompensationRange
        // Only ever brighten: 0 is the camera's own exposure, and darkening below it is what made a
        // well lit face swing into "too dark". Lowering only takes back an earlier boost.
        val maxBoost = min(range.upper, (config.maxExposureBoostEv / evPerIndex).roundToInt())
        if (maxBoost <= 0) return
        val next = (t.index + delta).coerceIn(0, maxBoost)
        if (next == t.index) return
        t.index = next
        t.camera.cameraControl.setExposureCompensationIndex(next)
    }

    /**
     * Mean luma of the middle of the face, read straight from the Y plane (no colour conversion),
     * sampling a coarse grid. The same central region the crop quality check looks at.
     */
    private fun faceLuma(proxy: ImageProxy, face: FaceObservation): Float? {
        if (proxy.format != ImageFormat.YUV_420_888) return null
        val rotation = proxy.imageInfo.rotationDegrees
        val halfW = face.width * 0.3f
        val halfH = (face.bottom - face.top) * 0.3f
        val a = FrameGeometry.uprightToRaw(rotation, proxy.width, proxy.height, Point(face.centerX - halfW, face.centerY - halfH))
        val b = FrameGeometry.uprightToRaw(rotation, proxy.width, proxy.height, Point(face.centerX + halfW, face.centerY + halfH))
        val left = min(a.x, b.x).toInt().coerceIn(0, proxy.width - 1)
        val right = max(a.x, b.x).toInt().coerceIn(0, proxy.width - 1)
        val top = min(a.y, b.y).toInt().coerceIn(0, proxy.height - 1)
        val bottom = max(a.y, b.y).toInt().coerceIn(0, proxy.height - 1)
        if (right <= left || bottom <= top) return null
        val plane = proxy.planes[0]
        val buffer = plane.buffer
        val stepX = max(1, (right - left) / GRID)
        val stepY = max(1, (bottom - top) / GRID)
        var sum = 0L
        var count = 0
        var y = top
        while (y <= bottom) {
            var x = left
            while (x <= right) {
                sum += buffer.get(y * plane.rowStride + x * plane.pixelStride).toInt() and 0xFF
                count++
                x += stepX
            }
            y += stepY
        }
        return if (count == 0) null else sum.toFloat() / count
    }

    private companion object {
        const val TAG = "FaceExposure"
        const val MIN_SIZE = 0.05f
        const val MAX_SIZE = 0.5f

        /** Samples per side when measuring face brightness. */
        const val GRID = 24
    }
}
