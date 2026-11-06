package com.deungbul.anshimgil.data

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class Turn { LEFT, RIGHT, STRAIGHT, ARRIVE }

/** What to tell the walker right now. Distances are in meters along the route. */
data class GuidanceState(
    /** Where the walker is drawn: their position snapped onto the route. */
    val snapped: LatLon,
    /** Distance walked along the route so far. */
    val progressM: Double,
    val remainingM: Int,
    val nextTurn: Turn,
    val metersToNextTurn: Int,
    /** Meters from the route; large values mean the walker left it. */
    val offRouteM: Int,
    val nearestHelp: HelpPoint?,
) {
    val arrived: Boolean get() = remainingM <= ARRIVE_RADIUS_M

    companion object {
        const val ARRIVE_RADIUS_M = 15
    }
}

data class HelpPoint(val kind: FacilityKind, val meters: Int, val side: Turn)

/** A sharp turn on the route at [point]; [left] says which way it goes. */
data class TurnMark(val point: LatLon, val left: Boolean)

/** A place to get help further along the route: [aheadM] along the route from here, [offM] off it. */
data class HelpAhead(val kind: FacilityKind, val aheadM: Int, val side: Turn, val offM: Int)

/** Help places this far from the route still count as along it. */
private const val HELP_LIST_OFF_M = 60.0

/** A help place a few meters behind still shows, since GPS can put the walker slightly past it. */
private const val PASSED_M = 5.0

/** A change of heading at least this large counts as a turn. */
private const val TURN_DEGREES = 30.0

/** Only sharp turns, close to a right angle, get an arrow drawn on the map. */
private const val TURN_ARROW_DEGREES = 65.0

/** Turn signs closer together than this along the route are merged into the first. */
private const val TURN_MARK_GAP_M = 25.0

/**
 * Turn-by-turn guidance along a fixed route (issue #7, FR-29·30·34).
 * Uses a flat-earth approximation, which is accurate enough over a few kilometers.
 */
class Guidance(private val path: List<LatLon>, private val facilities: List<Facility>) {
    private val origin = path.first()
    private val points = path.map { toXY(it) }
    private val legLengths = points.zipWithNext { a, b -> dist(a, b) }
    private val cumulative = legLengths.runningFold(0.0) { acc, l -> acc + l }
    val totalM: Double = cumulative.last()

    fun update(position: LatLon): GuidanceState {
        val p = toXY(position)
        var best = Double.MAX_VALUE
        var bestLeg = 0
        var bestT = 0.0
        for (i in legLengths.indices) {
            val (t, d) = projectOnLeg(p, i)
            if (d < best) {
                best = d; bestLeg = i; bestT = t
            }
        }
        val progress = cumulative[bestLeg] + legLengths[bestLeg] * bestT
        return stateAt(progress, bestLeg, best)
    }

    /**
     * Sharp turns (about a right angle) to mark on the map. Turns closer than [TURN_MARK_GAP_M] to the
     * previous mark are left out, so a roundabout or a jog gets one sign instead of a cluster.
     */
    val turnMarks: List<TurnMark> by lazy {
        val marks = mutableListOf<TurnMark>()
        var lastAt = Double.NEGATIVE_INFINITY
        for (v in 1 until points.lastIndex) {
            val change = headingChange(points[v - 1], points[v], points[v + 1])
            if (abs(change) < TURN_ARROW_DEGREES || cumulative[v] - lastAt < TURN_MARK_GAP_M) continue
            marks += TurnMark(toLatLon(points[v]), left = change > 0)
            lastAt = cumulative[v]
        }
        marks
    }

    /**
     * Places to get help along the rest of the route (도움 지점 목록): those within [maxOffM] of the route
     * and not already passed, nearest first, each with how far ahead it is and on which side.
     */
    fun helpAhead(progressM: Double, maxOffM: Double = HELP_LIST_OFF_M): List<HelpAhead> =
        facilities.filter { it.kind in HELP_KINDS }.mapNotNull { f ->
            val p = toXY(LatLon(f.lat, f.lon))
            var off = Double.MAX_VALUE
            var leg = 0
            var t = 0.0
            for (i in legLengths.indices) {
                val (tt, d) = projectOnLeg(p, i)
                if (d < off) { off = d; leg = i; t = tt }
            }
            val along = cumulative[leg] + legLengths[leg] * t
            if (off > maxOffM || along < progressM - PASSED_M) return@mapNotNull null
            val (ax, ay) = points[leg]
            val (bx, by) = points[leg + 1]
            val cross = (bx - ax) * (p.second - ay) - (by - ay) * (p.first - ax)
            val side = if (off < 3) Turn.STRAIGHT else if (cross > 0) Turn.LEFT else Turn.RIGHT
            HelpAhead(f.kind, (along - progressM).coerceAtLeast(0.0).roundToInt(), side, off.roundToInt())
        }.sortedBy { it.aheadM }

    /** The index of the path point the walker last passed at [progressM]. */
    fun pointIndexAt(progressM: Double): Int =
        cumulative.indexOfLast { it <= progressM }.coerceIn(0, legLengths.lastIndex)

    /** Direction of travel at [progressM], looking a few meters ahead, in degrees clockwise from north. */
    fun travelBearing(progressM: Double): Float {
        val (ax, ay) = pointAt(progressM.coerceIn(0.0, totalM))
        val (bx, by) = pointAt((progressM + 10).coerceIn(0.0, totalM))
        if (ax == bx && ay == by) return 0f
        return ((Math.toDegrees(kotlin.math.atan2(bx - ax, by - ay)) + 360) % 360).toFloat()
    }

