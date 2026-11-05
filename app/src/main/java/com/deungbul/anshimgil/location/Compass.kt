package com.deungbul.anshimgil.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.deungbul.anshimgil.data.LatLon
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos

/**
 * Which way the top of the phone points, in degrees clockwise from north, while the calling screen is shown.
 * Null on phones without the sensor, or until the first reading.
 */
@Composable
fun rememberCompassHeading(): Float? {
    val context = LocalContext.current
    var heading by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val matrix = FloatArray(9)
        val orientation = FloatArray(3)
        var smooth: Float? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, orientation)
                val degrees = ((Math.toDegrees(orientation[0].toDouble()) + 360) % 360).toFloat()
                // The raw reading shakes with every step, so it is smoothed heavily (a slow average
                // that follows a real turn within a second or so) and only turns of 12° or more count.
                val prev = smooth
                smooth = if (prev == null) degrees else {
                    val diff = ((degrees - prev + 540) % 360) - 180
                    ((prev + diff * COMPASS_SMOOTHING + 360) % 360)
                }
                val now = smooth ?: return
                val old = heading
                if (old == null || angleBetween(old, now) >= COMPASS_STEP_DEGREES) heading = now
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager?.unregisterListener(listener) }
    }
    return heading
}

/** Share of each new compass reading in the running average (lower is steadier). */
private const val COMPASS_SMOOTHING = 0.08f

/** The heading shown changes only when the phone turns at least this much. */
private const val COMPASS_STEP_DEGREES = 12f

/** Smallest angle between two headings in degrees (0..180). */
fun angleBetween(a: Float, b: Float): Float {
    val d = abs(a - b) % 360
    return if (d > 180) 360 - d else d
}

/** Direction from [from] to [to], degrees clockwise from north; fine for the short legs of a walk. */
fun bearingDegrees(from: LatLon, to: LatLon): Float {
    val dx = (to.lon - from.lon) * cos(Math.toRadians(from.lat))
    val dy = to.lat - from.lat
    return ((Math.toDegrees(atan2(dx, dy)) + 360) % 360).toFloat()
}
