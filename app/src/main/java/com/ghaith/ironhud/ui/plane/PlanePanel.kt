package com.ghaith.ironhud.ui.plane

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaith.ironhud.PanelUi
import com.ghaith.ironhud.plane.Aircraft
import com.ghaith.ironhud.plane.Airport
import com.ghaith.ironhud.plane.FlightRoute
import com.ghaith.ironhud.plane.AircraftInfo
import com.ghaith.ironhud.plane.AircraftModels
import com.ghaith.ironhud.plane.Geo
import com.ghaith.ironhud.plane.GeoPoint
import com.ghaith.ironhud.plane.Lod
import com.ghaith.ironhud.plane.ModelKind
import com.ghaith.ironhud.plane.models.Airframe
import com.ghaith.ironhud.ui.theme.Hud
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

/** The selected plane's card: live data, a spinning 3D hologram of its type, and J.A.R.V.I.S.'s brief. */
@Composable
fun PlanePanel(
    aircraft: Aircraft,
    viewer: GeoPoint?,
    panel: PanelUi?,
    route: FlightRoute?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    /** Opens the full-screen hologram of this aircraft's type. */
    onInspect: () -> Unit = {},
) {
    val transition = rememberInfiniteTransition(label = "plane-panel")
    val spin = if (animate) {
        transition.animateFloat(0f, 360f, infiniteRepeatable(tween(10_000, easing = LinearEasing)), label = "spin").value
    } else 35f
    val blink = if (animate) {
        transition.animateFloat(0.2f, 1f, infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "blink").value
    } else 1f
    val shape = CutCornerShape(topEnd = 22.dp, bottomStart = 22.dp)
    val airframe = AircraftModels.airframeFor(aircraft)
    val kind = airframe.kind
    val enu = viewer?.let { Geo.enu(it, GeoPoint(aircraft.lat, aircraft.lon, aircraft.altM)) }

    Column(
        modifier
            .pointerInput(Unit) { detectTapGestures { } }
            .background(Hud.Black.copy(alpha = 0.62f), shape)
            .background(Hud.blue(0.07f), shape)
            .border(1.dp, Hud.blue(0.75f), shape)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).alpha(if (panel?.streaming == true) blink else 1f).background(Hud.Blue))
            BasicText(
                (panel?.status ?: "LIVE TRACK").uppercase(Locale.US),
                style = Hud.text(10.sp, alpha = 0.85f, spacing = 1.5.sp),
                modifier = Modifier.padding(start = 8.dp).weight(1f),
                maxLines = 1,
            )
            BasicText("✕", style = Hud.text(16.sp, weight = FontWeight.Bold),
                modifier = Modifier.clickable(onClick = onClose).padding(start = 6.dp, end = 2.dp))
        }
        Spacer(Modifier.height(8.dp))
        BasicText(aircraft.label, style = Hud.text(24.sp, weight = FontWeight.Bold, spacing = 2.sp))
        BasicText(
            (AircraftInfo.airline(aircraft) ?: "UNKNOWN OPERATOR").uppercase(Locale.US),
            style = Hud.text(12.sp, alpha = 0.85f, spacing = 1.5.sp),
        )

        RouteBlock(route, aircraft, Modifier.padding(top = 10.dp))

        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                BasicText(
                    listOfNotNull(AircraftInfo.typeName(aircraft), aircraft.registration).joinToString("  ·  ").ifBlank { kind.label },
                    style = Hud.text(13.sp, weight = FontWeight.Bold, glow = false),
                )
                Spacer(Modifier.height(6.dp))
                val stats = listOf(
                    "ALT" to PlaneFormat.altitude(aircraft),
                    "GS" to PlaneFormat.speed(aircraft),
                    "HDG" to PlaneFormat.heading(aircraft),
                    "V/S" to PlaneFormat.verticalRate(aircraft),
                    "DIST" to (enu?.let { PlaneFormat.distance(it.horizontal / 1000) } ?: "--"),
                    "BRG" to (enu?.let { "${Geo.bearingDeg(it).roundToInt().toString().padStart(3, '0')}°" } ?: "--"),
                    "SQUAWK" to (aircraft.squawk ?: "----"),
                    "CLASS" to (AircraftInfo.categoryName(aircraft.category) ?: kind.label),
                )
                for (row in stats.chunked(2)) {
                    Row {
                        for ((k, v) in row) {
                            Row(Modifier.weight(1f).padding(vertical = 2.dp)) {
                                BasicText("$k ", style = Hud.text(10.sp, alpha = 0.55f, glow = false))
                                BasicText(v, style = Hud.text(12.sp, glow = false), maxLines = 1)
                            }
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onInspect)) {
                Turntable(
                    airframe, spin, Modifier.size(136.dp).border(1.dp, Hud.blue(0.5f)).background(Hud.blue(0.05f)),
                    // 10 s per turn: every rotor speed is a whole number of turns per cycle, so no jump at the wrap.
                    timeS = if (animate) spin / 36.0 else 0.3,
                )
                BasicText(
                    if (airframe.generic) "GENERIC ${kind.label}" else ModelFormat.size(airframe),
                    style = Hud.text(9.sp, alpha = 0.65f, spacing = 1.sp, glow = false),
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                )
                BasicText("◈ HANGAR", style = Hud.text(10.sp, weight = FontWeight.Bold, spacing = 1.5.sp), modifier = Modifier.padding(top = 2.dp))
            }
        }

        Canvas(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(2.dp)) {
            drawLine(Hud.blue(0.8f), Offset.Zero, Offset(size.width * 0.35f, 0f), size.height)
            drawLine(Hud.blue(0.25f), Offset(size.width * 0.37f, 0f), Offset(size.width, 0f), 1f)
        }

        Column {
            if (panel != null && (panel.brief.summary.isNotBlank() || panel.streaming)) {
                val brief = panel.brief
                val cursor = if (panel.streaming) " ▌" else ""
                BasicText(brief.summary + cursor, style = Hud.text(13.sp, alpha = 0.95f, glow = false).copy(lineHeight = 18.sp))
                for (fact in brief.facts) {
                    Row(Modifier.padding(top = 3.dp)) {
                        BasicText("▸ ", style = Hud.text(12.sp))
                        BasicText(fact, style = Hud.text(12.sp, alpha = 0.9f, glow = false))
                    }
                }
            }
            val footer = listOfNotNull(
                panel?.error,
                panel?.source?.let { (if (panel?.fromCache == true) "CACHED · " else "") + it },
                aircraft.source.attribution,
            ).joinToString("  ·  ")
            BasicText(
                footer.uppercase(Locale.US),
                style = Hud.text(9.sp, alpha = if (panel?.error != null) blink else 0.6f, glow = false, spacing = 1.sp),
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                maxLines = 3,
            )
        }
    }
}