    /** Meters from [position] to the nearest point of the route. */
    fun distanceToRoute(position: LatLon): Double {
        val p = toXY(position)
        return legLengths.indices.minOf { projectOnLeg(p, it).second }
    }

    /**
     * Where the rest of the route (from [fromM] on) comes closest to [center], as meters along the route,
     * when it passes within [radiusM] of it; null when the rest of the route keeps clear.
     */
    fun passesWithin(center: LatLon, radiusM: Double, fromM: Double): Double? {
        val p = toXY(center)
        var best: Pair<Double, Double>? = null // (distance, progress)
        for (i in legLengths.indices) {
            if (cumulative[i + 1] <= fromM) continue
            val (t, projected) = projectOnLeg(p, i)
            var at = cumulative[i] + legLengths[i] * t
            var d = projected
            // The nearest point is already behind the walker: what is left of this leg starts where they are.
            if (at < fromM) {
                at = fromM
                d = dist(p, pointAt(fromM))
            }
            if (d <= radiusM && (best == null || d < best.first)) best = d to at
        }
        return best?.second
    }

    /** Guidance at a given distance along the route; used for the demo walk without GPS. */
    fun atProgress(progressM: Double): GuidanceState {
        val progress = progressM.coerceIn(0.0, totalM)
        val leg = cumulative.indexOfLast { it <= progress }.coerceIn(0, legLengths.lastIndex)
        return stateAt(progress, leg, 0.0)
    }

    private fun stateAt(progress: Double, leg: Int, offRoute: Double): GuidanceState {
        val here = pointAt(progress)
        val remaining = (totalM - progress).coerceAtLeast(0.0)

        // The next vertex where the heading changes by 30° or more is the next turn.
        var turn = Turn.ARRIVE
        var toTurn = remaining
        for (v in (leg + 1) until points.lastIndex) {
            val change = headingChange(points[v - 1], points[v], points[v + 1])
            if (abs(change) >= TURN_DEGREES) {
                turn = if (change > 0) Turn.LEFT else Turn.RIGHT
                toTurn = cumulative[v] - progress
                break
            }
        }
        if (turn == Turn.ARRIVE && remaining > 300) turn = Turn.STRAIGHT

        return GuidanceState(
            snapped = toLatLon(here),
            progressM = progress,
            remainingM = remaining.roundToInt(),
            nextTurn = turn,
            metersToNextTurn = toTurn.coerceAtLeast(0.0).roundToInt(),
            offRouteM = offRoute.roundToInt(),
            nearestHelp = nearestHelp(here, leg),
        )
    }

    private fun nearestHelp(here: Pair<Double, Double>, leg: Int): HelpPoint? {
        val heading = points[leg + 1].let { (bx, by) -> (bx - points[leg].first) to (by - points[leg].second) }
        return facilities
            .filter { it.kind in HELP_KINDS }
            .map { f -> f to toXY(LatLon(f.lat, f.lon)) }
            .map { (f, xy) -> Triple(f, xy, dist(here, xy)) }
            .filter { it.third <= 200 }
            .minByOrNull { it.third }
            ?.let { (f, xy, d) ->
                val cross = heading.first * (xy.second - here.second) - heading.second * (xy.first - here.first)
                HelpPoint(f.kind, d.roundToInt(), if (abs(cross) < 1e-6) Turn.STRAIGHT else if (cross > 0) Turn.LEFT else Turn.RIGHT)
            }
    }

    private fun projectOnLeg(p: Pair<Double, Double>, i: Int): Pair<Double, Double> {
        val (ax, ay) = points[i]
        val (bx, by) = points[i + 1]
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((p.first - ax) * dx + (p.second - ay) * dy) / len2).coerceIn(0.0, 1.0)
        return t to dist(p, (ax + dx * t) to (ay + dy * t))
    }

    private fun pointAt(progress: Double): Pair<Double, Double> {
        val leg = cumulative.indexOfLast { it <= progress }.coerceIn(0, legLengths.lastIndex)
        val t = if (legLengths[leg] == 0.0) 0.0 else ((progress - cumulative[leg]) / legLengths[leg]).coerceIn(0.0, 1.0)
        val (ax, ay) = points[leg]
        val (bx, by) = points[leg + 1]
        return (ax + (bx - ax) * t) to (ay + (by - ay) * t)
    }

    /** Signed heading change at [b] in degrees; positive turns left. */
    private fun headingChange(a: Pair<Double, Double>, b: Pair<Double, Double>, c: Pair<Double, Double>): Double {
        val h1 = atan2(b.second - a.second, b.first - a.first)
        val h2 = atan2(c.second - b.second, c.first - b.first)
        var d = Math.toDegrees(h2 - h1)
        while (d > 180) d -= 360
        while (d < -180) d += 360
        return d
    }

    private fun toXY(p: LatLon): Pair<Double, Double> {
        val x = Math.toRadians(p.lon - origin.lon) * EARTH_R * cos(Math.toRadians(origin.lat))
        val y = Math.toRadians(p.lat - origin.lat) * EARTH_R
        return x to y
    }

    private fun toLatLon(xy: Pair<Double, Double>): LatLon = LatLon(
        lat = origin.lat + Math.toDegrees(xy.second / EARTH_R),
        lon = origin.lon + Math.toDegrees(xy.first / (EARTH_R * cos(Math.toRadians(origin.lat)))),
    )

    private fun dist(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dx = a.first - b.first
        val dy = a.second - b.second
        return sqrt(dx * dx + dy * dy)
    }

    private companion object {
        const val EARTH_R = 6_371_000.0
    }
}
