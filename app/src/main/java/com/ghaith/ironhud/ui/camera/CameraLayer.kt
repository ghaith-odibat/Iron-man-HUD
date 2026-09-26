package com.ghaith.ironhud.ui.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Range
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ghaith.ironhud.FocusRequest
import com.ghaith.ironhud.nightvision.NightVision
import com.ghaith.ironhud.plane.CameraOptics
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
    /** Night vision: longest exposures and maximum exposure compensation, plus the intensifier look. */
    nightVision: Boolean,
    /** Night-vision gain slider position (0..1, see [NightVision.gain]). */
    nightGain: Float,
    /** What the camera allowed for night vision: exposure compensation (EV) and the lowest frame rate. */
    onNightCamera: (ev: Float?, minFps: Int?) -> Unit,
    onPreviewView: (PreviewView?) -> Unit,
    onObjects: (List<TrackedObject>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnObjects = rememberUpdatedState(onObjects)
    val previewRef = remember { mutableStateOf<PreviewView?>(null) }
    var minFocusDiopters by remember { mutableStateOf<Float?>(null) }
    var fpsRanges by remember { mutableStateOf<List<Range<Int>>>(emptyList()) }
    var cameraReady by remember { mutableStateOf(false) }
    val nightShader = remember { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) NightShader.create() else null }

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
            fpsRanges = readFpsRanges(info)
            cameraReady = true
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

    // Camera2 request options: manual focus (AF off + LENS_FOCUS_DISTANCE) and, in night vision, the
    // AE frame-rate range that allows the longest exposures. One builder so neither wipes the other.
    LaunchedEffect(manualFocus, minFocusDiopters, nightVision, fpsRanges) {
        val control = cameraController.cameraControl ?: return@LaunchedEffect
        runCatching { applyCaptureOptions(control, manualFocus, minFocusDiopters, if (nightVision) nightRange(fpsRanges) else null) }
    }

    // Night vision pushes exposure compensation to the camera's maximum, and back to 0 after.
    LaunchedEffect(nightVision, cameraReady) {
        val info = cameraController.cameraInfo ?: return@LaunchedEffect
        val control = cameraController.cameraControl ?: return@LaunchedEffect
        val exposure = info.exposureState
        val supported = exposure.isExposureCompensationSupported
        val index = if (nightVision && supported) exposure.exposureCompensationRange.upper else 0
        if (supported) runCatching { control.setExposureCompensationIndex(index) }
        onNightCamera(
            if (nightVision && supported) index * exposure.exposureCompensationStep.toFloat() else null,
            if (nightVision) nightRange(fpsRanges)?.lower else null,
        )
    }

    // Live intensifier grain (Android 13+): re-set the shader effect about 30 times a second.
    LaunchedEffect(nightVision, nightGain, nightShader) {
        if (!nightVision || nightShader == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val gain = NightVision.gain(nightGain)
        var last = 0L
        while (true) withFrameNanos { t ->
            val view = previewRef.value
            if (view != null && t - last >= GRAIN_FRAME_NS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                last = t
                nightShader.apply(view, gain, t / 1e9)
            }
        }
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
        update = { view -> NightVisionLook.apply(view, tint, nightVision, NightVision.gain(nightGain), shaderActive = nightShader != null) },
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
private fun readFpsRanges(info: CameraInfo): List<Range<Int>> = runCatching {
    Camera2CameraInfo.from(info).getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.toList()
}.getOrNull().orEmpty()

private fun nightRange(ranges: List<Range<Int>>): Range<Int>? =
    NightVision.pickFpsRange(ranges.map { it.lower to it.upper })?.let { (lo, hi) -> Range(lo, hi) }

@OptIn(ExperimentalCamera2Interop::class)
private fun applyCaptureOptions(control: CameraControl, focus: Float?, minDiopters: Float?, nightFps: Range<Int>?) {
    val c2 = Camera2CameraControl.from(control)
    val manual = focus != null && minDiopters != null && minDiopters > 0f
    if (!manual && nightFps == null) {
        c2.clearCaptureRequestOptions()
        return
    }
    val options = CaptureRequestOptions.Builder()
    if (focus != null && minDiopters != null && minDiopters > 0f) {
        control.cancelFocusAndMetering()
        // Square curve: finer steps at the far end, where most things are.
        val diopters = minDiopters * (1 - focus) * (1 - focus)
        options.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        options.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, diopters)
    }
    if (nightFps != null) options.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, nightFps)
    c2.setCaptureRequestOptions(options.build())
}

private const val GRAIN_FRAME_NS = 33_000_000L
