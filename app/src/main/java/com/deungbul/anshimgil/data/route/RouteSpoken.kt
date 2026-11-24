package com.deungbul.anshimgil.data.route

import com.deungbul.anshimgil.data.assistant.summary

/*
 * 길찾기 결과를 소리로 (사용성 점검표 17번, 11/9): with 음성 안내 on, the route screen reads out what it
 * recommends, so a walker who does not look at the screen still knows what will happen when they start.
 */

/** "825미터", "1.2킬로미터". */
fun spokenDistance(meters: Int): String =
    if (meters < 1000) "${meters}미터" else "${"%.1f".format(java.util.Locale.US, meters / 1000.0).removeSuffix(".0")}킬로미터"

/** What the route screen says for the [selected] walking route, e.g. "안전한 길로 14분, 825미터예요. …". */
fun routeSpoken(result: RouteResult, selected: RouteType): String? {
    val route = result.route(selected) ?: return null
    val name = if (selected == RouteType.SAFE) "안전한 길" else "가장 빠른 길"
    val why = result.explanation.summary.takeIf { it.isNotBlank() && selected == RouteType.SAFE }?.let { " $it" }.orEmpty()
    return "${name}로 ${route.durationMin}분, ${spokenDistance(route.distanceM)}예요.$why 출발하려면 화면 아래 안내 시작을 눌러 주세요."
}

/** What the route screen says for a transit route, e.g. "대중교통으로 28분이에요. 37번 버스를 타요. …". */
fun transitSpoken(route: TransitRoute): String {
    val rides = route.legs.filter { it.type != LegType.WALK }.joinToString(" ") { "${rideTitle(it)}. ${rideDetail(it)}." }
    return "대중교통으로 ${route.totalMin}분이에요. $rides 출발하려면 화면 아래 안내 시작을 눌러 주세요."
}
