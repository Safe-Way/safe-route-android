package com.deungbul.anshimgil.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

/*
 * Personal walking speed (decision 54, FR-52). Measured during guidance; only the speed, distance,
 * moving time and date are kept, never where or when exactly the walk was. Rules from the team's
 * migration 025_walk_profile.sql.
 */

/** One measured walk. */
data class WalkSample(val speedMPerMin: Double, val distanceM: Int, val movingSec: Int, val date: String) {
    fun encode() = "%.2f|%d|%d|%s".format(java.util.Locale.US, speedMPerMin, distanceM, movingSec, date)

    companion object {
        /** Only walks of at least this length are measured. */
        const val MIN_DISTANCE_M = 300.0
        /** Speeds outside this range are not walking (e.g. standing in a bus, or GPS jumps). */
        val VALID_SPEED = 20.0..150.0
        /** The newest this many walks count. */
        const val KEEP = 20

        fun decode(text: String): WalkSample? {
            val parts = text.split('|')
            if (parts.size != 4) return null
            return WalkSample(
                parts[0].toDoubleOrNull() ?: return null,
                parts[1].toIntOrNull() ?: return null,
                parts[2].toIntOrNull() ?: return null,
                parts[3],
            )
        }
    }
}

/** How much the mode's default speed counts, as if it were this many walks. */
private const val PRIOR_WEIGHT = 5

/**
 * The speed to use: the mode's default blended with the measured walks,
 * (default × 5 + sum of measured) ÷ (5 + count), so it moves towards the walker's own pace as they walk.
 */
fun personalSpeed(mode: UserMode, samples: List<WalkSample>): Double {
    val recent = samples.takeLast(WalkSample.KEEP)
    return (mode.walkingSpeedMPerMin * PRIOR_WEIGHT + recent.sumOf { it.speedMPerMin }) / (PRIOR_WEIGHT + recent.size)
}

/** Minutes to walk [meters] at [speedMPerMin], rounded up. */
fun walkMinutes(meters: Double, speedMPerMin: Double): Int = kotlin.math.ceil(meters / speedMPerMin).toInt().coerceAtLeast(0)

/**
 * Measures one guided walk from the steadied positions: distance and the time actually moving.
 * Pauses (waiting at a crossing, stopping to rest) do not count, so they do not make the walker slow.
 */
class WalkMeter {
    private var last: LatLon? = null
    private var lastAt = 0L
    var distanceM = 0.0
        private set
    var movingMs = 0L
        private set

    fun add(position: LatLon, timeMs: Long) {
        val prev = last
        if (prev != null) {
            // Exact meters: the rounded-down distanceMeters loses up to a meter on each short step.
            val step = metersBetween(prev, position)
            val dt = timeMs - lastAt
            // A gap without fixes (tunnel, app in the background) says nothing about pace.
            if (dt in 1..MAX_GAP_MS) {
                val perMin = step / (dt / 60_000.0)
                if (perMin >= STOPPED_BELOW_M_PER_MIN) {
                    distanceM += step
                    movingMs += dt
                }
            }
        }
        last = position
        lastAt = timeMs
    }

    /** The walk as a sample, or null when it was too short or not a walking pace. */
    fun sample(date: String): WalkSample? {
        if (distanceM < WalkSample.MIN_DISTANCE_M || movingMs <= 0) return null
        val speed = distanceM / (movingMs / 60_000.0)
        if (speed !in WalkSample.VALID_SPEED) return null
        return WalkSample(speed, distanceM.toInt(), (movingMs / 1000).toInt(), date)
    }

    private companion object {
        /** Slower than this between two fixes counts as standing still. */
        const val STOPPED_BELOW_M_PER_MIN = 15.0
        const val MAX_GAP_MS = 15_000L
    }
}

/** Measured walks per mode, kept on this phone, plus the random guest id the server will use later. */
class WalkSpeedRepository(private val context: Context) {
    private fun key(mode: UserMode) = stringPreferencesKey("walk_samples_${mode.key}")
    private val guestKey = stringPreferencesKey("guest_id")

    fun samples(mode: UserMode): Flow<List<WalkSample>> =
        context.settingsDataStore.data.map { prefs -> prefs[key(mode)].orEmpty().lines().mapNotNull { WalkSample.decode(it) } }

    suspend fun add(mode: UserMode, sample: WalkSample) {
        context.settingsDataStore.edit {
            val now = it[key(mode)].orEmpty().lines().mapNotNull { line -> WalkSample.decode(line) }
            it[key(mode)] = (now + sample).takeLast(WalkSample.KEEP).joinToString("\n") { s -> s.encode() }
        }
    }

    /** A random id made once on this phone; it says nothing about who the walker is. */
    suspend fun guestId(): String {
        context.settingsDataStore.data.first()[guestKey]?.let { return it }
        val id = UUID.randomUUID().toString()
        context.settingsDataStore.edit { it[guestKey] = id }
        return id
    }
}

/** Straight-line meters between two points, unrounded. */
internal fun metersBetween(a: LatLon, b: LatLon): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dLon / 2).let { it * it }
    return 2 * r * Math.asin(Math.sqrt(h))
}
