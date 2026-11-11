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

/** A fix rougher than this (meters) is left out of speed and vehicle judging: it jumps about (m26, 11/11). */
const val MOTION_MAX_ACCURACY_M = 20f

/** A car or bus moves fast fix after fix; one jump between two fixes is GPS, not a ride (m26). */
private const val VEHICLE_FAST_STEPS = 3

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

    private var fastSteps = 0

    /**
     * Feeds one fix. [accuracyM] is the phone's own error for it (0 = unknown): a rough one is skipped, since a
     * jump of a few tens of meters read as 6 m/s for 8 s showed "차로 이동 중" while walking (영상 9:40, m26).
     */
    fun add(position: LatLon, timeMs: Long, accuracyM: Float = 0f) {
        if (accuracyM > MOTION_MAX_ACCURACY_M) return
        fixes.lastOrNull()?.let { (prev, prevAt) ->
            val dt = (timeMs - prevAt) / 1000.0
            if (dt > 0) fastSteps = if (metersBetween(prev, position) / dt >= VEHICLE_SPEED_MPS) fastSteps + 1 else 0
        }
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
            if (timeMs - since >= VEHICLE_AFTER_MS && fastSteps >= VEHICLE_FAST_STEPS) inVehicle = true
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
    // Walking on the route but clearly another way (turned the corner early, crossed over): the way they go,
    // so the map turns while walking too (11/10).
    onRoute && moving && course != null && courseAngle(course, routeAhead) >= COURSE_DIFFERS_DEGREES -> course
    onRoute -> routeAhead
    moving && course != null -> course
    else -> compass ?: routeAhead
}

/**
 * Which way the walker's arrow points: always the compass (where the phone faces), walking or not, so every
 * turn of the body shows at once (11/10); without a compass, [heading]. The map uses it only standing still;
 * walking, the map keeps to [heading] so it does not swing.
 */
fun arrowHeading(moving: Boolean, heading: Float, compass: Float?): Float = compass ?: heading

/** Walking this far off the route's direction (degrees), the map follows the GPS course instead. */
const val COURSE_DIFFERS_DEGREES = 45f

private fun courseAngle(a: Float, b: Float): Float {
    val d = Math.abs(a - b) % 360
    return if (d > 180) 360 - d else d
}

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
