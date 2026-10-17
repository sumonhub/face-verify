package com.banglatrac.workopolo.face.core.engine.android

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.banglatrac.workopolo.face.core.engine.FaceObservation
import com.banglatrac.workopolo.face.core.engine.Point
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark

/** ML Kit face detection (bundled model) mapped to plain [FaceObservation]s in upright coordinates. */
class MlKitFaceDetector {

    private val client = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.1f)
            .enableTracking()
            .build(),
    )

    /** Blocks the calling (analysis) thread until detection finishes. Never call on the main thread. */
    @OptIn(ExperimentalGetImage::class)
    fun detect(proxy: ImageProxy): List<FaceObservation> {
        val media = proxy.image ?: return emptyList()
        val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        return Tasks.await(client.process(input)).map { it.toObservation() }
    }

    private fun Face.toObservation(): FaceObservation {
        val box = boundingBox
        return FaceObservation(
            left = box.left.toFloat(),
            top = box.top.toFloat(),
            right = box.right.toFloat(),
            bottom = box.bottom.toFloat(),
            yaw = headEulerAngleY,
            pitch = headEulerAngleX,
            roll = headEulerAngleZ,
            leftEyeOpen = leftEyeOpenProbability,
            rightEyeOpen = rightEyeOpenProbability,
            leftEye = getLandmark(FaceLandmark.LEFT_EYE)?.position?.let { Point(it.x, it.y) },
            rightEye = getLandmark(FaceLandmark.RIGHT_EYE)?.position?.let { Point(it.x, it.y) },
            trackingId = trackingId,
        )
    }
}
