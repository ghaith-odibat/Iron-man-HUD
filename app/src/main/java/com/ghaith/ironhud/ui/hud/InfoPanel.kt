package com.ghaith.ironhud.ui.hud

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaith.ironhud.PanelUi
import com.ghaith.ironhud.ui.theme.Hud
import java.util.Locale

/** The hologram info card: name, category, brief, key facts and a wireframe of the target. */
@Composable
fun InfoPanel(
    panel: PanelUi,
    onClose: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val blink = if (animate) {
        rememberInfiniteTransition(label = "panel")
            .animateFloat(0.2f, 1f, infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "blink").value
    } else 1f
    val shape = CutCornerShape(topEnd = 22.dp, bottomStart = 22.dp)
    val brief = panel.brief

    Column(
        modifier
            .pointerInput(Unit) { detectTapGestures { } } // taps on the card never re-target the camera
            .background(Hud.Black.copy(alpha = 0.62f), shape)
            .background(Hud.blue(0.07f), shape)
            .border(1.dp, Hud.blue(0.75f), shape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(8.dp).alpha(if (panel.streaming) blink else 1f).background(Hud.Blue)
            )
            BasicText(
                panel.status.uppercase(Locale.US),
                style = Hud.text(10.sp, alpha = 0.85f, spacing = 1.5.sp),
                modifier = Modifier.padding(start = 8.dp).weight(1f),
                maxLines = 1,
            )
            BasicText(
                "RESCAN",
                style = Hud.text(10.sp, weight = FontWeight.Bold),
                modifier = Modifier.clickable(onClick = onRescan).padding(horizontal = 8.dp, vertical = 4.dp),
            )
            BasicText(
                "✕",
                style = Hud.text(16.sp, weight = FontWeight.Bold),
                modifier = Modifier.clickable(onClick = onClose).padding(start = 6.dp, end = 2.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        BasicText(
            text = brief.name.ifBlank { panel.prelim?.substringBefore(" ·")?.let { "$it?" } ?: "ACQUIRING…" },
            style = Hud.text(22.sp, weight = FontWeight.Bold, spacing = 1.sp),
        )
        val meta = listOfNotNull(
            brief.type.takeIf { it.isNotBlank() }?.uppercase(Locale.US),
            brief.confidence.takeIf { it.isNotBlank() }?.let { "CONFIDENCE ${it.uppercase(Locale.US)}" },
        ).joinToString("  ·  ")
        if (meta.isNotBlank()) BasicText(meta, style = Hud.text(11.sp, alpha = 0.75f, spacing = 1.5.sp))

        Canvas(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(2.dp)) {
            drawLine(Hud.blue(0.8f), Offset.Zero, Offset(size.width * 0.35f, 0f), size.height)
            drawLine(Hud.blue(0.25f), Offset(size.width * 0.37f, 0f), Offset(size.width, 0f), 1f)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                val cursor = if (panel.streaming && brief.isUsable) " ▌" else ""
                if (brief.summary.isNotBlank() || cursor.isNotEmpty()) {
                    BasicText(brief.summary + cursor, style = Hud.text(14.sp, alpha = 0.95f, glow = false).copy(lineHeight = 19.sp))
                }
                if (brief.facts.isNotEmpty()) Spacer(Modifier.height(8.dp))
                for (fact in brief.facts) {
                    Row(Modifier.padding(vertical = 2.dp)) {
                        BasicText("▸ ", style = Hud.text(13.sp))
                        BasicText(fact, style = Hud.text(13.sp, alpha = 0.9f, glow = false).copy(lineHeight = 17.sp))
                    }
                }
                panel.prelim?.let {
                    Spacer(Modifier.height(8.dp))
                    BasicText("ON-DEVICE SENSOR: $it", style = Hud.text(10.sp, alpha = 0.6f, glow = false))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(118.dp).border(1.dp, Hud.blue(0.5f)).background(Hud.blue(0.05f)),
                    contentAlignment = Alignment.Center,
                ) {
                    panel.wireframe?.let {
                        Image(it, contentDescription = "Wireframe", contentScale = ContentScale.Fit, modifier = Modifier.size(112.dp))
                    } ?: BasicText("NO SCAN", style = Hud.text(10.sp, alpha = 0.4f))
                }
                BasicText("HOLO-SCAN", style = Hud.text(9.sp, alpha = 0.6f, spacing = 2.sp), modifier = Modifier.padding(top = 4.dp))
            }
        }

        Spacer(Modifier.height(10.dp))
        val footer = panel.error ?: panel.source?.let { (if (panel.fromCache) "CACHED · " else "") + it }
        if (footer != null) {
            BasicText(
                footer.uppercase(Locale.US),
                style = Hud.text(9.sp, alpha = if (panel.error != null) blink else 0.6f, glow = false, spacing = 1.sp),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
            )
        }
    }
}
