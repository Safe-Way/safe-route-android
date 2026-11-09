package com.deungbul.anshimgil.data

/*
 * 경로가 지나는 행사·돌발상황 (팀 할 일 m08, 11/9): the server's routes[].events, shown on the route screen
 * so the walker knows before starting ("앞에 금남로 행사로 붐벼요").
 */

/**
 * One event on a route. [fromM]..[toM]: meters along the route inside its reach. [avoid]: this mode keeps away from
 * it (so it is a caution, the way round was too long); [near]: "start", "end" or "both" when the start or the
 * destination is inside it.
 */
data class RouteEvent(
    val kind: String,
    val title: String,
    val place: String?,
    val message: String?,
    val endsAt: String?,
    val fromM: Int,
    val toM: Int,
    val avoid: Boolean,
    val near: String?,
)

/** "~23:59까지" from the server's "2026-11-07 23:59"; null when not known. */
private fun until(endsAt: String?): String? =
    endsAt?.trim()?.takeIf { it.length >= 16 }?.substring(11, 16)?.let { "$it 까지" }

/** "100m", "1.2km" for a place along the route. */
private fun along(m: Int): String = if (m < 1000) "${m}m" else "${"%.1f".format(java.util.Locale.US, m / 1000.0)}km"

/** The line on the route screen for [e]. */
fun eventLine(e: RouteEvent): String {
    val name = "${e.kind} '${e.title}'"
    // A festival is held there; works or an accident just are there.
    val there = if (e.kind == "행사") "열리고 있어요" else "있어요"
    val where = when (e.near) {
        "start" -> "출발하는 곳 근처에 ${name}이(가) $there."
        "end" -> "도착하는 곳 근처에 ${name}이(가) $there."
        "both" -> "출발·도착하는 곳 근처에 ${name}이(가) $there."
        else -> "${along(e.fromM)}쯤 가면 ${name} 근처를 지나요."
    }
    val caution = if (e.avoid) " 붐비거나 막힐 수 있으니 조심하세요." else ""
    val time = until(e.endsAt)?.let { " ($it)" }.orEmpty()
    return "$where$caution$time"
}
