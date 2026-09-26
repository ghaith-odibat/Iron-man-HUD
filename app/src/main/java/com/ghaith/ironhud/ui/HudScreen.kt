package com.ghaith.ironhud.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghaith.ironhud.FocusStatus
import com.ghaith.ironhud.HudViewModel
import com.ghaith.ironhud.capture.HudCapture
import com.ghaith.ironhud.ui.camera.CameraLayer
import com.ghaith.ironhud.ui.hud.ControlRail
import com.ghaith.ironhud.ui.hud.HudContent
import com.ghaith.ironhud.ui.hud.HudSliderStrip
import com.ghaith.ironhud.ui.hud.ZoomControl
import com.ghaith.ironhud.ui.hud.rememberBatteryPercent
import com.ghaith.ironhud.ui.hud.rememberClock
import com.ghaith.ironhud.ui.hud.rememberSensorHub
import com.ghaith.ironhud.ui.plane.HangarScreen
import com.ghaith.ironhud.ui.plane.LocationPickerScreen
import com.ghaith.ironhud.ui.plane.PlaneScene
import com.ghaith.ironhud.ui.theme.Hud
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HudScreen(vm: HudViewModel, onOpenVault: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sensors = rememberSensorHub()
    val attitude by sensors.attitude
    val airspace by vm.airspace.collectAsStateWithLifecycle()
    // Once we know where we are, the compass reads true north (and matches the plane overlays).
    LaunchedEffect(airspace.declinationDeg) { sensors.declinationDeg = airspace.declinationDeg }
    val clock by rememberClock()
    val battery by rememberBatteryPercent()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val overlayLayer = rememberGraphicsLayer()
    val previewView = remember { mutableStateOf<PreviewView?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.onForeground(true)
                Lifecycle.Event.ON_STOP -> vm.onForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.onForeground(false)
        }
    }

    fun capture() {
        scope.launch {
            val overlay = overlayLayer.toImageBitmap().asAndroidBitmap()
            val camera = previewView.value?.bitmap
            val saved = withContext(Dispatchers.IO) {
                HudCapture.save(context, HudCapture.compose(camera, overlay, settings.tint))
            }
            vm.toast(if (saved) "CAPTURE SAVED · PICTURES/IRONHUD" else "CAPTURE FAILED")
        }
    }

    // Android 8/9 need storage permission to write to Pictures; newer versions don't.
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) capture() else vm.toast("STORAGE PERMISSION DENIED")
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) vm.setPlaneMode(true) else vm.toast("LOCATION NEEDED FOR PLANE MODE")
    }
    val togglePlane = {
        when {
            state.planeMode -> vm.setPlaneMode(false)
            // Watching a chosen place doesn't need the tablet's location.
            vm.hasLocationPermission() || settings.planeObserver != null -> vm.setPlaneMode(true)
            else -> locationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // "Use my current location" in the observer picker.
    val myLocationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) vm.useMyLocation() else vm.toast("LOCATION NEEDED TO USE YOUR POSITION")
    }
    val useMyLocation = {
        if (vm.hasLocationPermission()) vm.useMyLocation()
        else myLocationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    BackHandler(enabled = state.locationPicker) { vm.closeLocationPicker() }
    BackHandler(enabled = state.hangar != null) { vm.closeHangar() }

    val snap = {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else capture()
    }

    val showFocusStrip = state.focusStrip
    val showLightStrip = state.torch && state.torchMaxLevel > 1
    val extraStrips = 58.dp * ((if (showFocusStrip) 1 else 0) + (if (showLightStrip) 1 else 0))

    Box(
        Modifier
            .fillMaxSize()
            .background(Hud.Black)
            .onSizeChanged { vm.onViewSize(it.width, it.height) },
    ) {
        CameraLayer(
            tint = settings.tint,
            torch = state.torch,
            onTorchState = vm::onTorchState,
            onTorchUnavailable = vm::onTorchUnavailable,
            zoomTarget = state.zoomTarget,
            onZoomState = vm::onZoomState,
            onOptics = vm::onOptics,
            torchLevel = state.torchLevelTarget,
            onTorchLevel = vm::onTorchLevel,
            focusRequest = state.focusRequest,
            onFocusResult = vm::onFocusResult,
            manualFocus = state.manualFocus,
            focusResetSeq = state.focusResetSeq,
            onPreviewView = { view ->
                previewView.value = view
                vm.frameSource = view?.let { v -> { v.bitmap } }
            },
            onObjects = vm::onObjects,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    // Record the HUD into a layer so SNAP can composite it over the camera frame.
                    overlayLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(overlayLayer)
                }
                // Tap identifies; long-press focuses (and locks focus) on that spot.
                .pointerInput(Unit) { detectTapGestures(onTap = vm::onTap, onLongPress = vm::focusAt) }
                // Two-finger pinch zooms the camera.
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ -> if (zoom != 1f) vm.zoomBy(zoom) }
                },
        ) {
            HudContent(
                state = state,
                settings = settings,
                attitude = attitude,
                timeMs = clock,
                battery = battery,
                onClose = vm::dismiss,
                onRescan = vm::rescan,
                plane = if (state.planeMode) {
                    PlaneScene(
                        airspace = airspace,
                        sensors = sensors,
                        optics = state.optics,
                        zoom = state.zoom,
                        selectedHex = state.selectedHex,
                        panel = state.planePanel,
                        hits = vm.planeHits,
                        skyView = settings.skyView,
                    )
                } else null,
                onClosePlane = vm::deselectPlane,
                onOpenLocation = vm::openLocationPicker,
                onToggleSky = { vm.setSkyView(!settings.skyView) },
                onOpenHangar = vm::openHangar,
                bottomInset = extraStrips,
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showFocusStrip) {
                HudSliderStrip(
                    title = "FOCUS ${vm.focusLabel(state)}",
                    fraction = state.manualFocus ?: 1f,
                    onMinus = { vm.stepFocus(farther = false) },
                    onPlus = { vm.stepFocus(farther = true) },
                    onSet = vm::setManualFocus,
                    onReset = vm::resetFocus,
                    minusLabel = "NEAR",
                    plusLabel = "FAR",
                )
            }
            if (showLightStrip) {
                val max = state.torchMaxLevel
                HudSliderStrip(
                    title = "LIGHT ${(100f * state.torchLevel / max).roundToInt()}%",
                    fraction = (state.torchLevel - 1f) / (max - 1f),
                    onMinus = { vm.stepTorch(up = false) },
                    onPlus = { vm.stepTorch(up = true) },
                    onSet = vm::setTorchFraction,
                    onReset = { vm.setTorchFraction(1f) },
                )
            }
            ZoomControl(
                zoom = state.zoom,
                min = state.zoomMin,
                max = state.zoomMax,
                onZoomOut = { vm.zoomStep(zoomIn = false) },
                onZoomIn = { vm.zoomStep(zoomIn = true) },
                onReset = vm::resetZoom,
            )
        }

        ControlRail(
            settings = settings,
            torch = state.torch,
            planeMode = state.planeMode,
            onPlane = togglePlane,
            focusActive = state.focusStrip || state.manualFocus != null || state.focusMarker?.status == FocusStatus.LOCKED,
            onFocus = vm::toggleFocusStrip,
            onScan = vm::scanCenter,
            onVoice = { vm.setVoice(!settings.voice) },
            onTint = { vm.setTint(!settings.tint) },
            onAuto = { vm.setAutoLock(!settings.autoLock) },
            onSnap = snap,
            onLight = vm::toggleTorch,
            onVault = onOpenVault,
            modifier = Modifier.align(Alignment.CenterEnd),
        )

        if (state.locationPicker) {
            LocationPickerScreen(
                current = airspace.observer,
                gps = airspace.gps,
                busy = state.pickerBusy,
                onPick = vm::chooseLocation,
                onUseMyLocation = useMyLocation,
                onClose = vm::closeLocationPicker,
            )
        }
        state.hangar?.let { type ->
            HangarScreen(startId = type, onClose = vm::closeHangar)
        }
    }
}
