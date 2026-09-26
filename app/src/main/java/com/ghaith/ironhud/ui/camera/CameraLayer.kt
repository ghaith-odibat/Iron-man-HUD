package com.ghaith.ironhud.ui.camera

import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ghaith.ironhud.ui.theme.Hud
import com.ghaith.ironhud.vision.TrackedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

/**
 * Back-camera preview with ML Kit object tracking running on every analysis frame.
 * Detections arrive already mapped to PreviewView pixels (COORDINATE_SYSTEM_VIEW_REFERENCED),
 * so the HUD can draw them directly in any orientation.
 */
@Composable
fun CameraLayer(
    tint: Boolean,
    onPreviewView: (PreviewView?) -> Unit,
    onObjects: (List<TrackedObject>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnObjects = rememberUpdatedState(onObjects)

    val detector = remember {
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build()
        )
    }
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            val main = ContextCompat.getMainExecutor(context)
            setImageAnalysisAnalyzer(
                main,
                MlKitAnalyzer(listOf(detector), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, main) { result ->
                    val objects = result.getValue(detector).orEmpty().map(TrackedObject::from)
                    latestOnObjects.value(objects)
                },
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose {
            cameraController.unbind()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            onPreviewView(null)
            detector.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                // TextureView-backed so the hologram tint (a layer colour filter) and bitmap grabs work.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                controller = cameraController
                onPreviewView(this)
            }
        },
        update = { view -> applyTint(view, tint) },
    )
}

private fun applyTint(view: View, tint: Boolean) {
    if (tint) {
        view.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply {
            colorFilter = ColorMatrixColorFilter(Hud.TINT_MATRIX)
        })
    } else {
        view.setLayerType(View.LAYER_TYPE_NONE, null)
    }
}
