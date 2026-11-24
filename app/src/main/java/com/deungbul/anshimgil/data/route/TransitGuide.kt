package com.deungbul.anshimgil.data.route

import com.deungbul.anshimgil.data.place.distanceMeters

/*
 * 대중교통 길 안내: the chosen transit route one part at a time. Walking parts use the usual walking
 * guidance to the stop; a ride shows which bus or train to take and where to get off, and warns when the
 * stop to get off at is near.
 */

/** How near the stop to get off at the walker must be for the "곧 내리세요" warning. */
const val ALIGHT_NEAR_M = 400

/** Walks shorter than this (crossing to the platform, say) are skipped. */
const val SKIP_WALK_M = 30

/** The parts worth guiding: tiny walks between rides are left out. */
fun guideLegs(route: TransitRoute): List<TransitLeg> =
    route.legs.filter { it.type != LegType.WALK || it.distanceM >= SKIP_WALK_M }

/** A short name for a part, for the step strip: 걷기, 21번, 1호선. */
fun legLabel(leg: TransitLeg): String = when (leg.type) {
    LegType.WALK -> "걷기"
    LegType.BUS -> leg.lines.firstOrNull()?.let { "${it}번" } ?: "버스"
    LegType.SUBWAY -> leg.lines.firstOrNull() ?: "지하철"
    LegType.STOP -> "들르기"
}

/** Where a walk goes, e.g. "남광주역 정류장까지 걸어요"; the last walk goes to the destination. */
fun walkTitle(leg: TransitLeg, last: Boolean, destinationName: String): String =
    if (last) "${destinationName}까지 걸어요" else "${leg.to.name ?: "타는 곳"}까지 걸어요"

/** What to take, e.g. "21번, 518번 버스를 타요" or "광주 1호선 녹동 방면을 타요". */
fun rideTitle(leg: TransitLeg): String = when (leg.type) {
    LegType.BUS -> "${leg.lines.joinToString(", ") { "${it}번" }.ifBlank { "버스" }} 버스를 타요"
    LegType.SUBWAY -> "${leg.lines.firstOrNull() ?: "지하철"}${leg.way?.let { " $it 방면" } ?: ""}을 타요"
    LegType.WALK, LegType.STOP -> ""
}

/** Where to get on and off, e.g. "문화전당역에서 타서 3정거장 뒤 남광주역에서 내려요". */
fun rideDetail(leg: TransitLeg): String {
    val on = leg.from.name?.let { "${it}에서 타서 " }.orEmpty()
    val count = leg.stationCount?.let { "${it}정거장 뒤 " }.orEmpty()
    return "$on$count${leg.to.name ?: "내릴 곳"}에서 내려요"
}

/** The next bus or train when the route was found, e.g. "21번 저상버스 3분 뒤 도착 (찾을 때 기준)". */
fun nextRideText(leg: TransitLeg): String? = when (leg.type) {
    LegType.BUS -> leg.buses.filter { it.inMin != null }.minByOrNull { it.inMin!! }?.let {
        "${it.line}번${if (it.lowFloor) " 저상버스" else ""} ${if (it.inMin == 0) "곧" else "${it.inMin}분 뒤"} 도착 (찾을 때 기준)"
    }
    LegType.SUBWAY -> leg.trains.minByOrNull { it.inMin }?.let {
        "${it.direction} ${if (it.inMin == 0) "곧" else "${it.inMin}분 뒤"} 도착 (찾을 때 기준)"
    }
    LegType.WALK, LegType.STOP -> null
}

/** What to say on reaching a ride: what to take and where to get off. */
fun rideSpoken(leg: TransitLeg): String = "${rideTitle(leg)}. ${rideDetail(leg)}."

/** A walking part as a one-route result for the walking guidance; null when the server gave no line for it. */
fun walkResult(leg: TransitLeg): RouteResult? {
    if (leg.path.size < 2) return null
    return RouteResult(
        conditions = emptyList(),
        routes = listOf(RouteOption(RouteType.SAFE, leg.distanceM, leg.minutes, leg.path)),
        explanation = Explanation("", emptyList()),
        facilities = emptyList(),
        dataQuality = DataQuality(ok = true, message = null),
    )
}

/** 들를 곳 not reached yet: the stop legs after [step], as places to ask for again. */
fun stopsAfter(legs: List<TransitLeg>, step: Int): List<StopPlace> =
    legs.drop(step + 1).filter { it.type == LegType.STOP }.map { StopPlace(it.to.name ?: "들를 곳", it.to.point) }

/** What a stop part says, e.g. "다이소에 들러요". */
fun stopTitle(leg: TransitLeg): String = "${leg.to.name ?: "들를 곳"}에 들러요"

/** The parts after a stop, found again from there: those done so far, then the new route's parts. */
fun withRestFoundAgain(legs: List<TransitLeg>, step: Int, rest: TransitRoute): List<TransitLeg> =
    legs.take(step + 1) + guideLegs(rest)

/** At a stop when this close (m): the bus is there, so the next one is the one after. */
const val AT_STOP_M = 30

/**
 * Riding: which of [stops] comes next from [here], never going back before [before] (GPS on a bus wobbles).
 * Past stop k (nearer to k+1 than k is) means k+1 is next; at stop k means the one after it.
 */
fun nextStopIndex(stops: List<LatLon>, here: LatLon, before: Int = 0): Int {
    if (stops.isEmpty()) return 0
    fun d(a: LatLon, b: LatLon) = distanceMeters(a.lat, a.lon, b.lat, b.lon)
    val k = stops.indices.minBy { d(here, stops[it]) }
    val next = when {
        k == stops.lastIndex -> k
        d(here, stops[k]) <= AT_STOP_M -> k + 1
        d(here, stops[k + 1]) < d(stops[k], stops[k + 1]) -> k + 1
        else -> k
    }
    return maxOf(next, before).coerceAtMost(stops.lastIndex)
}

/** How many stops until getting off, counting that one: 1 means the next stop is it. */
fun stopsToAlight(stops: List<TransitStop>, next: Int): Int = (stops.lastIndex - next + 1).coerceAtLeast(0)

/** "정거장" for a bus, "역" for a subway. */
private fun stopWord(leg: TransitLeg) = if (leg.type == LegType.SUBWAY) "역" else "정거장"

/** The line under a ride, e.g. "내릴 곳까지 5정거장 남았어요" or "다음 정거장에서 내려요". */
fun stopsLeftLine(leg: TransitLeg, left: Int): String =
    if (left <= 1) "다음 ${stopWord(leg)} ${leg.to.name ?: ""}에서 내려요".replace("  ", " ")
    else "내릴 곳까지 ${left}${if (leg.type == LegType.SUBWAY) "개 역" else "정거장"} 남았어요"

/** What AI 비서 says when one stop is left. */
fun oneStopLeftSpoken(leg: TransitLeg): String =
    "한 ${stopWord(leg)} 남았어요. 다음 ${stopWord(leg)}${leg.to.name?.let { " $it" } ?: ""}에서 내릴 준비를 하세요."
