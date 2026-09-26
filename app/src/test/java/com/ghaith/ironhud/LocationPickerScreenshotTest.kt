package com.ghaith.ironhud

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ghaith.ironhud.plane.GeoPoint
import com.ghaith.ironhud.plane.ObserverPlace
import com.ghaith.ironhud.ui.plane.LocationPickerScreen
import com.ghaith.ironhud.ui.theme.Hud
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The observer picker's chrome (the osmdroid map needs live tiles, so it's a placeholder here). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-xhdpi")
class LocationPickerScreenshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pickerChrome() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            LocationPickerScreen(
                current = ObserverPlace("London Heathrow Airport, London", 51.47, -0.4543, 25.0),
                gps = GeoPoint(31.95, 35.93, 800.0),
                busy = false,
                onPick = { _, _ -> },
                onUseMyLocation = {},
                onClose = {},
                map = { _, _, _, m -> Box(m.background(Hud.blue(0.06f))) },
            )
        }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File("build/hud-screenshots").apply { mkdirs() }
        File(dir, "location_picker.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
