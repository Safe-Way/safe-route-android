package com.deungbul.anshimgil.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/*
 * 이어서 안내 (11/13, 팀 요청): leaving for a text message during guidance and coming back found the route gone
 * (the screen was only remembered, so when Android closed the app it opened on the map). The guidance in progress is
 * kept here when it starts and dropped when it ends; on opening the app the same route is asked for again and the
 * guidance goes on (the walker's position is found on it as usual).
 */

/** A walking guidance in progress: enough to ask for the same route again. */
data class SavedGuide(
    val destination: Place,
    /** The start that was chosen; null when it was the phone's position. */
    val start: Place?,
    val stops: List<StopPlace>,
    val type: RouteType,
    /** What the guidance calls the destination when it is not the place itself (an elevator exit). */
    val arriveAt: String?,
    /** Where the route started and ends (the end is the elevator exit when there is one). */
    val from: LatLon,
    val to: LatLon,
    val savedAtMs: Long,
)

/** Guidance left longer ago than this is not taken up again (the walker has most likely finished or gone home). */
const val GUIDE_RESUME_MS = 3 * 60 * 60 * 1000L

object GuideStore {
    private const val PREFS = "guide_resume"
    private const val KEY = "guide"

    fun save(context: Context, guide: SavedGuide) {
        prefs(context).edit().putString(KEY, savedGuideToJson(guide).toString()).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    /** The guidance to go on with, or null (none, unreadable, or too old). */
    fun load(context: Context, nowMs: Long = System.currentTimeMillis()): SavedGuide? =
        runCatching { prefs(context).getString(KEY, null)?.let { savedGuideFromJson(JSONObject(it)) } }.getOrNull()
            ?.takeIf { nowMs - it.savedAtMs in 0..GUIDE_RESUME_MS }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

private fun LatLon.toJson() = JSONObject().put("lat", lat).put("lon", lon)
private fun JSONObject.latLon() = LatLon(getDouble("lat"), getDouble("lon"))

fun savedGuideToJson(g: SavedGuide): JSONObject = JSONObject()
    .put("destination", g.destination.toJson())
    .put("start", g.start?.toJson() ?: JSONObject.NULL)
    .put("stops", JSONArray(g.stops.map { JSONObject().put("name", it.name).put("at", it.point.toJson()) }))
    .put("type", g.type.name)
    .put("arriveAt", g.arriveAt ?: JSONObject.NULL)
    .put("from", g.from.toJson())
    .put("to", g.to.toJson())
    .put("savedAtMs", g.savedAtMs)

fun savedGuideFromJson(j: JSONObject): SavedGuide {
    val stops = j.optJSONArray("stops")
    return SavedGuide(
        destination = Place.fromJson(j.getJSONObject("destination")),
        start = j.optJSONObject("start")?.let { Place.fromJson(it) },
        stops = (0 until (stops?.length() ?: 0)).map { i -> stops!!.getJSONObject(i).let { StopPlace(it.getString("name"), it.getJSONObject("at").latLon()) } },
        type = RouteType.valueOf(j.getString("type")),
        arriveAt = j.optString("arriveAt").takeIf { !j.isNull("arriveAt") && it.isNotBlank() },
        from = j.getJSONObject("from").latLon(),
        to = j.getJSONObject("to").latLon(),
        savedAtMs = j.getLong("savedAtMs"),
    )
}
