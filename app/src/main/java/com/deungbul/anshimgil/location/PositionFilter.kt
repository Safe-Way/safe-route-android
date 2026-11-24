package com.deungbul.anshimgil.location

import com.deungbul.anshimgil.data.place.distanceMeters
import com.deungbul.anshimgil.data.route.LatLon


/**
 * Steadies the phone's position for the map. The phone reports fixes from GPS, Wi-Fi and cell towers
 * at once; the rougher ones land tens of meters away, so the marker kept jumping back and forth.
 *
 * - A fix much rougher than the last good one is ignored for a while.
 * - Moves smaller than the fix's own error are ignored (the walker is standing still).
 * - Accepted fixes are blended into the shown position, trusting precise ones more.
 */
class PositionFilter {
    private var shown: LatLon? = null
    private var shownAccuracy = Float.MAX_VALUE
    private var lastAcceptedAt = 0L

    /** Feeds one fix; returns the position to show, or null to keep the current one. */
    fun accept(fix: LatLon, accuracyM: Float, timeMs: Long): LatLon? {
        val last = shown
        val accuracy = if (accuracyM > 0f) accuracyM else ROUGH_M
        if (last == null) return take(fix, accuracy, timeMs)

        val stale = timeMs - lastAcceptedAt > STALE_MS
        // A rough fix right after good ones is most likely a Wi-Fi or cell estimate: skip it.
        if (!stale && accuracy > ROUGH_M && accuracy > shownAccuracy * 2) return null

        val moved = distanceMeters(last.lat, last.lon, fix.lat, fix.lon).toFloat()
        // Within its own error the fix says nothing new: the walker has not really moved.
        if (!stale && moved < maxOf(MIN_MOVE_M, accuracy * 0.6f)) return null

        // Blend: a precise fix moves the marker most of the way, a rough one only a little.
        val weight = (BLEND_AT_M / (BLEND_AT_M + accuracy)).coerceIn(0.25f, 0.9f).let { if (stale) 1f else it }
        val blended = LatLon(last.lat + (fix.lat - last.lat) * weight, last.lon + (fix.lon - last.lon) * weight)
        return take(blended, accuracy, timeMs)
    }

    private fun take(p: LatLon, accuracy: Float, timeMs: Long): LatLon {
        shown = p
        shownAccuracy = accuracy
        lastAcceptedAt = timeMs
        return p
    }

    private companion object {
        /** Worse than this is a rough (network) fix. */
        const val ROUGH_M = 30f
        /** Never move for less than this. */
        const val MIN_MOVE_M = 3f
        /** A fix with this error moves the marker halfway. */
        const val BLEND_AT_M = 8f
        /** After this long without an accepted fix, take whatever comes (e.g. coming out of a building). */
        const val STALE_MS = 8_000L
    }
}
