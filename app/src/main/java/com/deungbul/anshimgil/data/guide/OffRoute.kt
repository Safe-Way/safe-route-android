package com.deungbul.anshimgil.data.guide

import com.deungbul.anshimgil.data.route.LatLon

/*
 * When the walker counts as off the route (m25, 11/11). The route line runs down the middle of the road, so on a
 * wide road or at a crossing a walker on the pavement was already past 25 m and got a new route they did not need.
 */

/** Off the route farther than this (meters) away from crossings. */
const val OFF_ROUTE_M = 25

/** At a turn or crossing the road is wide and the walker may be on the far corner: this much (meters). */
const val OFF_ROUTE_AT_CROSSING_M = 40

/** Within this distance (meters) of a turn on the route counts as at a crossing. */
const val TURN_NEAR_M = 30.0

/** Fixes in a row past the limit before the walker counts as off the route; one jumpy fix is not leaving. */
const val OFF_ROUTE_FIXES = 2

/** How far off the route is still on it, here. */
fun offRouteLimitM(atCrossing: Boolean): Int = if (atCrossing) OFF_ROUTE_AT_CROSSING_M else OFF_ROUTE_M

/** Whether [here] is near one of the route's turns. */
fun atCrossing(here: LatLon, turns: List<TurnMark>): Boolean = turns.any { metersBetween(it.point, here) <= TURN_NEAR_M }

/** Counts fixes past the limit in a row. */
class OffRouteCounter {
    var inRow = 0
        private set

    /** Feeds one fix [offM] off the route with [limitM] allowed here; true once the walker counts as off it. */
    fun update(offM: Int, limitM: Int): Boolean {
        inRow = if (offM > limitM) inRow + 1 else 0
        return inRow >= OFF_ROUTE_FIXES
    }
}
