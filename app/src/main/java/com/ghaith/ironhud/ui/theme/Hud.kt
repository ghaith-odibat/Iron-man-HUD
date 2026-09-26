package com.ghaith.ironhud.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The app's single colour. Everything on screen is [Blue] at some alpha, on black —
 * no second hue anywhere (errors pulse instead of turning red).
 */
object Hud {
    val Blue = Color(0xFF66D9FF)
    val Black = Color.Black

    fun blue(alpha: Float) = Blue.copy(alpha = alpha)

    val Glow = Shadow(color = Blue.copy(alpha = 0.75f), blurRadius = 10f)

    fun text(
        size: TextUnit = 13.sp,
        alpha: Float = 1f,
        weight: FontWeight = FontWeight.Normal,
        glow: Boolean = true,
        spacing: TextUnit = 0.8.sp,
    ) = TextStyle(
        color = blue(alpha),
        fontFamily = FontFamily.Monospace,
        fontSize = size,
        fontWeight = weight,
        letterSpacing = spacing,
        shadow = if (glow) Glow else null,
    )

    /** Colour matrix that turns the camera feed into a monochrome HUD-blue hologram. */
    val TINT_MATRIX: FloatArray = run {
        val (r, g, b) = Triple(0x66 / 255f, 0xD9 / 255f, 1f)
        val boost = 1.2f
        fun row(k: Float) = floatArrayOf(0.299f * k * boost, 0.587f * k * boost, 0.114f * k * boost, 0f, 0f)
        row(r) + row(g) + row(b) + floatArrayOf(0f, 0f, 0f, 1f, 0f)
    }
}
