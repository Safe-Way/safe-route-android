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
)

/** A detected situation such as night or rain; [on] is whether routing used it. */
data class Condition(val key: String, val label: String, val on: Boolean)

/** One row of "shortest → safe", e.g. CCTV 3대 → 9대. */
data class CompareItem(val label: String, val shortest: Int, val safe: Int, val unit: String)

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
