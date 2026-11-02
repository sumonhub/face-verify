package com.sumonlogs.faceverify.core.engine.android

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import android.graphics.Color
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import com.sumonlogs.faceverify.core.engine.FaceObservation
import com.sumonlogs.faceverify.core.engine.Point
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
            // Landmarks steer the alignment, and a few pixels of error there move the embedding more than
            // anything else in the pipeline, so take the slower, more precise mode.
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
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

    /** Loads the model by running it once on a blank image. Blocks; call off the main thread. */
    fun warmUp() {
        val blank = createBitmap(WARM_UP_SIZE, WARM_UP_SIZE).apply { eraseColor(Color.GRAY) }
        try {
            Tasks.await(client.process(InputImage.fromBitmap(blank, 0)))
        } finally {
            blank.recycle()
        }
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
            noseBase = getLandmark(FaceLandmark.NOSE_BASE)?.position?.let { Point(it.x, it.y) },
            mouthLeft = getLandmark(FaceLandmark.MOUTH_LEFT)?.position?.let { Point(it.x, it.y) },
            mouthRight = getLandmark(FaceLandmark.MOUTH_RIGHT)?.position?.let { Point(it.x, it.y) },
            trackingId = trackingId,
        )
    }

    private companion object {
        const val WARM_UP_SIZE = 256
    }
}
