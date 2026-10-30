package com.deungbul.anshimgil.data

import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Route finding: the server's `/api/route` when configured, otherwise an example route. */
interface RouteRepository {
    /** Throws [ApiException]. */
    suspend fun findRoutes(request: RouteRequest): RouteResult

    companion object {
        fun create(): RouteRepository = if (ServerApi.isConfigured) ServerRouteRepository() else FakeRouteRepository()
    }
}

/**
 * Talks to `POST /api/route`. Field names inside each section are a first guess;
 * adjust [toJson] and the parsers once the server shape is final.
 */
class ServerRouteRepository : RouteRepository {
    override suspend fun findRoutes(request: RouteRequest): RouteResult =
        parseRouteResult(ServerApi.post("/api/route", request.toJson()))
}

internal fun RouteRequest.toJson(): JSONObject = JSONObject()
    .put("origin", JSONObject().put("lat", origin.lat).put("lon", origin.lon))
    .put("destination", JSONObject().put("lat", destination.lat).put("lon", destination.lon))
    .put("mode", mode.key)
    .put("conditionsOff", JSONArray(conditionsOff.toList()))

internal fun parseRouteResult(json: JSONObject): RouteResult {
    fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    val conditions = json.optJSONArray("conditions").objects().map {
        Condition(it.getString("key"), it.optString("label", it.getString("key")), it.optBoolean("on", true))
    }
    val routes = json.optJSONArray("routes").objects().mapNotNull { route ->
        val type = runCatching { RouteType.valueOf(route.getString("type").uppercase()) }.getOrNull() ?: return@mapNotNull null
        val path = route.optJSONArray("path")
        RouteOption(
            type = type,
            distanceM = route.optInt("distanceM"),
            durationMin = route.optInt("durationMin"),
            path = if (path == null) emptyList() else (0 until path.length()).map { i ->
                val point = path.get(i)
                if (point is JSONArray) LatLon(point.getDouble(0), point.getDouble(1))
                else (point as JSONObject).let { LatLon(it.getDouble("lat"), it.getDouble("lon")) }
            },
        )
    }
    val explanationJson = json.optJSONObject("explanation") ?: JSONObject()
    val explanation = Explanation(
        summary = explanationJson.optString("summary"),
        items = explanationJson.optJSONArray("items").objects().map {
            CompareItem(it.getString("label"), it.optInt("shortest"), it.optInt("safe"), it.optString("unit"))
        },
    )
    val facilities = json.optJSONArray("facilities").objects().mapNotNull { f ->
        val kind = runCatching { FacilityKind.valueOf(f.getString("kind").uppercase()) }.getOrNull() ?: return@mapNotNull null
        Facility(kind, f.getDouble("lat"), f.getDouble("lon"))
    }
    val quality = json.optJSONObject("dataQuality")
    return RouteResult(
        conditions = conditions,
        routes = routes,
        explanation = explanation,
        facilities = facilities,
        dataQuality = DataQuality(
            ok = quality?.optBoolean("ok", true) ?: true,
            message = quality?.optString("message")?.ifBlank { null },
        ),
    )
}

/**
 * Example routes for building screens without the server: the shortest path is an L-shape,
 * the safe path detours about 110 m to the west. Numbers are illustrative only.
 */
class FakeRouteRepository : RouteRepository {
    override suspend fun findRoutes(request: RouteRequest): RouteResult {
        delay(400)
        val o = request.origin
        val d = request.destination
        if (distanceMeters(o.lat, o.lon, d.lat, d.lon) < 30) {
            throw ApiException("BAD_PARAM", "출발지와 도착지가 너무 가까워요.")
        }
        val detour = 0.0012
        val shortestPath = listOf(o, LatLon(d.lat, o.lon), d)
        val safePath = listOf(o, LatLon(o.lat, o.lon - detour), LatLon(d.lat, o.lon - detour), d)
        val speed = request.mode.walkingSpeedMPerMin
        val shortest = option(RouteType.SHORTEST, shortestPath, speed)
        val safe = option(RouteType.SAFE, safePath, speed)

        val conditions = listOf(
            Condition("night", "밤", "night" !in request.conditionsOff),
            Condition("rain", "비", "rain" !in request.conditionsOff),
        )
        val extraM = (safe.distanceM - shortest.distanceM).coerceAtLeast(0)
        val extraMin = (safe.durationMin - shortest.durationMin).coerceAtLeast(0)
        return RouteResult(
            conditions = conditions,
            routes = listOf(safe, shortest),
            explanation = Explanation(
                summary = "${extraM}m(${extraMin}분) 더 걷지만, 안전한 길이에요",
                items = compareItems(request.mode),
            ),
            facilities = facilitiesAlong(safePath, request.mode),
            dataQuality = DataQuality(ok = true, message = null),
        )
    }

    private fun option(type: RouteType, path: List<LatLon>, speed: Int): RouteOption {
        val meters = pathLength(path)
        return RouteOption(type, meters, (meters.toDouble() / speed).roundToInt().coerceAtLeast(1), path)
    }

    private fun compareItems(mode: UserMode): List<CompareItem> = when (mode) {
        UserMode.CHILD -> listOf(
            CompareItem("아동안전지킴이집", 0, 2, "곳"),
            CompareItem("CCTV", 3, 9, "대"),
            CompareItem("큰길 건너기", 2, 1, "번"),
        )
        UserMode.SENIOR -> listOf(
            CompareItem("쉼터", 0, 2, "곳"),
            CompareItem("화장실", 0, 1, "곳"),
            CompareItem("계단", 2, 0, "곳"),
        )
        UserMode.DISABLED -> listOf(
            CompareItem("계단·급경사", 3, 0, "곳"),
            CompareItem("장애인 화장실", 0, 1, "곳"),
            CompareItem("엘리베이터", 0, 1, "곳"),
        )
        UserMode.GENERAL -> listOf(
            CompareItem("CCTV", 3, 12, "대"),
            CompareItem("가로등", 20, 34, "개"),
            CompareItem("비상벨", 0, 3, "개"),
        )
    }

    private fun facilitiesAlong(path: List<LatLon>, mode: UserMode): List<Facility> {
        val kinds = mode.facilityKinds.filter { it != FacilityKind.STAIRS }
        return listOf(0.25, 0.5, 0.75).mapIndexed { i, fraction ->
            val p = pointAlong(path, fraction)
            // Offset slightly so the tag sits beside the line rather than on it.
            Facility(kinds[i % kinds.size], p.lat + 0.00008, p.lon - 0.00010)
        }
    }
}

fun pathLength(path: List<LatLon>): Int =
    path.zipWithNext { a, b -> distanceMeters(a.lat, a.lon, b.lat, b.lon) }.sum()

/** The point [fraction] (0..1) of the way along [path]. */
fun pointAlong(path: List<LatLon>, fraction: Double): LatLon {
    val total = pathLength(path).toDouble()
    var remaining = total * fraction.coerceIn(0.0, 1.0)
    path.zipWithNext { a, b ->
        val leg = distanceMeters(a.lat, a.lon, b.lat, b.lon).toDouble()
        if (remaining <= leg && leg > 0) {
            val t = remaining / leg
            return LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
        }
        remaining -= leg
    }
    return path.last()
}
