package com.ghaith.ironhud

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.ghaith.ironhud.capture.HudCapture
import com.ghaith.ironhud.data.HudSettings
import com.ghaith.ironhud.nightvision.NightVision
import com.ghaith.ironhud.ui.hud.Attitude
import com.ghaith.ironhud.ui.hud.HudContent
import com.ghaith.ironhud.ui.hud.HudSliderStrip
import com.ghaith.ironhud.ui.hud.LowLightHint
import com.ghaith.ironhud.vision.NightVisionBitmaps
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

/** Night vision: the HUD over an amplified dark scene, the low-light hint, and the SNAP / AI paths. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-xhdpi")
class NightVisionScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val dir = File("build/hud-screenshots").apply { mkdirs() }
    private val gain = NightVision.gain(0.5f)

    @Test
    fun nightVisionHud() {
        val amplified = NightVisionBitmaps.toHud(darkRoom(), gain, seed = 3).asImageBitmap()
        render("night_vision_hud", HudState(nightVision = true, nightEv = 2f, nightMinFps = 7, ambientLux = 1.5f)) { state ->
            Image(amplified, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            HudContent(state, HudSettings(), Attitude(heading = 212f), 1_790_000_000_000L, 64, {}, {}, animate = false)
            Column(Modifier.fillMaxSize().padding(bottom = 20.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                HudSliderStrip(
                    title = NightVision.label(state.nightGain, state.nightEv, state.nightMinFps, state.ambientLux),
                    fraction = state.nightGain, onMinus = {}, onPlus = {}, onSet = {}, onReset = {}, width = 320.dp,
                )
            }
        }
    }

    @Test
    fun lowLightHint() {
        val raw = darkRoom().asImageBitmap()
        render("night_vision_hint", HudState(lowLight = true, ambientLux = 3.2f), requireColour = false) { state ->
            Image(raw, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            HudContent(state, HudSettings(), Attitude(heading = 212f), 1_790_000_000_000L, 64, {}, {}, animate = false)
            Column(Modifier.fillMaxSize().padding(bottom = 20.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                LowLightHint(state.ambientLux, onEnable = {}, onDismiss = {})
            }
        }
    }

    @Test
    fun snapAndAiPaths() {
        val scene = darkRoom()
        val overlay = Bitmap.createBitmap(scene.width, scene.height, Bitmap.Config.ARGB_8888)
        val snap = HudCapture.compose(scene, overlay, tint = true, nightGain = gain)
        assertTrue("snap brighter", meanLuma(snap) > meanLuma(scene) * 3)
        assertOnlyHudBlue(snap)

        // Before / after, side by side, for review.
        val pair = Bitmap.createBitmap(scene.width * 2, scene.height, Bitmap.Config.ARGB_8888)
        Canvas(pair).apply {
            drawBitmap(scene, 0f, 0f, null)
            drawBitmap(snap, scene.width.toFloat(), 0f, null)
        }
        File(dir, "night_vision_capture.png").outputStream().use { pair.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // The AI gets a brighter image that is still in colour (the warm lamp stays warm).
        val ai = NightVisionBitmaps.brighten(scene, gain)
        assertTrue("AI crop brighter", meanLuma(ai) > meanLuma(scene) * 2)
        val lamp = ai.getPixel((scene.width * 0.72f).toInt(), (scene.height * 0.35f).toInt())
        assertTrue("lamp still warm", Color.red(lamp) > Color.blue(lamp))
    }

    private fun render(name: String, state: HudState, requireColour: Boolean = true, content: @androidx.compose.runtime.Composable (HudState) -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { Box(Modifier.fillMaxSize()) { content(state) } }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (requireColour) assertOnlyHudBlue(bitmap)
    }

    /** A dim room as a phone camera sees it at night: a faint doorway, a lamp, a window, a floor. */
    private fun darkRoom(): Bitmap {
        val w = 1280
        val h = 800
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(5, 6, 8))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, h * 0.6f, 0f, h.toFloat(), Color.rgb(8, 8, 9), Color.rgb(20, 18, 16), Shader.TileMode.CLAMP)
        c.drawRect(0f, h * 0.6f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.color = Color.rgb(16, 16, 20)
        c.drawRect(w * 0.12f, h * 0.18f, w * 0.30f, h * 0.62f, p)          // doorway
        p.color = Color.rgb(24, 28, 36)
        c.drawRect(w * 0.42f, h * 0.14f, w * 0.60f, h * 0.40f, p)          // window
        p.color = Color.rgb(10, 12, 16)
        c.drawRect(w * 0.505f, h * 0.14f, w * 0.515f, h * 0.40f, p)        // window bar
        p.color = Color.rgb(46, 34, 20)
        c.drawCircle(w * 0.72f, h * 0.35f, h * 0.05f, p)                   // warm lamp
        p.color = Color.rgb(14, 12, 10)
        c.drawRect(w * 0.64f, h * 0.45f, w * 0.86f, h * 0.66f, p)          // table
        p.color = Color.rgb(12, 12, 13)
        c.drawCircle(w * 0.36f, h * 0.36f, h * 0.05f, p)                   // someone in the doorway
        c.drawRect(w * 0.33f, h * 0.41f, w * 0.39f, h * 0.66f, p)
        return bmp
    }

    private fun meanLuma(b: Bitmap): Double {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return px.sumOf { NightVision.luma(it).toLong() }.toDouble() / px.size
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
        assertTrue("drew nothing", coloured > 1_000)
        assertTrue("$offHue of $coloured coloured pixels are not HUD blue", offHue <= coloured / 200)
    }
}
