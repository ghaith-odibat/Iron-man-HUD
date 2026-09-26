package com.ghaith.ironhud

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ghaith.ironhud.ai.Brief
import com.ghaith.ironhud.ai.ProviderId
import com.ghaith.ironhud.data.HudSettings
import com.ghaith.ironhud.ui.hud.Attitude
import com.ghaith.ironhud.ui.hud.HudContent
import com.ghaith.ironhud.vision.EdgeWireframe
import com.ghaith.ironhud.vision.TrackedObject
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

/**
 * Renders the HUD (no camera) to PNGs in app/build/hud-screenshots so the look can be reviewed
 * in CI artifacts, and checks the "only light tech blue" rule pixel by pixel.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HudScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletLandscape() = render("hud_tablet_landscape")

    @Test
    @Config(qualifiers = "w800dp-h1280dp-port-xhdpi")
    fun tabletPortrait() = render("hud_tablet_portrait")

    private fun render(name: String) {
        rule.mainClock.autoAdvance = false
        val density = rule.activity.resources.displayMetrics.density
        val w = rule.activity.resources.configuration.screenWidthDp * density
        val h = rule.activity.resources.configuration.screenHeightDp * density
        val cx = w * 0.62f
        val cy = h * 0.5f
        val locked = TrackedObject(7, Rect(cx - 150 * density / 2, cy - 110 * density / 2, cx + 150 * density / 2, cy + 110 * density / 2), "Home good")
        val other = TrackedObject(12, Rect(w * 0.78f, h * 0.18f, w * 0.9f, h * 0.36f), "Plant")

        val state = HudState(
            targets = listOf(locked, other),
            dwellId = 12,
            dwellProgress = 0.6f,
            lock = LockUi(7, locked.box, LockPhase.LOCKED),
            panel = PanelUi(
                brief = Brief(
                    name = "Santa Monica Ferris Wheel",
                    type = "Landmark",
                    confidence = "high",
                    summary = "The Pacific Wheel is a solar-powered Ferris wheel on Santa Monica Pier, " +
                        "overlooking the Pacific Ocean. It is one of Los Angeles' most photographed icons.",
                    facts = listOf("Stands about 26 m (85 ft) tall", "Lit by 174,000 LEDs", "Rebuilt on the pier in 2008"),
                ),
                prelim = "FERRIS WHEEL · 91%",
                status = "TARGET IDENTIFIED",
                source = "GEMINI · gemini-3.1-flash-lite · 820 MS",
                streaming = false,
                wireframe = EdgeWireframe.render(syntheticWheel()).asImageBitmap(),
            ),
            uplink = Uplink(ProviderId.GEMINI, 2, 3, 3, 4),
        )

        rule.setContent {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF0A1418), androidx.compose.ui.graphics.Color(0xFF02060A)))
                )
            ) {
                HudContent(
                    state = state,
                    settings = HudSettings(),
                    attitude = Attitude(heading = 287f, pitch = 4f, roll = -6f),
                    timeMs = 1_790_000_000_000L,
                    battery = 82,
                    onClose = {},
                    onRescan = {},
                    animate = false,
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

        assertOnlyHudBlue(bitmap)
    }

    /** Every clearly coloured pixel must have HUD blue's hue (grey/black background excluded). */
    private fun assertOnlyHudBlue(bitmap: Bitmap) {
        val hsv = FloatArray(3)
        var coloured = 0
        var offHue = 0
        val px = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (c in px) {
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            val sat = if (max(r, max(g, b)) == 0) 0f else (max(r, max(g, b)) - min(r, min(g, b))) / max(r, max(g, b)).toFloat()
            if (max(r, max(g, b)) < 60 || sat < 0.35f) continue
            coloured++
            Color.RGBToHSV(r, g, b, hsv)
            if (abs(hsv[0] - HUD_HUE) > 12f) offHue++
        }
        assertTrue("HUD drew nothing", coloured > 1_000)
        assertTrue("$offHue of $coloured coloured pixels are not HUD blue", offHue <= coloured / 200)
    }

    private fun syntheticWheel(): Bitmap {
        val bmp = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(20, 24, 30))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = Color.WHITE
        }
        c.drawCircle(160f, 150f, 120f, p)
        c.drawCircle(160f, 150f, 20f, p)
        for (k in 0 until 16) {
            val a = Math.toRadians(k * 22.5)
            c.drawLine(160f, 150f, (160 + 120 * Math.cos(a)).toFloat(), (150 + 120 * Math.sin(a)).toFloat(), p)
        }
        c.drawLine(160f, 150f, 90f, 310f, p)
        c.drawLine(160f, 150f, 230f, 310f, p)
        return bmp
    }

    private companion object {
        val HUD_HUE: Float = FloatArray(3).also { Color.RGBToHSV(0x66, 0xD9, 0xFF, it) }[0]
    }
}
