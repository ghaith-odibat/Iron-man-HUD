package com.ghaith.ironhud

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ghaith.ironhud.plane.AircraftModels
import com.ghaith.ironhud.plane.LineBuffer
import com.ghaith.ironhud.plane.Lod
import com.ghaith.ironhud.plane.WireProjection
import com.ghaith.ironhud.plane.models.AircraftTypes
import com.ghaith.ironhud.ui.plane.HangarScreen
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

/** The holo hangar, and a contact sheet of every aircraft model in the catalogue. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HangarScreenshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val dir = File("build/hud-screenshots").apply { mkdirs() }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun hangarLandscape() = hangar("B744", "hangar_747")

    @Test
    @Config(qualifiers = "w800dp-h1280dp-port-xhdpi")
    fun hangarPortrait() = hangar("A20N", "hangar_a320neo_portrait")

    private fun hangar(type: String, name: String) {
        rule.mainClock.autoAdvance = false
        rule.setContent { HangarScreen(startId = type, onClose = {}, animate = false) }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertOnlyHudBlue(bitmap)
    }

    @Test
    fun modelGallery() {
        val cols = 8
        val rows = 6
        val cw = 400
        val ch = 300
        val proj = WireProjection()
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HUD; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textSize = 22f }
        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 0x66, 0xD9, 0xFF); typeface = Typeface.MONOSPACE; textSize = 15f }
        fun draw(c: Canvas, b: LineBuffer, alpha: Int, w: Float) {
            line.color = Color.argb(alpha, 0x66, 0xD9, 0xFF)
            line.strokeWidth = w
            c.drawLines(b.points, 0, b.size, line)
        }
        AircraftTypes.catalog.chunked(cols * rows).forEachIndexed { page, list ->
            val bmp = Bitmap.createBitmap(cols * cw, rows * ch, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.BLACK)
            list.forEachIndexed { i, a ->
                val ox = (i % cols) * cw.toFloat()
                val oy = (i / cols) * ch.toFloat()
                val mesh = AircraftModels.mesh(a, Lod.DETAIL)
                proj.project(mesh, AircraftModels.viewBasis(215.0, 20.0, 3.2), cw * 0.78 * 3.2, ox + cw / 2f, oy + ch / 2f + 12f, 0.3)
                draw(c, proj.faint, 90, 1f)
                draw(c, proj.far, 110, 1.2f)
                draw(c, proj.near, 240, 1.4f)
                c.drawText(a.codes.joinToString("/").take(22), ox + 12f, oy + 30f, title)
                c.drawText(a.name.take(40), ox + 12f, oy + ch - 16f, caption)
            }
            File(dir, "model_gallery_${page + 1}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertOnlyHudBlue(bmp)
        }
    }

    private companion object {
        val HUD = Color.rgb(0x66, 0xD9, 0xFF)

        fun assertOnlyHudBlue(bitmap: Bitmap) {
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
}
