package com.deungbul.anshimgil.data

import kotlin.math.sqrt

/*
 * 뛰기 경고 (어린이 모드, 11/9 팀 아이디어 5-2): a child who starts running near a road crossing or where
 * roads meet is warned before getting there, with vibration, a beep and a short word.
 *
 * Running is told from the phone's own movement sensor (accelerometer) over the last two seconds: strong
 * shaking at a running step rate. Android's activity recognition also knows running, but it can take tens of
 * seconds to say so, too late for a crossing a few seconds ahead. GPS speed backs it up (between running and
 * riding speed).
 */

/** The accelerometer looks back this long. */
const val RUN_WINDOW_MS = 2_000L

/** At least this much of the window must be filled before deciding. */
private const val RUN_MIN_SPAN_MS = 1_500L

/** Shaking (standard deviation of the acceleration size, m/s²) above this: walking is usually 1.5–3. */
const val RUN_SHAKE = 3.5

/** Steps per second above this (about 145 a minute); walking is about 1.7–2. */
const val RUN_STEP_HZ = 2.4

/** GPS speed from this (m/s, about 9 km/h) up to riding speed ([VEHICLE_SPEED_MPS]) is running. */
const val RUN_SPEED_MPS = 2.5

/** Warn when a crossing or a turn is this close ahead. */
const val RUN_WARN_M = 30

const val RUN_CROSSING_TEXT = "앞에 횡단보도가 있어요. 뛰지 말고 천천히 걸어요."
const val RUN_TURN_TEXT = "앞에 길이 만나는 곳이 있어요. 뛰지 말고 천천히 걸어요."

/** Tells running from walking. Feed it accelerometer readings and GPS speeds; times in milliseconds. */
class RunDetector {
    private val times = ArrayDeque<Long>()
    private val sizes = ArrayDeque<Double>()
    private var gpsSpeed: Double? = null

    /** One accelerometer reading (x, y, z in m/s², gravity included). */
    fun addAccel(x: Float, y: Float, z: Float, tMs: Long) =
        addSize(sqrt((x * x + y * y + z * z).toDouble()), tMs)

    fun addSize(size: Double, tMs: Long) {
        times.addLast(tMs)
        sizes.addLast(size)
        while (times.isNotEmpty() && tMs - times.first() > RUN_WINDOW_MS) {
            times.removeFirst()
            sizes.removeFirst()
        }
    }

    fun addSpeed(mps: Double?) {
        gpsSpeed = mps
    }

    /** True when the phone shakes like running, or GPS says running speed. */
    val running: Boolean
        get() = shakingLikeRunning() || gpsSpeed?.let { it >= RUN_SPEED_MPS && it < VEHICLE_SPEED_MPS } == true

    private fun shakingLikeRunning(): Boolean {
        if (times.size < 10) return false
        val span = times.last() - times.first()
        if (span < RUN_MIN_SPAN_MS) return false
        val mean = sizes.average()
        val sd = sqrt(sizes.sumOf { (it - mean) * (it - mean) } / sizes.size)
        if (sd < RUN_SHAKE) return false
        // Steps: each rise above the mean by half a deviation, after having dropped below the mean.
        var steps = 0
        var low = true
        for (s in sizes) {
            if (low && s > mean + sd / 2) { steps++; low = false }
            else if (s < mean) low = true
        }
        return steps / (span / 1000.0) >= RUN_STEP_HZ
    }
}

/** A warning to give: [key] says which place, so each is warned about once. */
data class RunAlert(val key: String, val text: String, val aheadM: Int)

/**
 * What to warn a running child about, if anything: the nearest crossing within [RUN_WARN_M] ahead, else the
 * next turn (where roads meet) within that. [warned] holds keys already warned about.
 */
fun runAlert(
    progressM: Double,
    crossingsAlongM: List<Double>,
    nextTurn: Turn,
    metersToNextTurn: Int,
    warned: Set<String>,
): RunAlert? {
    crossingsAlongM.firstOrNull { it >= progressM && it - progressM <= RUN_WARN_M }?.let { c ->
        val key = "c:${c.toInt()}"
        // A crossing already warned about still holds back the turn there.
        return if (key in warned) null else RunAlert(key, RUN_CROSSING_TEXT, (c - progressM).toInt())
    }
    if ((nextTurn == Turn.LEFT || nextTurn == Turn.RIGHT) && metersToNextTurn in 0..RUN_WARN_M) {
        // Where the turn is moves a meter or two as GPS wobbles, so a neighbouring 10 m bucket counts too.
        val bucket = (progressM + metersToNextTurn).toInt() / 10
        if ((bucket - 1..bucket + 1).none { "t:$it" in warned }) return RunAlert("t:$bucket", RUN_TURN_TEXT, metersToNextTurn)
    }
    return null
}
