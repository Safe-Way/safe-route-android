package com.deungbul.anshimgil.data.route

import org.json.JSONArray
import org.json.JSONObject
import com.deungbul.anshimgil.data.guide.metersBetween

/*
 * 들를 곳 (팀 결정 11-09, 앱 할 일 11): a walk or a transit trip that stops at a few places on the way
 * (다이소 → CU → 집). Server: route engine w10 (원일), AI 비서 c15 (채림).
 *
 * Walking: routes[].stops[] {name, lat, lon, atM} where atM is meters along the route line. The map shows
 * numbered pins; reaching one says "다이소에 도착했어요. 다음은 CU예요" with [다 봤어요]; the top shows what
 * is left ("2/3 다이소"), and [이곳은 안 갈게요] skips the next one.
 * Transit: a leg of type "stop" between the others; after [다 봤어요] the rest is found again from here.
 */

/** At most this many stops on a walk (팀 결정). */
const val MAX_WALK_STOPS = 3

/** At most this many on a transit trip: each costs the server one more ODsay call, 30 a day (채림, #121). */
const val MAX_TRANSIT_STOPS = 1

/**
 * What the transit tab says when stops were left out (11/16, 원일): only the first [MAX_TRANSIT_STOPS] are asked for,
 * e.g. "대중교통은 첫 번째 들를 곳(다이소)만 들러요. CU는 빠져요"; null when nothing is left out.
 */
fun transitStopsLeftOutText(stops: List<String>): String? {
    if (stops.size <= MAX_TRANSIT_STOPS) return null
    val kept = stops.take(MAX_TRANSIT_STOPS).joinToString(", ")
    val left = stops.drop(MAX_TRANSIT_STOPS).joinToString(", ")
    return "대중교통은 첫 번째 들를 곳(${kept})만 들러요. ${eunNeun(left)} 빠져요"
}

/** Within this many meters of a stop already on the list, a new one is the same place (11/16: the server refuses twice). */
const val SAME_STOP_M = 30.0

/** [stops] with [place] added at the end, or null when it is already there (same name or within [SAME_STOP_M]). */
fun withStop(stops: List<StopPlace>, place: StopPlace): List<StopPlace>? =
    if (stops.any { it.name == place.name || metersBetween(it.point, place.point) < SAME_STOP_M }) null else stops + place

/** [word] with 은 after a closed syllable, 는 otherwise: 다이소는, 편의점은. */
fun eunNeun(word: String): String {
    val code = (word.lastOrNull()?.code ?: return word) - 0xAC00
    return word + if (code in 0..11171 && code % 28 != 0) "은" else "는"
}

/** Within this many meters along the route of a stop counts as being there. */
const val STOP_ARRIVE_M = 20.0

/** A place to stop at, as asked for. */
data class StopPlace(val name: String, val point: LatLon)

/** A stop on a walking route: [atM] meters along the route line as the server sent it. */
data class RouteStop(val name: String, val point: LatLon, val atM: Double)

/** `stops` for a route or transit request: [{name, lat, lon}], or null when there are none. */
fun stopsJson(stops: List<StopPlace>): JSONArray? =
    stops.takeIf { it.isNotEmpty() }?.let { list ->
        JSONArray().apply { list.forEach { put(JSONObject().put("name", it.name).put("lat", it.point.lat).put("lon", it.point.lon)) } }
    }

/** routes[].stops from the server; empty when not there. */
internal fun parseRouteStops(route: JSONObject): List<RouteStop> {
    val a = route.optJSONArray("stops") ?: return emptyList()
    return (0 until a.length()).mapNotNull { i ->
        val s = a.optJSONObject(i) ?: return@mapNotNull null
        val name = s.optString("name").takeIf { !s.isNull("name") && it.isNotBlank() } ?: "들를 곳 ${i + 1}"
        if (!s.has("lat") || !s.has("lon")) return@mapNotNull null
        RouteStop(name, LatLon(s.getDouble("lat"), s.getDouble("lon")), s.optDouble("atM", 0.0))
    }.sortedBy { it.atM }
}

/** "이에요" after a closed syllable, "예요" otherwise: 다이소예요, 편의점이에요. */
fun ieyo(word: String): String {
    val code = (word.lastOrNull()?.code ?: return word) - 0xAC00
    return word + if (code in 0..11171 && code % 28 != 0) "이에요" else "예요"
}

/**
 * What to say on reaching stop [index] of [stops] ([skipped] ones are not "next"): the place, then where to go next,
 * e.g. "다이소에 도착했어요. 다음은 CU예요." or, at the last one, "… 다음은 집이에요."
 */
fun stopArrivedText(stops: List<String>, index: Int, skipped: Set<Int>, destinationName: String): String {
    val next = (index + 1 until stops.size).firstOrNull { it !in skipped }?.let { stops[it] } ?: destinationName
    return "${stops[index]}에 도착했어요. 다음은 ${ieyo(next)}."
}

/** "2/3 다이소": the next stop not yet done, counted among all; null when all are done. */
fun stopsLeftText(stops: List<String>, done: Set<Int>): String? {
    val next = stops.indices.firstOrNull { it !in done } ?: return null
    return "${next + 1}/${stops.size} ${stops[next]}"
}

/**
 * The stop the walker has just reached, if any: the first not [done] one whose place along the route ([alongM]) is
 * within [STOP_ARRIVE_M] of [progressM] or already passed. Stops must be reached in order.
 */
fun stopReached(alongM: List<Double>, progressM: Double, done: Set<Int>): Int? {
    val next = alongM.indices.firstOrNull { it !in done } ?: return null
    return next.takeIf { progressM >= alongM[next] - STOP_ARRIVE_M }
}

/** Shown after the rest of a transit trip is found again from a stop. */
const val STOP_REFOUND_TEXT = "지금 있는 곳에서 남은 길을 다시 찾았어요. 버스 도착 시간이 새로 바뀌어서 처음 보여 드린 길과 다를 수 있어요."

/** Shown when finding the rest again failed: the first route carries on. */
const val STOP_REFIND_FAILED_TEXT = "남은 길을 다시 찾지 못해서 처음 길로 안내할게요."
