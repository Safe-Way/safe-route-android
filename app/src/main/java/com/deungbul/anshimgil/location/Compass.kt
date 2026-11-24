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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import com.deungbul.anshimgil.data.route.LatLon

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
        val remapped = FloatArray(9)
        var smooth: Float? = null
        var upright = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, orientation)
                // Held upright (looking at the screen while walking) the flat-phone azimuth swings and can
                // flip by 180°; then measure where the back of the phone points instead (11/8). Switching
                // only past 50° and back under 40° keeps a phone held near 45° from flicking between the two.
                val pitch = Math.abs(Math.toDegrees(orientation[1].toDouble()))
                upright = if (upright) pitch > UPRIGHT_PITCH_DEGREES - 5 else pitch > UPRIGHT_PITCH_DEGREES + 5
                if (upright) {
                    SensorManager.remapCoordinateSystem(matrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
                    SensorManager.getOrientation(remapped, orientation)
                }
                val degrees = ((Math.toDegrees(orientation[0].toDouble()) + 360) % 360).toFloat()
                // Small shakes (hand, steps) are averaged away slowly; a real turn of the body (the reading
                // far from the average) is followed fast (11/10: still the arrow wobbled, turning it lagged).
                val prev = smooth
                smooth = if (prev == null) degrees else {
                    val diff = ((degrees - prev + 540) % 360) - 180
                    val share = if (Math.abs(diff) >= COMPASS_TURN_DEGREES) COMPASS_FOLLOW_TURN else COMPASS_SMOOTHING
                    ((prev + diff * share + 360) % 360)
                }
                val now = smooth ?: return
                val old = heading
                if (old == null || angleBetween(old, now) >= COMPASS_STEP_DEGREES) heading = now
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager?.unregisterListener(listener) }
    }
    return heading
}

/** Tilted further than this from flat, the phone counts as held upright. */
private const val UPRIGHT_PITCH_DEGREES = 45.0

/** Share of each new compass reading in the running average while it stays close (lower is steadier). */
private const val COMPASS_SMOOTHING = 0.04f

/** A reading this far from the average is a real turn of the body… */
private const val COMPASS_TURN_DEGREES = 20f

/** …and is followed this fast (about a tenth of a second at the game sensor rate). */
private const val COMPASS_FOLLOW_TURN = 0.35f

/** The heading shown changes only when the phone turns at least this much. */
private const val COMPASS_STEP_DEGREES = 6f

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
