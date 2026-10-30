package com.deungbul.anshimgil.data

/** A point on the ground. */
data class LatLon(val lat: Double, val lon: Double)

enum class RouteType { SHORTEST, SAFE }

data class RouteOption(
    val type: RouteType,
    val distanceM: Int,
    val durationMin: Int,
    val path: List<LatLon>,
)

/** A detected situation such as night or rain; [on] is whether routing used it. */
data class Condition(val key: String, val label: String, val on: Boolean)

/** One row of "shortest → safe", e.g. CCTV 3대 → 9대. */
data class CompareItem(val label: String, val shortest: Int, val safe: Int, val unit: String)

data class Explanation(val summary: String, val items: List<CompareItem>)

data class DataQuality(val ok: Boolean, val message: String?)

/**
 * `/api/route` result. Top-level names follow requirements 8.3
 * (conditions, routes, explanation, facilities, dataQuality); inner fields are provisional
 * until the server shares the final shape.
 */
data class RouteResult(
    val conditions: List<Condition>,
    val routes: List<RouteOption>,
    val explanation: Explanation,
    val facilities: List<Facility>,
    val dataQuality: DataQuality,
) {
    fun route(type: RouteType): RouteOption? = routes.firstOrNull { it.type == type }
}

data class RouteRequest(
    val origin: LatLon,
    val destination: LatLon,
    val mode: UserMode,
    /** Condition keys the user switched off (FR-03). */
    val conditionsOff: Set<String> = emptySet(),
)

/** Walking speed per mode in meters per minute (requirements 0.2; disabled is a placeholder). */
val UserMode.walkingSpeedMPerMin: Int
    get() = when (this) {
        UserMode.CHILD -> 70
        UserMode.SENIOR -> 60
        UserMode.DISABLED -> 50
        UserMode.GENERAL -> 80
    }
