package com.deungbul.anshimgil.data

/** A point on the ground. */
data class LatLon(val lat: Double, val lon: Double)

enum class RouteType { SHORTEST, SAFE }

data class RouteOption(
    val type: RouteType,
    val distanceM: Int,
    val durationMin: Int,
    val path: List<LatLon>,
    /** Facilities along the route by group: light, cctv, bell, help. */
    val counts: Map<String, Int> = emptyMap(),
    /** Which side the pavement is on along stretches beside a road (OSM sidewalk tag). */
    val sidewalks: List<Sidewalk> = emptyList(),
)

enum class SidewalkSide { LEFT, RIGHT, BOTH, NONE, UNKNOWN }

/** Path points [from]..[to] (indices into the route's path) run beside a road with the pavement on [side]. */
data class Sidewalk(val from: Int, val to: Int, val side: SidewalkSide)

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

data class Explanation(val summary: String, val items: List<CompareItem>)

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
