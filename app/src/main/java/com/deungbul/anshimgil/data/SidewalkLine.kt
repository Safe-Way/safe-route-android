package com.deungbul.anshimgil.data

import kotlin.math.cos
import kotlin.math.hypot

/*
 * 길 선을 인도 쪽으로 (11/11): the route comes as road centre lines, so beside a big road the line ran down the middle
 * of the carriageway. Where the server says which side the pavement is on (OSM sidewalk), the line and the walker's
 * marker are drawn that far towards it. Only the drawing moves: guidance, distances and off-route checks still use
 * the route as it came.
 */

/**
 * How far (meters) the line is drawn towards the pavement. 6 → 9 (11/11 phone check): on 제봉로 at 6 m the line still
 * sat on the edge of the carriageway. OSM gives a pavement side mostly on wide roads, so a narrow street seldom gets it.
 */
const val SIDEWALK_DRAW_OFFSET_M = 9.0

private const val M_PER_DEG_LAT = 111_320.0

/** Where path point [i] is drawn: moved towards the pavement when the stretch it is on has one on one side. */
fun sidewalkOffsetAt(path: List<LatLon>, sidewalks: List<Sidewalk>, i: Int): LatLon {
    if (path.size < 2 || i !in path.indices) return LatLon(0.0, 0.0)
    val side = sidewalks.firstOrNull { i >= it.from && i <= it.to }?.side ?: return LatLon(0.0, 0.0)
    val sign = when (side) {
        SidewalkSide.LEFT -> 1.0
        SidewalkSide.RIGHT -> -1.0
        else -> return LatLon(0.0, 0.0)
    }
    val p = path[i]
    val kx = M_PER_DEG_LAT * cos(Math.toRadians(p.lat))
    // Direction of travel here: the segments before and after, averaged at a bend.
    var dx = 0.0
    var dy = 0.0
    fun add(a: LatLon, b: LatLon) {
        val x = (b.lon - a.lon) * kx
        val y = (b.lat - a.lat) * M_PER_DEG_LAT
        val len = hypot(x, y)
        if (len > 0) { dx += x / len; dy += y / len }
    }
    if (i > 0) add(path[i - 1], p)
    if (i < path.lastIndex) add(p, path[i + 1])
    val len = hypot(dx, dy)
    if (len == 0.0) return LatLon(0.0, 0.0)
    // Left of the way of travel is (-dy, dx).
    val ox = -dy / len * SIDEWALK_DRAW_OFFSET_M * sign
    val oy = dx / len * SIDEWALK_DRAW_OFFSET_M * sign
    return LatLon(oy / M_PER_DEG_LAT, ox / kx)
}

/** The route's line as drawn on the map, moved onto the pavement side where that is known. */
fun drawnPath(route: RouteOption): List<LatLon> =
    if (route.sidewalks.isEmpty()) route.path
    else route.path.mapIndexed { i, p -> sidewalkOffsetAt(route.path, route.sidewalks, i).let { LatLon(p.lat + it.lat, p.lon + it.lon) } }

/** [here] on the route near path point [i], moved the same way as the line there, so the marker sits on it. */
fun drawnPoint(route: RouteOption, here: LatLon, i: Int): LatLon =
    sidewalkOffsetAt(route.path, route.sidewalks, i).let { LatLon(here.lat + it.lat, here.lon + it.lon) }

/** Turn signs moved the same way as the line at the path point nearest each, so they stay on it. */
fun drawnTurnMarks(route: RouteOption, marks: List<TurnMark>): List<TurnMark> {
    if (route.sidewalks.isEmpty() || route.path.isEmpty()) return marks
    return marks.map { m ->
        val i = route.path.indices.minByOrNull { metersBetween(route.path[it], m.point) } ?: return@map m
        m.copy(point = drawnPoint(route, m.point, i))
    }
}
