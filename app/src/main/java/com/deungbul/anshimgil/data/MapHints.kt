package com.deungbul.anshimgil.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/*
 * AI 비서 말풍선: one short line under the AI 비서 button on the map, like a pinned comment on a live
 * stream. Only from real data, never filler:
 *  - the walker's usual trip at this time, when the server's assistant recommends it (with the weather
 *    reason in its words) — the only one that asks, and its button opens that route;
 *  - a road event (festival, roadworks, accident, closure) near the walker, from the server;
 *  - sunset within the hour, worked out on the phone; routes then use night rules by themselves.
 * Nothing applies, nothing shows. Each line shows once per app run.
 */

/** One bubble. [place] is set when the bubble offers a route there (its button opens it). */
data class MapHint(val key: String, val text: String, val place: Place? = null)

/** Lines already shown while the app runs, so leaving the map and coming back does not repeat them. */
object ShownHints {
    private val keys = mutableSetOf<String>()
    fun isNew(key: String) = key !in keys
    fun mark(key: String) {
        keys += key
    }
}

private val KST: ZoneId = ZoneId.of("Asia/Seoul")

/** Today's sunset at [lat], [lon] in Korean time (NOAA's simple formula, within a minute or two). Null in polar day or night. */
fun sunsetAt(date: LocalDate, lat: Double, lon: Double): LocalTime? {
    val n = date.dayOfYear
    val gamma = 2 * Math.PI / 365 * (n - 1)
    val eqTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
        0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
    val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) - 0.006758 * cos(2 * gamma) +
        0.000907 * sin(2 * gamma) - 0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
    val latR = Math.toRadians(lat)
    val cosHa = cos(Math.toRadians(90.833)) / (cos(latR) * cos(decl)) - tan(latR) * tan(decl)
    if (cosHa < -1 || cosHa > 1) return null
    val ha = Math.toDegrees(acos(cosHa))
    val utcMinutes = 720 - 4 * (lon - ha) - eqTime
    val offset = KST.rules.getOffset(date.atStartOfDay()).totalSeconds / 60
    val minutes = ((utcMinutes + offset).roundToInt() % 1440 + 1440) % 1440
    return LocalTime.of(minutes / 60, minutes % 60)
}

/** Within the hour before sunset: say so, since routes switch to night rules from then (autoNightReason). */
fun eveningHint(now: LocalDateTime, sunset: LocalTime?): MapHint? {
    sunset ?: return null
    val left = java.time.Duration.between(now.toLocalTime(), sunset).toMinutes()
    if (left !in 1..EVENING_BEFORE_SUNSET_MIN) return null
    val time = "%d시 %02d분".format(if (sunset.hour > 12) sunset.hour - 12 else sunset.hour, sunset.minute)
    return MapHint("sunset-${now.toLocalDate()}", "${time}에 해가 져요. 지금부터는 밝은 길로 먼저 안내할게요.")
}

/** How near (beyond its own radius) an event must be to mention. */
private const val EVENT_NEAR_EXTRA_M = 500

/** The nearest road event whose area is within [EVENT_NEAR_EXTRA_M] of [here]. */
fun eventHint(here: LatLon, events: List<RoadEvent>): MapHint? {
    val (event, meters) = events
        .map { it to distanceMeters(here.lat, here.lon, it.lat, it.lon) }
        .filter { (e, d) -> d <= e.radiusM + EVENT_NEAR_EXTRA_M }
        .minByOrNull { it.second } ?: return null
    val where = if (meters <= event.radiusM) "지금 계신 곳 근처에서" else "약 ${((meters - event.radiusM) / 50 + 1) * 50}m 떨어진 곳에서"
    val what = when (event.kind) {
        "행사" -> "'${event.title}' 행사가 열리고 있어요"
        "공사" -> "공사를 하고 있어요 (${event.title})"
        "사고" -> "사고가 났어요 (${event.title})"
        "통제" -> "길을 막고 있어요 (${event.title})"
        else -> "${event.kind} 상황이 있어요 (${event.title})"
    }
    return MapHint("event-${event.id}", "$where $what. 그쪽을 지나게 되면 미리 알려 드릴게요.")
}

/**
 * The server assistant's own first word (`text` empty): kept only when it recommends a trip (the walker's
 * usual one at this time), never its general "어디 가실 곳이 있으세요?".
 */
internal fun habitHint(json: org.json.JSONObject, today: LocalDate): MapHint? {
    if (json.optJSONObject("pick") == null) return null
    val reply = parseServerAssistantReply(json) ?: return null
    val go = reply.action as? AssistantAction.GoPlace ?: return null
    return MapHint("habit-$today-${go.place.name}", go.say, go.place)
}

/** Picks the bubble for the map: the usual trip first, then a nearby event, then sunset. */
class MapHintSource(
    private val events: EventRepository,
    private val guestId: suspend () -> String?,
    private val favorites: suspend () -> List<Pair<String, LatLon>>,
) {
    suspend fun next(mode: UserMode, here: LatLon, now: LocalDateTime = LocalDateTime.now(KST)): MapHint? {
        if (ServerApi.isConfigured) {
            val habit = runCatching {
                val body = serverAssistantRequest(guestId(), "", mode, here, now, null, null, favorites())
                habitHint(ServerApi.post("/api/assistant", body), now.toLocalDate())
            }.getOrNull()
            if (habit != null && ShownHints.isNew(habit.key)) return habit
        }
        val event = runCatching {
            eventHint(here, events.within(Bbox(here.lon - 0.012, here.lat - 0.01, here.lon + 0.012, here.lat + 0.01)))
        }.getOrNull()
        if (event != null && ShownHints.isNew(event.key)) return event
        return eveningHint(now, sunsetAt(now.toLocalDate(), here.lat, here.lon))?.takeIf { ShownHints.isNew(it.key) }
    }
}
