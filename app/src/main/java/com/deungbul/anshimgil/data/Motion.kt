package com.deungbul.anshimgil.data

import kotlin.math.atan2

/*
 * How the walker is moving, from the last few position fixes (11/8 phone test):
 *  - the way they are going (GPS course), steadier than the compass while walking;
 *  - riding in a car or bus, so walking guidance pauses instead of racing along the route.
 */

/** Faster than this (about 22 km/h) for [VEHICLE_AFTER_MS] means a car or bus. */
const val VEHICLE_SPEED_MPS = 6.0

/** Slower than this (about 11 km/h) for [WALK_AGAIN_AFTER_MS] means walking again. */
const val WALK_AGAIN_SPEED_MPS = 3.0

const val VEHICLE_AFTER_MS = 8_000L
const val WALK_AGAIN_AFTER_MS = 15_000L

/** Over this window the speed and the course are worked out. */
private const val WINDOW_MS = 10_000L

/** The course needs at least this much movement in the window, or GPS noise would steer it. */
private const val COURSE_MIN_M = 8.0

/** Feeds raw position fixes; tells speed, course and whether the walker is in a vehicle. */
class MotionTracker {
    private val fixes = ArrayDeque<Pair<LatLon, Long>>()
    private var fastSince: Long? = null
    private var slowSince: Long? = null

    /** True while riding (see [VEHICLE_SPEED_MPS]). */
    var inVehicle = false
        private set

    /** Meters per second over the last [WINDOW_MS]; null with too few fixes. */
    var speedMps: Double? = null
        private set

    /** Degrees clockwise from north the walker is going; null when they have not moved enough to tell. */
    var courseDeg: Float? = null
        private set

    fun add(position: LatLon, timeMs: Long) {
        fixes.addLast(position to timeMs)
        while (fixes.size > 2 && timeMs - fixes.first().second > WINDOW_MS) fixes.removeFirst()
        val (first, firstAt) = fixes.first()
        val dt = (timeMs - firstAt) / 1000.0
        if (dt < 2.0) return
        val meters = metersBetween(first, position)
        val speed = meters / dt
        speedMps = speed
        courseDeg = if (meters >= COURSE_MIN_M) bearingDeg(first, position) else null

        if (speed >= VEHICLE_SPEED_MPS) {
            slowSince = null
            val since = fastSince ?: timeMs.also { fastSince = it }
            if (timeMs - since >= VEHICLE_AFTER_MS) inVehicle = true
        } else if (speed <= WALK_AGAIN_SPEED_MPS) {
            fastSince = null
            val since = slowSince ?: timeMs.also { slowSince = it }
            if (timeMs - since >= WALK_AGAIN_AFTER_MS) inVehicle = false
        }
    }
}

/** Degrees clockwise from north, from [a] to [b]. */
fun bearingDeg(a: LatLon, b: LatLon): Float {
    val dx = (b.lon - a.lon) * Math.cos(Math.toRadians(a.lat))
    val dy = b.lat - a.lat
    return ((Math.toDegrees(atan2(dx, dy)) + 360) % 360).toFloat()
}

/**
 * Which way the arrow and the map should face. On the route, walking or not: the route ahead (it cannot flip,
 * and switching to the compass whenever the walker slowed at a corner swung the map about, 11/9 video).
 * Walking off the route: the way they are actually going (GPS). Standing still off it: the compass, which
 * shows where the phone points; without one, the route ahead.
 */
fun guideHeading(onRoute: Boolean, moving: Boolean, routeAhead: Float, course: Float?, compass: Float?): Float = when {
    onRoute -> routeAhead
    moving && course != null -> course
    else -> compass ?: routeAhead
}

/**
 * Which way the walker's arrow points. The map keeps facing the route ahead (above), but standing still the
 * arrow follows the compass, so turning round shows on screen at once instead of the arrow staying fixed (11/10).
 * Walking: the same way as the map.
 */
fun arrowHeading(moving: Boolean, heading: Float, compass: Float?): Float = if (!moving && compass != null) compass else heading

/** Moving from this speed (m/s) up… */
const val MOVING_ON_MPS = 0.6

/** …and still moving until slower than this for [STILL_AFTER_MS]: a short pause at a corner is not stopping. */
const val MOVING_OFF_MPS = 0.3
const val STILL_AFTER_MS = 3_000L

/** Whether the walker is moving, with the hysteresis above, so it does not flick on and off at one speed. */
class MovingState {
    var moving = false
        private set
    private var slowSince: Long? = null

    fun update(speedMps: Double?, timeMs: Long): Boolean {
        val v = speedMps ?: 0.0
        when {
            v >= MOVING_ON_MPS -> { moving = true; slowSince = null }
            v < MOVING_OFF_MPS -> {
                val since = slowSince ?: timeMs.also { slowSince = it }
                if (timeMs - since >= STILL_AFTER_MS) moving = false
            }
            else -> slowSince = null
        }
        return moving
    }
}
