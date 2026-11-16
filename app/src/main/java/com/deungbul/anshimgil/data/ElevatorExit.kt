package com.deungbul.anshimgil.data

/*
 * 엘리베이터 출구로 도착 (11/11): walking to a subway station, Naver ended at "남광주역 4번 출구 근처 엘리베이터 이용"
 * while 세잎지도 led to the middle of the station, and which exit had an elevator was only in the map's tap card.
 * For the modes that need it (휠체어, 어르신), when the destination is a station, the route now ends at its nearest
 * exit with an elevator from the street (the server's subway_entrance with wheelchair = true).
 */

/** An exit to walk to instead of the station itself. */
data class ElevatorExit(val name: String, val point: LatLon) {
    /** What the guidance calls the destination, e.g. "남광주역 4번 출구 엘리베이터". */
    val label: String get() = "$name 엘리베이터"
}

/**
 * 지하상가 (11/13): an underground mall is entered from the station exits around it (금남지하상가 lies between
 * 문화전당역 and 금남로4가역, server UndergroundLinks), so it is reached by the nearest exit with an elevator within
 * [EXIT_SEARCH_M] of it, of any station; null when none is known.
 */
fun mallElevatorExit(destination: Place, from: LatLon, facilities: List<Facility>): ElevatorExit? {
    val at = LatLon(destination.lat, destination.lon)
    return facilities
        .filter { it.kind == FacilityKind.SUBWAY_ENTRANCE && it.elevator && it.name != null }
        .map { LatLon(it.lat, it.lon) to it }
        .filter { (p, _) -> metersBetween(p, at) <= EXIT_SEARCH_M }
        .minByOrNull { (p, _) -> metersBetween(p, from) }
        ?.let { (p, f) ->
            val station = stationName(f.name!!)
            ElevatorExit(if (station != null) exitNumber(f.name)?.let { "$station ${it}번 출구" } ?: f.name else f.name, p)
        }
}

/** Whether [placeName] is an underground mall ("금남지하상가", "충장로 지하상가"), not a shop in one. */
fun isUndergroundMall(placeName: String): Boolean = placeName.replace(" ", "").let { n -> n.endsWith("지하상가") || n.endsWith("지하쇼핑센터") }

/** Whether the route to [placeName] may end at an elevator exit: a station or an underground mall. */
fun arrivesByElevatorExit(placeName: String): Boolean = stationName(placeName) != null || isUndergroundMall(placeName)

/** What [ElevatorExit.label] ends with. */
private const val ELEVATOR_SUFFIX = " 엘리베이터"

/** The exit's name without " 엘리베이터" when [label] is an elevator exit's ("돌고개역 2번 출구"); null otherwise. */
fun elevatorExitName(label: String): String? = label.takeIf { it.endsWith(ELEVATOR_SUFFIX) }?.removeSuffix(ELEVATOR_SUFFIX)

/** On arrival: (shown, spoken). At an elevator exit it names the exit and the elevator (11/13, was "목적지에 도착했어요"). */
fun arrivalText(destinationName: String): Pair<String, String> =
    elevatorExitName(destinationName)?.let { exit ->
        "$exit 엘리베이터에 도착했어요" to "$exit 엘리베이터에 도착했어요. 엘리베이터를 타고 역으로 내려가세요. 안내를 마칩니다."
    } ?: ("목적지에 도착했어요" to "목적지에 도착했어요. 안내를 마칩니다.")

/** Modes that are led to an elevator exit. */
val ELEVATOR_EXIT_MODES: Set<UserMode> = setOf(UserMode.DISABLED, UserMode.SENIOR)

/** How far around the station its exits are looked for (meters). */
const val EXIT_SEARCH_M = 400.0

/** The station's own name in a place name, e.g. "남광주역" for "남광주역 광주1호선"; null when it is not a station. */
fun stationName(placeName: String): String? =
    Regex("""^\s*(\S+역)(\s|$)""").find(placeName)?.groupValues?.get(1)?.takeIf { "출구" !in placeName }

/**
 * The exit with an elevator to arrive at for [destination], the one nearest to [from] (the shorter walk); null when
 * the destination is not a station or none of its exits is known to have an elevator.
 */
fun elevatorExitFor(destination: Place, from: LatLon, facilities: List<Facility>): ElevatorExit? {
    if (isUndergroundMall(destination.name)) return mallElevatorExit(destination, from, facilities)
    val station = stationName(destination.name) ?: return null
    val at = LatLon(destination.lat, destination.lon)
    return facilities
        .filter { it.kind == FacilityKind.SUBWAY_ENTRANCE && it.elevator && it.name?.startsWith(station) == true }
        .map { LatLon(it.lat, it.lon) to it }
        .filter { (p, _) -> metersBetween(p, at) <= EXIT_SEARCH_M }
        .minByOrNull { (p, _) -> metersBetween(p, from) }
        ?.let { (p, f) -> ElevatorExit(exitNumber(f.name)?.let { "$station ${it}번 출구" } ?: (f.name ?: station), p) }
}

/**
 * What to call the destination when the server chose the exit (11/16, server #264): its `entrance` note says
 * "금남지하상가는 문화전당역 3번 출구(엘리베이터)로 들어가요", so the route ends at "문화전당역 3번 출구 엘리베이터"
 * (the same label as [ElevatorExit], so arrival names the elevator) or, by stairs, "문화전당역 5번 출구".
 * Null when there is no such note: the server did not know the place, and the app picks the exit itself.
 */
fun serverExitLabel(notes: List<RouteNote>): String? {
    val text = notes.firstOrNull { it.kind == "entrance" }?.text ?: return null
    val exit = Regex("""[은는]\s+(.+?)로\s*들어가요""").find(text)?.groupValues?.get(1)?.trim() ?: return null
    val way = Regex("""\((계단|엘리베이터)\)$""").find(exit)?.groupValues?.get(1)
    val name = exit.replace(Regex("""\s*\((계단|엘리베이터)\)$"""), "")
    return if (way == "엘리베이터") "$name$ELEVATOR_SUFFIX" else name
}
