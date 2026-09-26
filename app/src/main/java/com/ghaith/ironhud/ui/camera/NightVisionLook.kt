package com.ghaith.ironhud.ui.camera

import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import com.ghaith.ironhud.nightvision.NightVision
import com.ghaith.ironhud.ui.theme.Hud

/**
 * How the camera preview is drawn: as is, in the HUD-blue hologram tint, or through the night-vision
 * intensifier. On Android 13+ night vision is a GPU shader with live grain ([NightShader]); older
 * versions get a colour-matrix gain.
 */
object NightVisionLook {

    /** Applies the static part of the look; an animated [NightShader] is driven separately. */
    fun apply(view: View, tint: Boolean, nightVision: Boolean, gain: Double, shaderActive: Boolean) {
        when {
            nightVision && shaderActive -> {
                view.setLayerType(View.LAYER_TYPE_NONE, null)
            }
            nightVision -> {
                clearEffect(view)
                view.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply {
                    colorFilter = ColorMatrixColorFilter(NightVision.fallbackMatrix(gain))
                })
            }
            tint -> {
                clearEffect(view)
                view.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = ColorMatrixColorFilter(Hud.TINT_MATRIX) })
            }
            else -> {
                clearEffect(view)
                view.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        }
    }

    fun clearEffect(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) view.setRenderEffect(null)
    }
}

/** The live intensifier: [NightVision.AGSL] as a render effect on the preview, re-set each frame for fresh grain. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class NightShader private constructor(private val shader: RuntimeShader) {

    fun apply(view: View, gain: Double, timeS: Double) {
        shader.setFloatUniform("gain", gain.toFloat())
        shader.setFloatUniform("norm", NightVision.norm(gain).toFloat())
        // Keep the time small: GPU floats lose the grain pattern at large values.
        shader.setFloatUniform("time", (timeS % 100.0).toFloat())
        shader.setFloatUniform("density", view.resources.displayMetrics.density)
        view.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "image"))
    }

    companion object {
        /** Null when the shader can't be built on this device (the caller falls back to the colour matrix). */
        fun create(): NightShader? = runCatching { NightShader(RuntimeShader(NightVision.AGSL)) }.getOrNull()
    }
}
