package com.deungbul.anshimgil.data

import kotlin.math.cos
import kotlin.math.roundToInt

/** A point on the ground. */
data class LatLon(val lat: Double, val lon: Double)

enum class RouteType { SHORTEST, SAFE }

data class RouteOption(
    val type: RouteType,
    val distanceM: Int,
    val durationMin: Int,
    val path: List<LatLon>,
    /** Facilities along the route by group: light, cctv, bell, help, rest (shelters and toilets open now), school. */
    val counts: Map<String, Int> = emptyMap(),
    /**
     * Meters walked within reach of each thing this mode and time weighs, e.g. child by day
     * {"school": 1215, "help": 1392}; empty when nothing is weighed (adult or wheelchair by day).
     */
    val covered: Map<String, Int> = emptyMap(),
    /** Which side the pavement is on along stretches beside a road (OSM sidewalk tag). */
    val sidewalks: List<Sidewalk> = emptyList(),
    /**
     * Where the route crosses a road (the middle of each crossing the server lists); null when the server did not
     * say. Guidance uses it to tell crossing over from the pavement jogging aside a few meters.
     */
    val crossings: List<LatLon>? = null,
    /** Events and incidents the route passes (routes[].events, 팀 할 일 m08). */
    val events: List<RouteEvent> = emptyList(),
    /** Places to stop at on the way, in order (routes[].stops, 들를 곳). */
    val stops: List<RouteStop> = emptyList(),
)

enum class SidewalkSide { LEFT, RIGHT, BOTH, NONE, UNKNOWN }

/**
 * Path points [from]..[to] (indices into the route's path) run beside a road with the pavement on [side].
 * [offsetM] is the server's distance from the line to the middle of the pavement (11/11 proposal: half the width,
 * or lanes × 3.25 m ÷ 2, or a default by road kind, plus 1.5 m; for a one-way road the side is its outer side);
 * null when the server does not send it. [highway], [lanes], [oneway] and [widthM] are the road's own values, kept
 * for later; null when unknown.
 */
data class Sidewalk(
    val from: Int,
    val to: Int,
    val side: SidewalkSide,
    val offsetM: Double? = null,
    val highway: String? = null,
    val lanes: Int? = null,
    val oneway: Boolean? = null,
    val widthM: Double? = null,
)

/**
 * What to tell the walker about the pavement where they are, given the path point they are past;
 * null when nothing useful is known (not beside a road, or the side is not mapped).
 */
fun sidewalkHint(sidewalks: List<Sidewalk>, pointIndex: Int): String? =
    when (sidewalks.firstOrNull { pointIndex >= it.from && pointIndex < it.to }?.side) {
        SidewalkSide.LEFT -> "왼쪽 인도로 걸어요"
        SidewalkSide.RIGHT -> "오른쪽 인도로 걸어요"
        SidewalkSide.BOTH -> "양쪽에 인도가 있어요"
        SidewalkSide.NONE -> "인도가 없어요. 차 조심하세요"
        SidewalkSide.UNKNOWN, null -> null
    }

/** A detected situation such as night or rain; [on] is whether routing used it. */
data class Condition(val key: String, val label: String, val on: Boolean)

/** One row of "shortest → safe", e.g. CCTV 3대 → 9대. [key] is the server's count key ("light", "cctv", …). */
data class CompareItem(val label: String, val shortest: Int, val safe: Int, val unit: String, val key: String = "")

/**
 * [summary] is the server's one-line reason ("24m(1분) 더 돌지만 쉼 곳 +1곳"); [notes] are warnings and notices
 * shown under it, such as stairs a senior cannot avoid or an event on the way.
 */
data class Explanation(val summary: String, val items: List<CompareItem>, val notes: List<RouteNote> = emptyList())

/** A warning ([warn] true) or a notice about the route, in a plain sentence. [kind] is stairs, event, unpaved, surface or snap. */
data class RouteNote(val warn: Boolean, val kind: String, val text: String)

data class DataQuality(val ok: Boolean, val message: String?)

/**
 * `/api/route` result (requirements 8.3). When the shortest route is also the safe one,
 * or the mode has no daytime rule, [routes] holds only the safe route.
 */
data class RouteResult(
    val conditions: List<Condition>,
    val routes: List<RouteOption>,
    val explanation: Explanation,
    val facilities: List<Facility>,
    val dataQuality: DataQuality,
    /** Weather and air shown for information, e.g. "비", "12°C", "미세먼지 나쁨"; empty when the server could not get them. */
    val weather: List<String> = emptyList(),
    /** Today's sunset at the start ("17:34"), when the server gives it. */
    val sunset: java.time.LocalTime? = null,
    /** Whether it is raining now (only when the server could get the weather). */
    val raining: Boolean = false,
) {
    fun route(type: RouteType): RouteOption? = routes.firstOrNull { it.type == type }

    /** Facilities along the [type] route, leaving out those only on the other one. */
    fun facilitiesOn(type: RouteType): List<Facility> = facilities.filter { it.route == null || it.route == type }
}

data class RouteRequest(
    val origin: LatLon,
    val destination: LatLon,
    val mode: UserMode,
    /** Condition keys the user switched off (FR-03). */
    val conditionsOff: Set<String> = emptySet(),
    /** Condition keys the user switched on even though they were not detected, e.g. night in daytime. */
    val conditionsOn: Set<String> = emptySet(),
    /** Places to stop at on the way (들를 곳), at most [MAX_WALK_STOPS] on a walk. */
    val stops: List<StopPlace> = emptyList(),
)

