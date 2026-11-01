package com.sumonlogs.faceverify.core.ui.camera

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sumonlogs.faceverify.core.api.FaceDiagnostics
import com.sumonlogs.faceverify.core.engine.FaceConfig
import com.sumonlogs.faceverify.core.engine.FaceFrame
import com.sumonlogs.faceverify.core.engine.android.FaceCameraAnalyzer
import com.sumonlogs.faceverify.core.engine.android.FaceCropper
import com.sumonlogs.faceverify.core.engine.android.MlKitFaceDetector
import org.koin.compose.koinInject
import java.util.concurrent.Executors

/**
 * Front camera preview (mirrored, TextureView so it clips to the oval) plus face analysis.
 * [onFrame] runs on the analysis thread; [onError] is called if the camera can't start.
 */
@Composable
fun FaceCameraPreview(
    onFrame: (FaceFrame) -> Unit,
    onError: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
    detector: MlKitFaceDetector = koinInject(),
    cropper: FaceCropper = koinInject(),
    config: FaceConfig = koinInject(),
    diagnostics: FaceDiagnostics = koinInject(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentOnError by rememberUpdatedState(onError)
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val resolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
            )
            .build()
        val previewResolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(config.previewWidth, config.previewHeight),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                ),
            )
            .build()
        val preview = Preview.Builder().setResolutionSelector(previewResolution).build()
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(
            executor,
            FaceCameraAnalyzer(detector, cropper, config, { currentOnFrame(it) }, { currentOnError(it) }, diagnostics::recordTiming),
        )
        var provider: ProcessCameraProvider? = null
        var disposed = false

        providerFuture.addListener({
            if (disposed) return@addListener
            try {
                val cameraProvider = providerFuture.get()
                if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    currentOnError(IllegalStateException("No front camera"))
                    return@addListener
                }
                preview.surfaceProvider = previewView.surfaceProvider
                cameraProvider.unbind(preview, analysis)
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                provider = cameraProvider
            } catch (e: Exception) {
                currentOnError(e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            provider?.unbind(preview, analysis)
            analysis.clearAnalyzer()
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
