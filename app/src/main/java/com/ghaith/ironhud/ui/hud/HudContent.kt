package com.ghaith.ironhud.ui.hud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.ghaith.ironhud.HudState
import com.ghaith.ironhud.PanelUi
import com.ghaith.ironhud.data.HudSettings
import com.ghaith.ironhud.ui.plane.PlaneLayer
import com.ghaith.ironhud.ui.plane.PlanePanel
import com.ghaith.ironhud.ui.plane.PlaneScene
import com.ghaith.ironhud.ui.plane.RadarScope
import com.ghaith.ironhud.ui.theme.Hud

/** Everything drawn over the camera. Stateless, so it can be rendered in tests without a camera. */
@Composable
fun HudContent(
    state: HudState,
    settings: HudSettings,
    attitude: Attitude,
    timeMs: Long,
    battery: Int?,
    onClose: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    /** Non-null in Plane Mode: aircraft overlays replace object scanning. */
    plane: PlaneScene? = null,
    onClosePlane: () -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val panelWidth = if (landscape) min(maxWidth * 0.40f, 470.dp) else maxWidth - 32.dp
        val density = LocalDensity.current
        // Where the leader line meets the card (landscape only; in portrait the card sits below).
        val anchor = if (state.panel == null || !landscape) null else with(density) {
            Offset((20.dp + panelWidth).toPx(), (maxHeight * 0.42f).toPx())
        }

        val lastPanel = remember { mutableStateOf<PanelUi?>(null) }
        SideEffect { if (state.panel != null) lastPanel.value = state.panel }

        HudOverlay(
            attitude = attitude,
            timeMs = timeMs,
            battery = battery,
            uplink = state.uplink.label,
            tracking = state.targets.size,
            autoLock = settings.autoLock,
            dwellProgress = state.dwellProgress,
            analyzing = state.panel?.streaming == true || state.planePanel?.streaming == true,
            animate = animate,
            showStatus = plane == null,
        )

        if (plane != null) {
            PlaneLayer(plane, Modifier.fillMaxSize(), animate = animate)
            RadarScope(
                plane,
                viewW = with(density) { maxWidth.roundToPx() },
                viewH = with(density) { maxHeight.roundToPx() },
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 26.dp, bottom = 22.dp),
                diameter = if (landscape) 190.dp else 170.dp,
                animate = animate,
            )
            AirspaceStatus(plane, Modifier.align(Alignment.TopCenter).padding(top = 96.dp))
            val selected = plane.selected
            AnimatedVisibility(
                visible = selected != null,
                modifier = if (landscape) {
                    Modifier.align(Alignment.CenterStart).padding(start = 20.dp, top = 40.dp, bottom = 40.dp)
                } else {
                    Modifier.align(Alignment.BottomEnd).padding(bottom = 230.dp, start = 16.dp, end = 16.dp)
                },
                enter = if (landscape) slideInHorizontally { -it } + fadeIn() else slideInVertically { it } + fadeIn(),
                exit = if (landscape) slideOutHorizontally { -it } + fadeOut() else slideOutVertically { it } + fadeOut(),
            ) {
                if (selected != null) {
                    PlanePanel(
                        aircraft = selected,
                        viewer = plane.airspace.viewer,
                        panel = plane.panel,
                        onClose = onClosePlane,
                        modifier = Modifier
                            .width(panelWidth)
                            .heightIn(max = if (landscape) maxHeight * 0.78f else maxHeight * 0.5f),
                        animate = animate,
                    )
                }
            }
        } else {
        TargetBrackets(
            targets = state.targets,
            dwellId = state.dwellId,
            dwellProgress = state.dwellProgress,
            lock = state.lock,
            lockLabel = state.panel?.brief?.name?.takeIf { it.isNotBlank() },
            panelAnchor = anchor,
            modifier = Modifier.fillMaxSize(),
            animate = animate,
        )

        AnimatedVisibility(
            visible = state.panel != null,
            modifier = if (landscape) {
                Modifier.align(Alignment.CenterStart).padding(start = 20.dp, top = 40.dp, bottom = 40.dp)
            } else {
                // Sit above the bottom-left status read-out.
                Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
            },
            enter = if (landscape) slideInHorizontally { -it } + fadeIn() else slideInVertically { it } + fadeIn(),
            exit = if (landscape) slideOutHorizontally { -it } + fadeOut() else slideOutVertically { it } + fadeOut(),
        ) {
            // Keep showing the last card while it animates out.
            (state.panel ?: lastPanel.value)?.let { panel ->
                InfoPanel(
                    panel = panel,
                    onClose = onClose,
                    onRescan = onRescan,
                    modifier = Modifier.width(panelWidth).heightIn(max = if (landscape) maxHeight * 0.72f else maxHeight * 0.46f),
                    animate = animate,
                )
            }
        }
        }

        state.toast?.let {
            BasicText(
                it,
                style = Hud.text(12.sp, weight = FontWeight.Bold, spacing = 2.sp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 92.dp)
                    .background(Hud.Black.copy(alpha = 0.6f))
                    .border(1.dp, Hud.blue(0.8f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/** Right-edge button column: SCAN / PLANE / VOICE / TINT / AUTO / LIGHT / SNAP / VAULT. */
@Composable
fun ControlRail(
    settings: HudSettings,
    torch: Boolean,
    planeMode: Boolean,
    onPlane: () -> Unit,
    onScan: () -> Unit,
    onVoice: () -> Unit,
    onTint: () -> Unit,
    onAuto: () -> Unit,
    onSnap: () -> Unit,
    onLight: () -> Unit,
    onVault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(end = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.End,
    ) {
        HudButton("SCAN", active = true, emphasis = true, onClick = onScan)
        HudButton("PLANE", active = planeMode, onClick = onPlane)
        HudButton("VOICE", active = settings.voice, onClick = onVoice)
        HudButton("TINT", active = settings.tint, onClick = onTint)
        HudButton("AUTO", active = settings.autoLock && !planeMode, onClick = onAuto)
        HudButton("LIGHT", active = torch, onClick = onLight)
        HudButton("SNAP", active = false, onClick = onSnap)
        HudButton("VAULT", active = false, onClick = onVault)
    }
}

/** Bottom-centre "−  2.4×  +" zoom strip with a bar showing where in the camera's range we are. Tap the readout for 1×. */
@Composable
fun ZoomControl(
    zoom: Float,
    min: Float,
    max: Float,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canZoom = max > min
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HudButton("−", active = canZoom && zoom > min + 0.01f, onClick = onZoomOut, modifier = Modifier.width(56.dp))
        Column(
            Modifier
                .width(110.dp)
                .background(Hud.Black.copy(alpha = 0.35f))
                .border(1.dp, Hud.blue(0.5f))
                .clickable(onClick = onReset)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                "ZOOM " + String.format(java.util.Locale.US, "%.1f×", zoom),
                style = Hud.text(13.sp, weight = FontWeight.Bold),
            )
            val fraction = if (canZoom) ((zoom - min) / (max - min)).coerceIn(0f, 1f) else 0f
            Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(3.dp).background(Hud.blue(0.25f))) {
                Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(Hud.Blue))
            }
        }
        HudButton("+", active = canZoom && zoom < max - 0.01f, onClick = onZoomIn, modifier = Modifier.width(56.dp))
    }
}

