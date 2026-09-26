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
import androidx.compose.foundation.layout.fillMaxSize
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
            analyzing = state.panel?.streaming == true,
            animate = animate,
        )
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

/** Right-edge button column: SCAN / VOICE / TINT / AUTO / LIGHT / SNAP / VAULT. */
@Composable
fun ControlRail(
    settings: HudSettings,
    torch: Boolean,
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
        HudButton("VOICE", active = settings.voice, onClick = onVoice)
        HudButton("TINT", active = settings.tint, onClick = onTint)
        HudButton("AUTO", active = settings.autoLock, onClick = onAuto)
        HudButton("LIGHT", active = torch, onClick = onLight)
        HudButton("SNAP", active = false, onClick = onSnap)
        HudButton("VAULT", active = false, onClick = onVault)
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
