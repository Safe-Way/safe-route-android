package com.deungbul.anshimgil.data

import org.json.JSONArray
import org.json.JSONObject

/*
 * 대중교통 탭 (FR-37): the server's `POST /api/transit`, which asks ODsay for bus and subway routes and
 * swaps the walking parts for our own safer walking routes. At most 3 routes, best first.
 */

enum class LegType { WALK, BUS, SUBWAY }

data class TransitStop(val name: String?, val point: LatLon)

/** A bus coming to the stop where the walker gets on. */
data class BusComing(val line: String, val inMin: Int?, val stopsAway: Int?, val lowFloor: Boolean)

/** One part of a transit route: a walk, a bus ride or a subway ride. */
data class TransitLeg(
    val type: LegType,
    val distanceM: Int,
    val minutes: Int,
    val from: TransitStop,
    val to: TransitStop,
    /** Bus numbers or the subway line name; empty for walks. */
    val lines: List<String>,
    /** Subway direction, e.g. "녹동". */
    val way: String?,
    val stationCount: Int?,
    /** Walking line (only when the server swapped in our walking route). */
    val path: List<LatLon>,
    val buses: List<BusComing>,
    val trains: List<Train>,
)

data class TransitRoute(val totalMin: Int, val fare: Int?, val transfers: Int, val walkM: Int, val legs: List<TransitLeg>)

data class TransitResult(val routes: List<TransitRoute>, val hiddenForWheelchair: Int, val note: String?)

object TransitRepository {
    /** Throws [ApiException], e.g. TOO_CLOSE (walk instead), NO_ROUTE, DAILY_LIMIT, TRANSIT_UNAVAILABLE. */
    suspend fun find(origin: LatLon, destination: LatLon, mode: UserMode): TransitResult {
        val body = JSONObject()
            .put("origin", JSONObject().put("lat", origin.lat).put("lon", origin.lon))
            .put("destination", JSONObject().put("lat", destination.lat).put("lon", destination.lon))
            .put("mode", mode.key)
        return parseTransit(ServerApi.post("/api/transit", body))
    }
}

internal fun parseTransit(json: JSONObject): TransitResult {
    val paths = json.optJSONArray("paths") ?: JSONArray()
    return TransitResult(
        routes = (0 until paths.length()).map { i -> parsePath(paths.getJSONObject(i)) },
        hiddenForWheelchair = json.optInt("hiddenForWheelchair"),
        note = json.optString("note").takeUnless { json.isNull("note") || it.isBlank() },
    )
}

private fun parsePath(p: JSONObject): TransitRoute {
    val legs = p.optJSONArray("legs") ?: JSONArray()
    return TransitRoute(
        totalMin = p.optInt("totalMin"),
        fare = if (p.isNull("fare")) null else p.optInt("fare"),
        transfers = p.optInt("transfers"),
        walkM = p.optInt("walkM"),
        legs = (0 until legs.length()).map { parseLeg(legs.getJSONObject(it)) },
    )
}

private fun parseLeg(l: JSONObject): TransitLeg {
    fun stop(o: JSONObject?) = TransitStop(
        name = o?.optString("name")?.takeUnless { o.isNull("name") || it.isBlank() },
        point = LatLon(o?.optDouble("lat") ?: 0.0, o?.optDouble("lon") ?: 0.0),
    )
    fun strings(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }
    val geometry = l.optJSONArray("geometry")
    val buses = l.optJSONArray("buses")
    val trains = l.optJSONArray("trains")
    return TransitLeg(
        type = when (l.optString("type")) {
            "bus" -> LegType.BUS
            "subway" -> LegType.SUBWAY
            else -> LegType.WALK
        },
        distanceM = l.optInt("distanceM"),
        minutes = l.optInt("minutes"),
        from = stop(l.optJSONObject("from")),
        to = stop(l.optJSONObject("to")),
        lines = strings(l.optJSONArray("lines")),
        way = l.optString("way").takeUnless { l.isNull("way") || it.isBlank() },
        stationCount = if (l.isNull("stationCount")) null else l.optInt("stationCount"),
        // GeoJSON order: [lon, lat]
        path = if (geometry == null) emptyList() else (0 until geometry.length()).map { i ->
            geometry.getJSONArray(i).let { LatLon(it.getDouble(1), it.getDouble(0)) }
        },
        buses = if (buses == null) emptyList() else (0 until buses.length()).map { i ->
            val b = buses.getJSONObject(i)
            BusComing(
                line = b.optString("lineName"),
                inMin = if (b.isNull("remainMin")) null else b.optInt("remainMin"),
                stopsAway = if (b.isNull("remainStop")) null else b.optInt("remainStop"),
                lowFloor = b.optBoolean("lowFloor"),
            )
        },
        trains = if (trains == null) emptyList() else (0 until trains.length()).map { i ->
            val t = trains.getJSONObject(i)
            Train(t.optString("direction"), t.optInt("inMin"), if (t.isNull("stopsAway")) null else t.optInt("stopsAway"))
        },
    )
}

/** "45번 · 5분 뒤 (3정거장 전) · 저상" for the soonest bus, or null when none is known. */
fun nextBusText(buses: List<BusComing>): String? {
    val b = buses.minByOrNull { it.inMin ?: Int.MAX_VALUE } ?: return null
    val whenText = when (val m = b.inMin) {
        null -> "도착 시간 모름"
        0 -> "곧 도착"
        else -> "${m}분 뒤"
    }
    val stops = b.stopsAway?.takeIf { it > 0 }?.let { " (${it}정거장 전)" } ?: ""
    return "${b.line}번 · $whenText$stops" + if (b.lowFloor) " · 저상버스" else ""
}

/** One line about a leg for the route list, e.g. "45번 버스 · 조선대해오름관 → 시청광주청사 · 32정거장". */
fun legTitle(leg: TransitLeg): String = when (leg.type) {
    LegType.WALK -> "걷기 ${leg.distanceM}m · ${leg.minutes}분" + (leg.to.name?.let { " → $it" } ?: " → 도착")
    LegType.BUS -> "${leg.lines.joinToString(", ") { "${it}번" }} 버스 · ${leg.from.name ?: "정류장"} → ${leg.to.name ?: "정류장"}" +
        (leg.stationCount?.let { " · ${it}정거장" } ?: "")
    LegType.SUBWAY -> "${leg.lines.firstOrNull() ?: "지하철"}${leg.way?.let { " $it 방면" } ?: ""} · ${leg.from.name ?: "역"} → ${leg.to.name ?: "역"}" +
        (leg.stationCount?.let { " · ${it}개 역" } ?: "")
}
