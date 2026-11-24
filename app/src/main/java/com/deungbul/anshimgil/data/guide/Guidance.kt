package com.deungbul.anshimgil.data.guide

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt
import com.deungbul.anshimgil.data.place.Facility
import com.deungbul.anshimgil.data.place.FacilityKind
import com.deungbul.anshimgil.data.place.HELP_KINDS
import com.deungbul.anshimgil.data.route.LatLon
import com.deungbul.anshimgil.data.route.RouteOption

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
/** A turn sign: where, which way, and [bearing] the way the walker comes into it (degrees clockwise from north). */
data class TurnMark(val point: LatLon, val left: Boolean, val bearing: Float = 0f)

/** A place to get help further along the route: [aheadM] along the route from here, [offM] off it. */
data class HelpAhead(val kind: FacilityKind, val aheadM: Int, val side: Turn, val offM: Int)

/** Help places this far from the route still count as along it. */
private const val HELP_LIST_OFF_M = 60.0

/** A help place a few meters behind still shows, since GPS can put the walker slightly past it. */
private const val PASSED_M = 5.0

/**
 * A change of heading at least this large counts as a turn (11/8: 30 → 50; compared with OSM's walking steps on
 * eight routes around 조선대, bends of 30–50° were almost all a road curving, not a turn to tell).
 */
private const val TURN_DEGREES = 45.0

/** A bend this sharp is looked at as part of a turn (a corner drawn as several small bends adds up). */
private const val BEND_DEGREES = 15.0

/**
 * The heading before and after a point is measured over this far, so a corner drawn as several short bends
 * counts as one turn, and a few meters of jitter in the line do not (11/8, compared with OSM's walking steps).
 */
private const val TURN_LOOK_M = 8.0

/** Bends this close together are one turn; when they cancel out (a step aside, then on) they are none… */
private const val TURN_CLUSTER_M = 8.0

/** …unless the way out lies this far to the side of the way in: that is crossing over, two turns. */
private const val SIDESTEP_M = 5.0

/**
 * When the server lists where the route crosses roads, a jog up to this far aside with no crossing there is the
 * pavement shifting (a wider kerb, a bus stop), not two turns (11/9: 3 of 4 such jogs on our server's routes
 * around 조선대 had no crossing, and the route engine did not count them as turns).
 */
private const val SIDESTEP_NO_CROSSING_M = 10.0

/** A crossing this close to a jog is where it crosses. */
private const val CROSSING_NEAR_M = 12.0

/** A crossing this far from the route still counts as on it (for [Guidance.crossingsAlongM]). */
private const val CROSSING_ON_ROUTE_M = 15.0

/** Where the walker is looked for first: from this far behind the last place on the route… */
private const val NEAR_BACK_M = 30.0

/** …to this far ahead of it; only when they are not near that stretch is the whole route searched. */
private const val NEAR_AHEAD_M = 80.0

/** Near the stretch walked means within this of it. */
private const val NEAR_OK_M = 40.0

/**
 * Going further ahead than a few seconds of walking costs this many meters of distance per meter skipped, so on a
 * hairpin the side being walked wins over the side coming back a few meters away.
 */
private const val SKIP_COST = 0.2
private const val EXPECTED_AHEAD_M = 15.0

/** Only sharp turns, close to a right angle, get an arrow drawn on the map. */
private const val TURN_ARROW_DEGREES = 65.0

/** Turn signs closer together than this along the route are merged into the first. */
private const val TURN_MARK_GAP_M = 25.0

/**
 * Turn-by-turn guidance along a fixed route (issue #7, FR-29·30·34).
 * Uses a flat-earth approximation, which is accurate enough over a few kilometers.
 */