/** The server's `night` value: what the user chose, otherwise judged from sunset. */
val RouteRequest.night: String
    get() = when ("night") {
        in conditionsOff -> "off"
        in conditionsOn -> "on"
        else -> "auto"
    }

/** Walking speed per mode in meters per minute (requirements 0.2; disabled is a placeholder). */
val UserMode.walkingSpeedMPerMin: Int
    get() = when (this) {
        UserMode.CHILD -> 70
        UserMode.SENIOR -> 60
        UserMode.DISABLED -> 50
        UserMode.GENERAL -> 80
    }

/** How long before sunset the evening counts as night for routing. */
const val EVENING_BEFORE_SUNSET_MIN = 60L

/**
 * Whether the app should switch night routing on by itself although the server did not: in the evening
 * (from an hour before sunset, for every mode) or when it rains, since both make streets darker.
 * Null when night routing is already on or nothing calls for it; otherwise the reason to show.
 */
fun autoNightReason(result: RouteResult, now: java.time.LocalTime): String? {
    if (result.conditions.any { it.key == "night" && it.on }) return null
    return when {
        result.raining -> "비가 와서"
        result.sunset != null && !now.isBefore(result.sunset.minusMinutes(EVENING_BEFORE_SUNSET_MIN)) -> "저녁이라"
        else -> null
    }
}


/** How near the start or the end the route must already pass for the loop after it to be cut. */
const val END_DETOUR_NEAR_M = 15.0

/** The loop must be at least this much longer than going straight across, or it is left alone. */
private const val END_DETOUR_MIN_SAVING_M = 10.0

/**
 * The route without the loops at either end: the server joins the start and the end to the nearest road,
 * which for a bus stop can be the road beside the pavement the walker is on, so the line went 28 m past
 * the stop and came back up the other side (11/6). When the route already passes within
 * [END_DETOUR_NEAR_M] of the end (or the start), it goes straight there from that point instead.
 * Distance and time are shortened by what was cut; sidewalk stretches are moved to the new point numbers.
 */
fun RouteOption.withoutEndDetours(): RouteOption {
    if (path.size < 3) return this
    var points = path
    var sidewalks = sidewalks
    var cut = 0.0

    // The end: the first segment that comes near the end with a long way still to go.
    run {
        val end = points.last()
        val after = suffixLengths(points)
        for (j in 0 until points.size - 2) {
            val c = closestOnSegment(end, points[j], points[j + 1])
            val straight = metersBetween(c, end)
            val remaining = metersBetween(points[j], c).let { after[j] - it }
            if (straight <= END_DETOUR_NEAR_M && remaining > straight + END_DETOUR_MIN_SAVING_M) {
                cut += remaining - straight
                points = points.take(j + 1) + listOf(c, end)
                val last = points.lastIndex
                sidewalks = sidewalks.mapNotNull { w -> if (w.from >= last) null else w.copy(to = minOf(w.to, last)) }
                break
            }
        }
    }
    // The start: the last segment that comes near the start, after a walk away from it.
    run {
        val start = points.first()
        val before = prefixLengths(points)
        for (j in points.size - 2 downTo 1) {
            val c = closestOnSegment(start, points[j], points[j + 1])
            val straight = metersBetween(start, c)
            val walked = before[j] + metersBetween(points[j], c)
            if (straight <= END_DETOUR_NEAR_M && walked > straight + END_DETOUR_MIN_SAVING_M) {
                cut += walked - straight
                points = listOf(start, c) + points.drop(j + 1)
                val shift = j - 1 // old index k (> j) is now k - shift
                sidewalks = sidewalks.mapNotNull { w ->
                    val to = w.to - shift
                    if (to <= 1) null else w.copy(from = maxOf(1, w.from - shift), to = to)
                }
                break
            }
        }
    }
    if (cut <= 0.0) return this
    val meters = (distanceM - cut.roundToInt()).coerceAtLeast(1)
    val minutes = if (distanceM > 0) (durationMin * meters.toDouble() / distanceM).roundToInt().coerceAtLeast(1) else durationMin
    return copy(path = points, distanceM = meters, durationMin = minutes, sidewalks = sidewalks)
}

/** Meters from each point to the end of [points]. */
private fun suffixLengths(points: List<LatLon>): DoubleArray {
    val out = DoubleArray(points.size)
    for (i in points.size - 2 downTo 0) out[i] = out[i + 1] + metersBetween(points[i], points[i + 1])
    return out
}

/** Meters from the start of [points] to each point. */
private fun prefixLengths(points: List<LatLon>): DoubleArray {
    val out = DoubleArray(points.size)
    for (i in 1 until points.size) out[i] = out[i - 1] + metersBetween(points[i - 1], points[i])
    return out
}

/** The point of segment [a]–[b] nearest [p] (flat-earth, fine over a few hundred meters). */
private fun closestOnSegment(p: LatLon, a: LatLon, b: LatLon): LatLon {
    val k = cos(Math.toRadians(a.lat))
    val ax = a.lon * k; val ay = a.lat
    val bx = b.lon * k; val by = b.lat
    val px = p.lon * k; val py = p.lat
    val dx = bx - ax; val dy = by - ay
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
    return LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
}
