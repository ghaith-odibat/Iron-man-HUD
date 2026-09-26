package com.ghaith.ironhud.ui.camera

import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.view.View
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.TorchState
import androidx.camera.core.ZoomState
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ghaith.ironhud.FocusRequest
import com.ghaith.ironhud.plane.CameraOptics
import com.ghaith.ironhud.ui.theme.Hud
import com.ghaith.ironhud.vision.TrackedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.delay

/**
 * Back-camera preview with ML Kit object tracking running on every analysis frame.
 * Detections arrive already mapped to PreviewView pixels (COORDINATE_SYSTEM_VIEW_REFERENCED),
 * so the HUD can draw them directly in any orientation.
 */
@Composable
fun CameraLayer(
    tint: Boolean,
    torch: Boolean,
    onTorchState: (Boolean) -> Unit,
    onTorchUnavailable: () -> Unit,
    zoomTarget: Float?,
    onZoomState: (ratio: Float, min: Float, max: Float) -> Unit,
    onOptics: (CameraOptics) -> Unit,
    torchLevel: Int?,
    onTorchLevel: (max: Int, level: Int) -> Unit,
    focusRequest: FocusRequest?,
    onFocusResult: (id: Int, success: Boolean) -> Unit,
    manualFocus: Float?,
    focusResetSeq: Int,
    onPreviewView: (PreviewView?) -> Unit,
    onObjects: (List<TrackedObject>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnObjects = rememberUpdatedState(onObjects)
    val previewRef = remember { mutableStateOf<PreviewView?>(null) }
    var minFocusDiopters by remember { mutableStateOf<Float?>(null) }

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
        val torchObserver = Observer<Int> { onTorchState(it == TorchState.ON) }
        cameraController.torchState.observe(lifecycleOwner, torchObserver)
        val zoomObserver = Observer<ZoomState> { onZoomState(it.zoomRatio, it.minZoomRatio, it.maxZoomRatio) }
        cameraController.zoomState.observe(lifecycleOwner, zoomObserver)
        onDispose {
            cameraController.torchState.removeObserver(torchObserver)
            cameraController.zoomState.removeObserver(zoomObserver)
            cameraController.unbind()
        }
    }

    LaunchedEffect(zoomTarget) {
        if (zoomTarget != null && cameraController.cameraInfo != null) cameraController.setZoomRatio(zoomTarget)
    }

    // Lens focal length and sensor size, so Plane Mode can project aircraft with the real field of view.
    LaunchedEffect(Unit) {
        var info = cameraController.cameraInfo
        var waited = 0
        while (info == null && waited < 10_000) {
            delay(200)
            waited += 200
            info = cameraController.cameraInfo
        }
        if (info != null) {
            val optics = readOptics(info)
            minFocusDiopters = optics.minFocusDiopters
            onOptics(optics)
            // Torch brightness (CameraX 1.5; needs hardware support, typically Android 15+).
            val max = runCatching { info.maxTorchStrengthLevel }.getOrDefault(1).coerceAtLeast(1)
            onTorchLevel(max, runCatching { info.torchStrengthLevel.value }.getOrNull() ?: max)
            if (max > 1) info.torchStrengthLevel.observe(lifecycleOwner) { level -> onTorchLevel(max, level ?: max) }
        }
    }

    LaunchedEffect(torchLevel) {
        if (torch && torchLevel != null) {
            runCatching { cameraController.cameraControl?.setTorchStrengthLevel(torchLevel) }
        }
    }

    // Long-press focus: meter AF/AE on that point and keep it locked until the user resets.
    LaunchedEffect(focusRequest) {
        val req = focusRequest ?: return@LaunchedEffect
        val view = previewRef.value
        val control = cameraController.cameraControl
        if (view == null || control == null) {
            onFocusResult(req.id, false)
            return@LaunchedEffect
        }
        val point = view.meteringPointFactory.createPoint(req.x, req.y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .disableAutoCancel()
            .build()
        val future = runCatching { control.startFocusAndMetering(action) }.getOrNull()
        if (future == null) {
            onFocusResult(req.id, false)
            return@LaunchedEffect
        }
        future.addListener(
            { onFocusResult(req.id, runCatching { future.get().isFocusSuccessful }.getOrDefault(false)) },
            ContextCompat.getMainExecutor(context),
        )
    }

    LaunchedEffect(focusResetSeq) {
        if (focusResetSeq > 0) runCatching { cameraController.cameraControl?.cancelFocusAndMetering() }
    }

    // Manual focus distance via Camera2 (AF off + LENS_FOCUS_DISTANCE); null returns to autofocus.
    LaunchedEffect(manualFocus, minFocusDiopters) {
        val control = cameraController.cameraControl ?: return@LaunchedEffect
        runCatching { applyManualFocus(control, manualFocus, minFocusDiopters) }
    }

    // Flashlight: wait for the camera to be bound, then check it actually has a flash unit.
    LaunchedEffect(torch) {
        var info = cameraController.cameraInfo
        var waited = 0
        while (info == null && waited < 3_000) {
            delay(100)
            waited += 100
            info = cameraController.cameraInfo
        }
        when {
            info == null -> if (torch) onTorchUnavailable()
            !info.hasFlashUnit() -> if (torch) onTorchUnavailable()
            else -> {
                cameraController.enableTorch(torch)
                if (torch && torchLevel != null && runCatching { info.maxTorchStrengthLevel }.getOrDefault(1) > 1) {
                    runCatching { cameraController.cameraControl?.setTorchStrengthLevel(torchLevel) }
                }
            }
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
                previewRef.value = this
                onPreviewView(this)
            }
        },
        update = { view -> applyTint(view, tint) },
    )
}

@OptIn(ExperimentalCamera2Interop::class)
private fun readOptics(info: CameraInfo): CameraOptics = runCatching {
    val c2 = Camera2CameraInfo.from(info)
    val focal = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
    val size = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    val minFocus = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
    CameraOptics(focal, size?.width, size?.height, minFocus)
}.getOrDefault(CameraOptics())

@OptIn(ExperimentalCamera2Interop::class)
private fun applyManualFocus(control: CameraControl, fraction: Float?, minDiopters: Float?) {
    val c2 = Camera2CameraControl.from(control)
    if (fraction == null || minDiopters == null || minDiopters <= 0f) {
        c2.clearCaptureRequestOptions()
        return
    }
    control.cancelFocusAndMetering()
    // Square curve: finer steps at the far end, where most things are.
    val diopters = minDiopters * (1 - fraction) * (1 - fraction)
    c2.setCaptureRequestOptions(
        CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, diopters)
            .build()
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
