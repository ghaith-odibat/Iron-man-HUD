package com.ghaith.ironhud.ui.hud

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ghaith.ironhud.plane.Projector
import kotlinx.coroutines.delay
import kotlin.math.PI

/** Where the back camera points: heading (0-360°), pitch and roll in degrees. */
data class Attitude(val heading: Float = 0f, val pitch: Float = 0f, val roll: Float = 0f)

/**
 * Live device orientation. [rot] (a low-passed rotation-vector matrix) is updated at sensor rate
 * without triggering recomposition, for per-frame AR drawing; [attitude] is a throttled Compose
 * state for the compass/horizon read-outs. Both use [Projector] so the compass and the plane
 * overlays agree.
 */
class SensorHub {
    @Volatile var rot: FloatArray? = null
        internal set
    @Volatile var displayRotation: Int = 0
        internal set
    /** Estimated heading accuracy from the sensor, degrees (null when not reported). */
    @Volatile var headingAccuracyDeg: Float? = null
        internal set
    /** East-positive magnetic declination; set once we have a location so the compass reads true north. */
    @Volatile var declinationDeg: Double = 0.0

    val attitude = mutableStateOf(Attitude())
}

@Composable
fun rememberSensorHub(): SensorHub {
    val context = LocalContext.current
    val hub = remember { SensorHub() }
    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val raw = FloatArray(9)
        val filtered = FloatArray(9)
        var primed = false
        var lastAttitude = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(raw, event.values)
                if (!primed) {
                    raw.copyInto(filtered)
                    primed = true
                } else {
                    for (i in 0 until 9) filtered[i] += (raw[i] - filtered[i]) * SMOOTH
                }
                hub.rot = filtered.copyOf()
                hub.displayRotation = displayRotation(context)
                hub.headingAccuracyDeg = event.values.getOrNull(4)
                    ?.takeIf { it > 0f }?.let { (it * 180f / PI.toFloat()) }

                val now = event.timestamp / 1_000_000
                if (now - lastAttitude >= ATTITUDE_INTERVAL_MS) {
                    lastAttitude = now
                    val o = Projector.orientation(filtered, hub.displayRotation, hub.declinationDeg)
                    hub.attitude.value = Attitude(o.headingDeg.toFloat(), o.pitchDeg.toFloat(), o.rollDeg.toFloat())
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
    return hub
}

private const val SMOOTH = 0.2f
private const val ATTITUDE_INTERVAL_MS = 66L

@Suppress("DEPRECATION")
internal fun displayRotation(context: Context): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display.rotation
    } else {
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    }

/** Ambient light in lux from the light sensor (about once a second), or null when there isn't one. */
@Composable
fun rememberAmbientLux(): State<Float?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<Float?>(null) }
    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
        var last = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = event.timestamp / 1_000_000
                if (state.value == null || now - last >= LUX_INTERVAL_MS) {
                    last = now
                    state.value = event.values[0]
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm.unregisterListener(listener) }
    }
    return state
}

private const val LUX_INTERVAL_MS = 1_000L

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
