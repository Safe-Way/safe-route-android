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
 * How far (meters) the line is drawn towards the pavement when the server gives a side but not its own offsetM
 * (servers before the 11/11 proposal). With offsetM, that is used: it knows the road's width and one-way lanes.
 */
const val SIDEWALK_DRAW_OFFSET_M = 6.0

/** An offsetM above this is taken as a mistake and not used (the widest roads here are about 50 m across). */
const val SIDEWALK_MAX_OFFSET_M = 30.0

private const val M_PER_DEG_LAT = 111_320.0

/**
 * Which side a "both" stretch is drawn on (w15, 11/11): the server says a two-way city road has pavements on both
 * sides when OSM does not say; the walker's own side is used on the stretch they are on, and the right-hand side
 * everywhere else (before guidance starts, or stretches further on).
 */
fun interface BothSide {
    fun of(stretch: Sidewalk): SidewalkSide

    companion object {
        val RIGHT = BothSide { SidewalkSide.RIGHT }
    }
}

/** Where path point [i] is drawn: moved towards the pavement when the stretch it is on has one on one side. */
fun sidewalkOffsetAt(path: List<LatLon>, sidewalks: List<Sidewalk>, i: Int, both: BothSide = BothSide.RIGHT): LatLon {
    if (path.size < 2 || i !in path.indices) return LatLon(0.0, 0.0)
    val stretch = sidewalks.firstOrNull { i >= it.from && i <= it.to } ?: return LatLon(0.0, 0.0)
    val distance = stretch.offsetM?.takeIf { it <= SIDEWALK_MAX_OFFSET_M } ?: SIDEWALK_DRAW_OFFSET_M
    // "both" only comes with the server's own offsetM: without it (older servers) it is not moved, as before.
    val side = if (stretch.side == SidewalkSide.BOTH) (if (stretch.offsetM != null) both.of(stretch) else null) else stretch.side
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
    val ox = -dy / len * distance * sign
    val oy = dx / len * distance * sign
    return LatLon(oy / M_PER_DEG_LAT, ox / kx)
}

/** The route's line as drawn on the map, moved onto the pavement side where that is known. */
fun drawnPath(route: RouteOption, both: BothSide = BothSide.RIGHT): List<LatLon> =
    if (route.sidewalks.isEmpty()) route.path
    else route.path.mapIndexed { i, p -> sidewalkOffsetAt(route.path, route.sidewalks, i, both).let { LatLon(p.lat + it.lat, p.lon + it.lon) } }

/** [here] on the route near path point [i], moved the same way as the line there, so the marker sits on it. */
fun drawnPoint(route: RouteOption, here: LatLon, i: Int, both: BothSide = BothSide.RIGHT): LatLon =
    sidewalkOffsetAt(route.path, route.sidewalks, i, both).let { LatLon(here.lat + it.lat, here.lon + it.lon) }

/** Meters the walker must be off the line before their side counts, so GPS noise on the line does not flip it. */
const val WALKER_SIDE_MIN_M = 3.0

/**
 * Which side of the route line [fix] is on, at path point [i] (the segment from it to the next); null when too
 * close to the line to tell.
 */
fun walkerSide(path: List<LatLon>, fix: LatLon, i: Int): SidewalkSide? {
    if (path.size < 2) return null
    val a = path[i.coerceIn(0, path.lastIndex - 1)]
    val b = path[i.coerceIn(0, path.lastIndex - 1) + 1]
    val kx = M_PER_DEG_LAT * cos(Math.toRadians(a.lat))
    val bx = (b.lon - a.lon) * kx
    val by = (b.lat - a.lat) * M_PER_DEG_LAT
    val px = (fix.lon - a.lon) * kx
    val py = (fix.lat - a.lat) * M_PER_DEG_LAT
    val len = hypot(bx, by)
    if (len == 0.0) return null
    // Cross product: positive is left of the way of travel.
    val off = (bx * py - by * px) / len
    return when {
        off >= WALKER_SIDE_MIN_M -> SidewalkSide.LEFT
        off <= -WALKER_SIDE_MIN_M -> SidewalkSide.RIGHT
        else -> null
    }
}
