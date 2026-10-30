package com.deungbul.anshimgil.data

import kotlinx.coroutines.delay

/** Place search: the server's `/api/places` when configured, otherwise example places. */
interface PlaceRepository {
    /** Up to 10 places, nearest first when [lat]/[lon] are given. Throws [ApiException]. */
    suspend fun search(query: String, lat: Double?, lon: Double?): List<Place>

    companion object {
        fun create(): PlaceRepository = if (ServerApi.isConfigured) ServerPlaceRepository() else FakePlaceRepository()
    }
}

class ServerPlaceRepository : PlaceRepository {
    override suspend fun search(query: String, lat: Double?, lon: Double?): List<Place> {
        val json = ServerApi.get("/api/places", mapOf("query" to query, "lat" to lat, "lon" to lon))
        val places = json.optJSONArray("places") ?: return emptyList()
        return (0 until places.length()).map { Place.fromJson(places.getJSONObject(it)) }
    }
}

/** Example places around Chosun University for building screens without the server. */
class FakePlaceRepository : PlaceRepository {
    private val places = listOf(
        Place("ex-1", "조선대학교 IT융합대학", "광주 동구 조선대5길 65", 35.1400, 126.9342, null),
        Place("ex-2", "조선대학교 중앙도서관", "광주 동구 필문대로 309", 35.1418, 126.9330, null),
        Place("ex-3", "조선대학교 후문", "광주 동구 서석동", 35.1372, 126.9322, null),
        Place("ex-4", "남광주역", "광주 동구 학동", 35.1388, 126.9235, null),
        Place("ex-5", "전남대학교병원", "광주 동구 제봉로 42", 35.1430, 126.9215, null),
        Place("ex-6", "충장로", "광주 동구 충장로", 35.1490, 126.9150, null),
    )

    override suspend fun search(query: String, lat: Double?, lon: Double?): List<Place> {
        delay(200)
        if (query.trim().length < 2) throw ApiException("BAD_PARAM", "검색어는 2자 이상 입력해 주세요")
        val words = query.trim().split(" ").filter { it.isNotBlank() }
        return places
            .filter { place -> words.any { place.name.contains(it) || place.address.contains(it) } }
            .map { it.copy(distanceM = if (lat != null && lon != null) distanceMeters(lat, lon, it.lat, it.lon) else null) }
            .sortedBy { it.distanceM ?: 0 }
    }
}

/** Straight-line distance, good enough for sorting nearby results. */
fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).let { it * it }
    return (2 * r * Math.asin(Math.sqrt(a))).toInt()
}

internal fun List<Place>.toJsonArrayString(): String = org.json.JSONArray(map { it.toJson() }).toString()

internal fun placesFromJsonArray(text: String?): List<Place> {
    if (text.isNullOrBlank()) return emptyList()
    val array = org.json.JSONArray(text)
    return (0 until array.length()).map { Place.fromJson(array.getJSONObject(it)) }
}