@Composable
fun HudButton(label: String, active: Boolean, onClick: () -> Unit, emphasis: Boolean = false, modifier: Modifier = Modifier) {
    val shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
    Box(
        modifier
            .size(width = 74.dp, height = if (emphasis) 54.dp else 42.dp)
            .background(if (emphasis) Hud.blue(0.28f) else if (active) Hud.blue(0.16f) else Hud.Black.copy(alpha = 0.35f), shape)
            .border(if (emphasis) 2.dp else 1.dp, Hud.blue(if (active) 0.95f else 0.45f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = Hud.text(if (emphasis) 14.sp else 11.sp, alpha = if (active) 1f else 0.55f, weight = FontWeight.Bold, spacing = 1.5.sp),
        )
    }
}

/** "AIRSPACE 14 CONTACTS · ADSB.LOL · 3S AGO · GPS ±8 M · HDG ±12°", plus a compass-calibration hint. */
@Composable
private fun AirspaceStatus(plane: PlaneScene, modifier: Modifier = Modifier) {
    val a = plane.airspace
    val age = if (a.lastUpdateMs == 0L) null else ((plane.clock() - a.lastUpdateMs) / 1000).coerceAtLeast(0)
    val parts = listOfNotNull(
        if (a.status == "LIVE") "AIRSPACE ${a.aircraft.size} CONTACTS" else a.status,
        a.source?.display,
        age?.let { "${it}S AGO" },
        a.viewerAccuracyM?.let { "GPS ±${it.toInt()} M" },
        plane.sensors.headingAccuracyDeg?.let { "HDG ±${it.toInt()}°" },
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(parts.joinToString("  ·  "), style = Hud.text(11.sp, alpha = 0.9f))
        val acc = plane.sensors.headingAccuracyDeg
        if (acc != null && acc > 20f) {
            BasicText("CALIBRATE COMPASS — WAVE THE TABLET IN A FIGURE 8", style = Hud.text(10.sp, alpha = 0.75f))
        }
    }
}
