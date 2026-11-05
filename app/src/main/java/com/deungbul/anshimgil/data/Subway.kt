package com.deungbul.anshimgil.data

import org.json.JSONObject

/** One train coming to a station (server `/api/subway/nearby`). */
data class Train(
    /** Which way it goes, e.g. "평동행". */
    val direction: String,
    /** Minutes until it arrives; 0 means arriving now. */
    val inMin: Int,
    /** How many stations away it is, when the server knows (live position); null from the timetable only. */
    val stopsAway: Int?,
)

data class StationTrains(val name: String, val lat: Double, val lon: Double, val trains: List<Train>)

/** Trains for stations near a point, from the server's subway timetable (and live positions when known). */
object SubwayTrains {
    /** Stations near [lat]/[lon] with their next trains. Throws [ApiException]. */
    suspend fun near(lat: Double, lon: Double): List<StationTrains> =
        parseSubwayNearby(ServerApi.get("/api/subway/nearby", mapOf("lat" to lat, "lon" to lon)))
}

internal fun parseSubwayNearby(json: JSONObject): List<StationTrains> {
    val stations = json.optJSONArray("stations") ?: return emptyList()
    return (0 until stations.length()).map { i ->
        val s = stations.getJSONObject(i)
        val trains = s.optJSONArray("trains")
        StationTrains(
            name = s.optString("name"),
            lat = s.optDouble("lat"),
            lon = s.optDouble("lon"),
            trains = if (trains == null) emptyList() else (0 until trains.length()).map { j ->
                val t = trains.getJSONObject(j)
                Train(
                    direction = t.optString("direction"),
                    inMin = t.optInt("inMin"),
                    stopsAway = if (t.isNull("stopsAway")) null else t.optInt("stopsAway"),
                )
            },
        )
    }
}

/** The station among [stations] that is [name] ("남광주역" matches the server's "남광주"), or null. */
fun matchStation(name: String?, stations: List<StationTrains>): StationTrains? {
    val key = name?.removeSuffix("역")?.trim() ?: return null
    return stations.firstOrNull { it.name.removeSuffix("역") == key }
}

/**
 * The next train each way, soonest first, e.g. "평동행 · 곧 도착 (1정거장 전)", "녹동행 · 6분 뒤".
 */
fun nextTrainLines(trains: List<Train>): List<String> =
    trains.sortedBy { it.inMin }
        .distinctBy { it.direction }
        .map { t ->
            val whenText = if (t.inMin <= 0) "곧 도착" else "${t.inMin}분 뒤"
            val where = t.stopsAway?.takeIf { it > 0 }?.let { " (${it}정거장 전)" } ?: ""
            "${t.direction} · $whenText$where"
        }
