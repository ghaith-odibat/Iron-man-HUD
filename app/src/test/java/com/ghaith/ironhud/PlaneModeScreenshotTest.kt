package com.ghaith.ironhud

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ghaith.ironhud.ai.Brief
import com.ghaith.ironhud.airspace.AirspaceState
import com.ghaith.ironhud.data.HudSettings
import com.ghaith.ironhud.plane.Aircraft
import com.ghaith.ironhud.plane.CameraOptics
import com.ghaith.ironhud.plane.FeedSource
import com.ghaith.ironhud.plane.Geo
import com.ghaith.ironhud.plane.GeoPoint
import com.ghaith.ironhud.plane.PlaneHitIndex
import com.ghaith.ironhud.ui.hud.Attitude
import com.ghaith.ironhud.ui.hud.HudContent
import com.ghaith.ironhud.ui.hud.SensorHub
import com.ghaith.ironhud.ui.plane.PlaneScene
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Renders Plane Mode with synthetic live traffic over Amman, tablet facing north. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PlaneModeScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val viewer = GeoPoint(31.95, 35.93, 800.0)
    private val now = 1_790_000_000_000L

    private fun at(eastKm: Double, northKm: Double): Pair<Double, Double> {
        val lat = viewer.lat + Geo.deg(northKm * 1000 / Geo.EARTH_RADIUS_M)
        val lon = viewer.lon + Geo.deg(eastKm * 1000 / (Geo.EARTH_RADIUS_M * kotlin.math.cos(Geo.rad(viewer.lat))))
        return lat to lon
    }

    private fun plane(hex: String, cs: String, type: String?, cat: String, eastKm: Double, northKm: Double, altM: Double, track: Double, vs: Double = 0.0): Aircraft {
        val (lat, lon) = at(eastKm, northKm)
        return Aircraft(hex, cs, null, type, null, null, cat, lat, lon, altM, 420.0, track, vs, "2000", now, FeedSource.ADSB_LOL)
    }

    private val traffic = listOf(
        plane("740001", "RJA305", "A20N", "A3", 3.0, 15.0, 3_200.0, 250.0, -900.0).copy(registration = "JY-RNA"),
        plane("896002", "UAE17", "B77W", "A5", -8.0, 30.0, 10_600.0, 320.0),
        plane("740003", "JYHEL", null, "A7", -1.2, 4.0, 1_250.0, 90.0),
        plane("06a004", "QTR401", "A359", "A5", 20.0, -5.0, 11_000.0, 180.0),
        plane("4ca005", "VJT88", "GLF6", "A2", 18.0, 33.0, 12_000.0, 40.0, 1_500.0),
    )

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun planeModeLandscape() = render("plane_mode_landscape")

    @Test
    @Config(qualifiers = "w800dp-h1280dp-port-xhdpi")
    fun planeModePortrait() = render("plane_mode_portrait")

    private fun render(name: String) {
        rule.mainClock.autoAdvance = false
        val sensors = SensorHub().apply {
            // Upright, camera facing (magnetic) north.
            rot = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)
            headingAccuracyDeg = 9f
        }
        val hits = PlaneHitIndex()
        val scene = PlaneScene(
            airspace = AirspaceState(
                active = true, viewer = viewer, viewerAccuracyM = 6f, declinationDeg = 0.0,
                aircraft = traffic, source = FeedSource.ADSB_LOL, lastUpdateMs = now - 3_000, status = "LIVE",
            ),
            sensors = sensors,
            optics = CameraOptics(),
            zoom = 1f,
            selectedHex = "740001",
            panel = PanelUi(
                brief = Brief(
                    name = "Airbus A320neo", type = "Narrow-body airliner", confidence = "high",
                    summary = "Royal Jordanian's A320neo, descending toward Queen Alia International. " +
                        "The neo's new engines burn about 15% less fuel than the original A320.",
                    facts = listOf("CFM LEAP-1A or PW1100G engines", "Seats about 165 in two classes"),
                ),
                status = "BRIEF COMPLETE", streaming = false, source = "GEMINI · gemini-3.1-flash-lite · 640 MS",
            ),
            hits = hits,
            clock = { now },
        )

        rule.setContent {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                androidx.compose.ui.graphics.Color(0xFF0A1418), androidx.compose.ui.graphics.Color(0xFF02060A),
            )))) {
                HudContent(
                    state = HudState(planeMode = true, selectedHex = "740001"),
                    settings = HudSettings(),
                    attitude = Attitude(heading = 0f),
                    timeMs = now,
                    battery = 76,
                    onClose = {},
                    onRescan = {},
                    animate = false,
                    plane = scene,
                )
            }
        }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()

        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File("build/hud-screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // Visible planes were registered for tap selection.
        assertTrue("no planes drawn", hits.nearest(root.width / 2f, root.height / 2f, 10_000f) != null)
        assertOnlyHudBlue(bitmap)
    }

    private fun assertOnlyHudBlue(bitmap: Bitmap) {
        val hudHue = FloatArray(3).also { Color.RGBToHSV(0x66, 0xD9, 0xFF, it) }[0]
        val hsv = FloatArray(3)
        var coloured = 0
        var offHue = 0
        val px = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (c in px) {
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            val mx = max(r, max(g, b))
            val sat = if (mx == 0) 0f else (mx - min(r, min(g, b))) / mx.toFloat()
            if (mx < 60 || sat < 0.35f) continue
            coloured++
            Color.RGBToHSV(r, g, b, hsv)
            if (abs(hsv[0] - hudHue) > 12f) offHue++
        }
        assertTrue("HUD drew nothing", coloured > 1_000)
        assertTrue("$offHue of $coloured coloured pixels are not HUD blue", offHue <= coloured / 200)
    }
}