class Guidance(
    private val path: List<LatLon>,
    private val facilities: List<Facility>,
    /** [RouteOption.crossings]: null when not known. */
    private val crossings: List<LatLon>? = null,
) {
    private val origin = path.first()
    private val points = path.map { toXY(it) }
    private val legLengths = points.zipWithNext { a, b -> dist(a, b) }
    private val cumulative = legLengths.runningFold(0.0) { acc, l -> acc + l }
    val totalM: Double = cumulative.last()

    /**
     * The guidance for [position]. With [nearM] (how far along the route the walker was), the stretch around it
     * is tried first, so a route that comes back near itself (a hairpin, a road walked out and back on the other
     * side) does not throw the walker to its other part (11/8: it jumped 64 m and turned the arrow round).
     */
    fun update(position: LatLon, nearM: Double? = null): GuidanceState {
        val p = toXY(position)
        fun nearest(legs: Iterable<Int>, cost: (Int, Double) -> Double = { _, _ -> 0.0 }): Triple<Int, Double, Double>? {
            var best: Triple<Int, Double, Double>? = null
            var bestScore = Double.MAX_VALUE
            for (i in legs) {
                val (t, d) = projectOnLeg(p, i)
                val score = d + cost(i, t)
                if (score < bestScore) { bestScore = score; best = Triple(i, t, d) }
            }
            return best
        }
        val local = nearM?.let { near ->
            nearest(legLengths.indices.filter { cumulative[it + 1] >= near - NEAR_BACK_M && cumulative[it] <= near + NEAR_AHEAD_M }) { i, t ->
                SKIP_COST * (cumulative[i] + legLengths[i] * t - near - EXPECTED_AHEAD_M).coerceAtLeast(0.0)
            }?.takeIf { it.third <= NEAR_OK_M }
        }
        val (leg, t, d) = local ?: nearest(legLengths.indices)!!
        return stateAt(cumulative[leg] + legLengths[leg] * t, leg, d)
    }

    /** A turn the walker is told about: where along the route, which way, and how sharp (degrees, signed). */
    private data class RouteTurn(val atM: Double, val turn: Turn, val degrees: Double, val vertex: Int)

    /**
     * The turns along the route (11/8, compared with OSM's walking steps on eight routes around 조선대):
     * - bends of [BEND_DEGREES] or more the same way within [TURN_CLUSTER_M] add up to one turn (a corner drawn
     *   as several small bends), told at the sharpest of them;
     * - a bend one way and back the other way within [TURN_CLUSTER_M] that moves the line less than [SIDESTEP_M]
     *   to the side is a step aside on the pavement, not told; crossing over to the other side is two turns;
 *   when the server lists the crossings, a jog under [SIDESTEP_NO_CROSSING_M] with no crossing there is a step aside too;
     * - what adds up to less than [TURN_DEGREES] is the road curving, not told.
     */
    private val turns: List<RouteTurn> by lazy {
        data class Group(val first: Int, val last: Int, val degrees: Double, val sharpest: Int)
        val bends = (1 until points.lastIndex).mapNotNull { v ->
            val change = headingChange(points[v - 1], points[v], points[v + 1])
            if (abs(change) >= BEND_DEGREES) v to change else null
        }
        val groups = mutableListOf<Group>()
        bends.forEach { (v, change) ->
            val g = groups.lastOrNull()
            if (g != null && cumulative[v] - cumulative[g.last] <= TURN_CLUSTER_M && (g.degrees > 0) == (change > 0)) {
                val sharpest = if (abs(change) > abs(headingChange(points[g.sharpest - 1], points[g.sharpest], points[g.sharpest + 1]))) v else g.sharpest
                groups[groups.lastIndex] = Group(g.first, v, g.degrees + change, sharpest)
            } else {
                groups += Group(v, v, change, v)
            }
        }
        // A step aside: one way, then straight back the other within a few meters, ending up beside the old line.
        val aside = mutableSetOf<Int>()
        val crossingXY = crossings?.map { toXY(it) }
        groups.zipWithNext().forEachIndexed { i, (a, b) ->
            // A bend already taken as half of a step aside is not paired again: a left, right, left would otherwise
            // lose its real left turn as well.
            if (i in aside) return@forEachIndexed
            if ((a.degrees > 0) == (b.degrees > 0) || cumulative[b.first] - cumulative[a.last] > TURN_CLUSTER_M) return@forEachIndexed
            if (abs(a.degrees + b.degrees) >= TURN_DEGREES) return@forEachIndexed
            val inA = points[maxOf(0, a.first - 1)]
            val inB = points[a.first]
            val out = points[minOf(points.lastIndex, b.last + 1)]
            val len = dist(inA, inB)
            val side = if (len < 0.5) 0.0 else abs((inB.first - inA.first) * (out.second - inA.second) - (inB.second - inA.second) * (out.first - inA.first)) / len
            val crossing = crossingXY?.any { c -> dist(c, points[a.first]) <= CROSSING_NEAR_M || dist(c, points[b.last]) <= CROSSING_NEAR_M }
            val limit = if (crossing == false) SIDESTEP_NO_CROSSING_M else SIDESTEP_M
            if (side < limit) { aside += i; aside += i + 1 }
        }
        groups.filterIndexed { i, g -> i !in aside && abs(g.degrees) >= TURN_DEGREES }
            .map { RouteTurn(cumulative[it.sharpest], if (it.degrees > 0) Turn.LEFT else Turn.RIGHT, it.degrees, it.sharpest) }
    }

    /**
     * Sharp turns (about a right angle) to mark on the map. Turns closer than [TURN_MARK_GAP_M] to the
     * previous mark are left out, so a roundabout or a jog gets one sign instead of a cluster.
     */
    val turnMarks: List<TurnMark> by lazy {
        val marks = mutableListOf<TurnMark>()
        var lastAt = Double.NEGATIVE_INFINITY
        // The same turns the walker is told about ([turns]), only the sharp ones: no sign at a step aside.
        for (t in turns) {
            if (abs(t.degrees) < TURN_ARROW_DEGREES || t.atM - lastAt < TURN_MARK_GAP_M) continue
            val (ax, ay) = pointAt((t.atM - TURN_LOOK_M).coerceAtLeast(0.0))
            val (bx, by) = points[t.vertex]
            val bearing = ((Math.toDegrees(atan2(bx - ax, by - ay)) + 360) % 360).toFloat()
            marks += TurnMark(toLatLon(points[t.vertex]), left = t.turn == Turn.LEFT, bearing = bearing)
            lastAt = t.atM
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

        // The next turn ahead ([turns]).
        var turn = Turn.ARRIVE
        var toTurn = remaining
        turns.firstOrNull { it.atM > progress }?.let {
            turn = it.turn
            toTurn = it.atM - progress
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

    /** How far along the route each road crossing is, in order; crossings not on the route are left out. */
    val crossingsAlongM: List<Double> by lazy {
        crossings.orEmpty().mapNotNull { c ->
            val p = toXY(c)
            var best = Double.MAX_VALUE
            var along = 0.0
            for (i in legLengths.indices) {
                val (t, d) = projectOnLeg(p, i)
                if (d < best) { best = d; along = cumulative[i] + legLengths[i] * t }
            }
            along.takeIf { best <= CROSSING_ON_ROUTE_M }
        }.sorted()
    }

    /**
     * How far along the route each of [places] is, taken in order: each is looked for only after the one before,
     * so a route that passes a later stop early (or comes back past an earlier one) keeps them in order.
     */
    fun alongInOrder(places: List<LatLon>): List<Double> {
        var from = 0.0
        return places.map { place ->
            val p = toXY(place)
            var best = Double.MAX_VALUE
            var along = from
            for (i in legLengths.indices) {
                if (cumulative[i + 1] < from) continue
                val (t, d) = projectOnLeg(p, i)
                val a = cumulative[i] + legLengths[i] * t
                if (a >= from && d < best) { best = d; along = a }
            }
            from = along
            along
        }
    }

    /** Where [point] lies against the route: meters along it to the nearest spot, and meters off it. */
    fun alongAndOff(point: LatLon): Pair<Double, Double> {
        val p = toXY(point)
        var best = Double.MAX_VALUE
        var along = 0.0
        for (i in legLengths.indices) {
            val (t, d) = projectOnLeg(p, i)
            if (d < best) { best = d; along = cumulative[i] + legLengths[i] * t }
        }
        return along to best
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
