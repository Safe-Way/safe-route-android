package com.deungbul.anshimgil.data

import org.json.JSONObject

/*
 * Road events while walking: an accident, roadworks, a closure or a festival that started after the
 * route was found. The server keeps them from the national traffic centre and festival data
 * (`GET /api/events`, road_event); the guide screen asks every few minutes and tells the walker about
 * a new one on the road ahead, offering to find another way.
 */

/** One event from `GET /api/events`; the area it affects is a circle of [radiusM] around its point. */
data class RoadEvent(
    val id: Long,
    /** 행사, 공사, 사고, 통제, 재난, 기타 */
    val kind: String,
    val title: String,
    val place: String?,
    val lat: Double,
    val lon: Double,
    val radiusM: Int,
    val message: String?,
)

/** How often the guide screen asks for new events. */
const val EVENT_CHECK_MS = 3 * 60_000L

interface EventRepository {
    /** Events going on now in [bbox]. Throws [ApiException]. */
    suspend fun within(bbox: Bbox): List<RoadEvent>

    companion object {
        fun create(): EventRepository = if (ServerApi.isConfigured) ServerEventRepository() else NoEventRepository
    }
}

class ServerEventRepository : EventRepository {
    override suspend fun within(bbox: Bbox): List<RoadEvent> {
        val b = bbox.clamped()
        return parseRoadEvents(ServerApi.get("/api/events", mapOf("bbox" to "${b.west},${b.south},${b.east},${b.north}")))
    }
}

/** Without the server there are no events. */
object NoEventRepository : EventRepository {
    override suspend fun within(bbox: Bbox): List<RoadEvent> = emptyList()
}

internal fun parseRoadEvents(json: JSONObject): List<RoadEvent> {
    val array = json.optJSONArray("events") ?: return emptyList()
    fun JSONObject.text(key: String): String? = optString(key).takeIf { !isNull(key) && it.isNotBlank() }
    return (0 until array.length()).mapNotNull { i ->
        val e = array.optJSONObject(i) ?: return@mapNotNull null
        if (!e.has("lat") || !e.has("lon")) return@mapNotNull null
        RoadEvent(
            id = e.optLong("id"),
            kind = e.text("kind") ?: "기타",
            title = e.text("title") ?: return@mapNotNull null,
            place = e.text("place"),
            lat = e.getDouble("lat"),
            lon = e.getDouble("lon"),
            radiusM = e.optInt("radiusM", 100).coerceAtLeast(30),
            message = e.text("message"),
        )
    }
}

/** A new event on the road ahead and how far along the route it is. */
data class EventAhead(val event: RoadEvent, val aheadM: Int)

/**
 * Events in [events] the rest of the route passes through, leaving out [known] ones (already on the
 * route when it was found, or already told), nearest first.
 */
fun eventsAhead(guidance: Guidance, progressM: Double, events: List<RoadEvent>, known: Set<Long>): List<EventAhead> =
    events.filter { it.id !in known }.mapNotNull { e ->
        guidance.passesWithin(LatLon(e.lat, e.lon), e.radiusM.toDouble(), progressM)?.let { at ->
            EventAhead(e, (at - progressM).toInt().coerceAtLeast(0))
        }
    }.sortedBy { it.aheadM }

/** What to say about an event ahead, e.g. "앞 300m에 공사가 있어요. 금남로 보수작업" */
fun eventAheadText(ahead: EventAhead): String {
    val what = when (ahead.event.kind) {
        "공사" -> "공사가 있어요"
        "사고" -> "사고가 났어요"
        "통제" -> "길이 통제되고 있어요"
        "재난" -> "재난 상황이 있어요"
        "행사" -> "행사로 붐벼요"
        else -> "돌발 상황이 있어요"
    }
    val where = if (ahead.aheadM < 50) "이 근처에" else "앞 ${(ahead.aheadM / 10) * 10}m에"
    return "$where $what. ${ahead.event.title}"
}
