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
import com.ghaith.ironhud.plane.ModelKind
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
                draw(c, proj.accent, 255, 2.4f)
                c.drawText(a.codes.joinToString("/").take(22), ox + 12f, oy + 30f, title)
                c.drawText(a.name.take(40), ox + 12f, oy + ch - 16f, caption)
            }
            File(dir, "model_gallery_${page + 1}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertOnlyHudBlue(bmp)
        }
    }

    /** Nose close-ups, one per windshield layout, each centred on its glazing. */
    @Test
    fun windshields() {
        val list = NOSES.split(",").map { requireNotNull(AircraftTypes.byCode(it)) { it } }
        val cols = 8
        val cw = 320
        val ch = 250
        val bmp = Bitmap.createBitmap(cols * cw, ((list.size + cols - 1) / cols) * ch, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.BLACK)
        val proj = WireProjection()
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HUD; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textSize = 17f }
        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 0x66, 0xD9, 0xFF); typeface = Typeface.MONOSPACE; textSize = 11f }
        fun draw(b: LineBuffer, alpha: Int, w: Float) {
            line.color = Color.argb(alpha, 0x66, 0xD9, 0xFF)
            line.strokeWidth = w
            c.drawLines(b.points, 0, b.size, line)
        }
        list.forEachIndexed { i, a ->
            val ox = (i % cols) * cw.toFloat()
            val oy = (i / cols) * ch.toFloat()
            val mesh = AircraftModels.mesh(a, Lod.DETAIL)
            assertTrue("${a.id} has no glazing", mesh.accent.isNotEmpty())
            var gy = 0.0
            var gz = 0.0
            for (k in mesh.accent) {
                gy += mesh.vertices[k * 3 + 1]
                gz += mesh.vertices[k * 3 + 2]
            }
            gy /= mesh.accent.size
            gz /= mesh.accent.size
            val d = 3.0
            val b = AircraftModels.viewBasis(215.0, 18.0, d)
            for (k in 0..2) b[k] -= b[6 + k] * gy + b[9 + k] * gz
            val zoom = if (a.kind == ModelKind.HELI) 2.2 else 3.4
            c.save()
            c.clipRect(ox, oy, ox + cw, oy + ch)
            proj.project(mesh, b, cw * zoom * d, ox + cw / 2f, oy + ch / 2f + 8f, 0.3)
            draw(proj.faint, 110, 1f)
            draw(proj.far, 90, 1f)
            draw(proj.near, 200, 1f)
            draw(proj.accent, 70, 5f)
            draw(proj.accent, 255, 2.2f)
            c.restore()
            c.drawText(a.codes.first() + "  " + a.name.take(26), ox + 8f, oy + 20f, title)
            val shield = a.features.firstOrNull { f -> GLAZING.any { it in f } }.orEmpty()
            c.drawText(shield.take(46), ox + 8f, oy + ch - 10f, caption)
        }
        File(dir, "windshields.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertOnlyHudBlue(bmp)
    }

    private companion object {
        val HUD = Color.rgb(0x66, 0xD9, 0xFF)

        val GLAZING = listOf("PANE", "WINDSHIELD", "CANOPY", "GREENHOUSE", "COCKPITS", "GLAZED")

        /** One aircraft per windshield layout. */
        const val NOSES = "A320,BCS3,A359,A388,A3ST,B738,B733,B744,B77W,B789,B752,MD82,MD11,DC10,L101,E190,E145,CRJ9,AT76,DH8D,SF34,F100,B463,C919," +
            "AJ27,T154,IL76,A124,C130,C17,A400,P3,V22,B52,GLF6,GL7T,C68A,LJ45,FA7X,E55P,HDJT,PC24,SF50,P180,C172,P28A,PA34,SR22," +
            "DA40,PC12,DHC6,AN2,E300,DH82,PC21,F16,F15,HAWK,GLID,R44,B06,AS50,EC35,A139,H64,H47,B703,BE20"

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
