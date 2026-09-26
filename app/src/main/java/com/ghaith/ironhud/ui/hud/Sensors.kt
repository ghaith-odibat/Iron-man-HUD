package com.ghaith.ironhud.ui.hud

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.PI

/** Where the back camera points: heading (0-360°), pitch and roll in degrees. */
data class Attitude(val heading: Float = 0f, val pitch: Float = 0f, val roll: Float = 0f)

@Composable
fun rememberAttitude(): State<Attitude> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Attitude()) }
    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val rot = FloatArray(9)
        val remapped = FloatArray(9)
        val angles = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, event.values)
                // Remap so the angles describe the camera's line of sight for the current screen rotation.
                val (x, y) = when (displayRotation(context)) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Z to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Z
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Z to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Z
                }
                SensorManager.remapCoordinateSystem(rot, x, y, remapped)
                SensorManager.getOrientation(remapped, angles)
                val heading = ((angles[0] * 180 / PI).toFloat() + 360f) % 360f
                val pitch = (angles[1] * 180 / PI).toFloat()
                val roll = (angles[2] * 180 / PI).toFloat()
                val prev = state.value
                // Low-pass filter; heading blends along the shortest arc.
                var dh = heading - prev.heading
                if (dh > 180) dh -= 360f
                if (dh < -180) dh += 360f
                state.value = Attitude(
                    heading = (prev.heading + dh * SMOOTH + 360f) % 360f,
                    pitch = prev.pitch + (pitch - prev.pitch) * SMOOTH,
                    roll = prev.roll + (roll - prev.roll) * SMOOTH,
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return state
}

private const val SMOOTH = 0.15f

@Suppress("DEPRECATION")
private fun displayRotation(context: Context): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display.rotation
    } else {
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    }

@Composable
fun rememberBatteryPercent(): State<Int?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(context) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        while (true) {
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            state.value = pct.takeIf { it in 0..100 }
            delay(30_000)
        }
    }
    return state
}

@Composable
fun rememberClock(): State<Long> {
    val state = remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            state.value = System.currentTimeMillis()
            delay(1_000 - state.value % 1_000)
        }
    }
    return state
}