/** A slowly rotating hologram of the aircraft type, seen from 20° above, on a ring pedestal. */
@Composable
fun Turntable(airframe: Airframe, yawDeg: Float, modifier: Modifier = Modifier, timeS: Double = 0.0) {
    val painter = remember { WirePainter() }
    Canvas(modifier) {
        val el = 20.0
        val d = 3.2
        val px = size.minDimension * 0.86f
        val c = center + Offset(0f, size.height * 0.02f)
        // Pedestal ring.
        val ringW = px * 0.9f
        val ringH = ringW * sin(Geo.rad(el)).toFloat()
        drawOval(Hud.blue(0.3f), topLeft = Offset(c.x - ringW / 2, c.y + px * 0.18f - ringH / 2), size = Size(ringW, ringH), style = Stroke(1.dp.toPx()))
        painter.draw(
            this, AircraftModels.mesh(airframe, Lod.DETAIL), AircraftModels.viewBasis(yawDeg.toDouble(), el, d),
            px * d, c.x, c.y, timeS, alpha = 1f, strokePx = 0.9.dp.toPx(), glowPx = 3.dp.toPx(),
        )
    }
}

/** Dimension read-outs for a model. */
object ModelFormat {
    fun metres(m: Double) = String.format(Locale.US, "%.1f M", m)

    /** "L 37.6 · SPAN 35.8 M", or the rotor / envelope for rotorcraft and balloons. */
    fun size(a: Airframe): String = when {
        a.rotorM != null -> "L ${fmt(a.lengthM)} · ROTOR ${fmt(a.rotorM!!)} M"
        a.kind == ModelKind.BALLOON -> "H ${fmt(a.lengthM)} · Ø ${fmt(a.spanM)} M"
        else -> "L ${fmt(a.lengthM)} · SPAN ${fmt(a.spanM)} M"
    }

    private fun fmt(m: Double) = String.format(Locale.US, "%.1f", m)
}

/** FROM → TO with airport names and how far along the flight is. */
@Composable
private fun RouteBlock(route: FlightRoute?, aircraft: Aircraft, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        if (route == null) {
            BasicText(
                if (aircraft.callsign.isNullOrBlank()) "ROUTE · NO CALLSIGN" else "ROUTE · LOOKING UP / NOT IN DATABASE",
                style = Hud.text(10.sp, alpha = 0.5f, glow = false, spacing = 1.sp),
            )
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AirportCell("FROM", route.origin, Modifier.weight(1f))
            BasicText("  ✈  ", style = Hud.text(16.sp, weight = FontWeight.Bold))
            AirportCell("TO", route.destination, Modifier.weight(1f), alignEnd = true)
        }
        val progress = route.progress(aircraft.lat, aircraft.lon)
        if (progress != null) {
            Canvas(Modifier.fillMaxWidth().padding(top = 6.dp).height(12.dp)) {
                val y = size.height / 2
                drawLine(Hud.blue(0.25f), Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                val x = size.width * progress.toFloat()
                drawLine(Hud.Blue, Offset(0f, y), Offset(x, y), 3.dp.toPx())
                drawCircle(Hud.Blue, 5.dp.toPx(), Offset(x, y))
                drawCircle(Hud.blue(0.6f), 3.dp.toPx(), Offset(0f, y))
                drawCircle(Hud.blue(0.6f), 3.dp.toPx(), Offset(size.width, y))
            }
        }
        BasicText(
            listOfNotNull(
                progress?.let { "${(it * 100).roundToInt()}% FLOWN" },
                route.midpoint?.let { "VIA ${it.code}" },
                "SCHEDULED ROUTE · ${route.source.display}" + if (route.plausible) "" else " · UNCONFIRMED",
            ).joinToString("  ·  "),
            style = Hud.text(9.sp, alpha = if (route.plausible) 0.6f else 0.4f, glow = false, spacing = 1.sp),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun AirportCell(label: String, airport: Airport, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        BasicText("$label  ${airport.code}", style = Hud.text(18.sp, weight = FontWeight.Bold, spacing = 1.sp))
        BasicText(
            listOfNotNull(airport.city, airport.country).joinToString(", ").ifBlank { airport.icao ?: "" }.uppercase(Locale.US),
            style = Hud.text(10.sp, alpha = 0.75f, glow = false),
            maxLines = 1,
        )
        airport.name?.let {
            BasicText(it, style = Hud.text(10.sp, alpha = 0.55f, glow = false), maxLines = 1)
        }
    }
}
