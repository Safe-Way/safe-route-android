package com.deungbul.anshimgil.data

import org.json.JSONObject

/** The map area to load, in degrees. */
data class Bbox(val west: Double, val south: Double, val east: Double, val north: Double) {
    /** The server takes at most [max] degrees a side, so a wider view keeps only the middle. */
    fun clamped(max: Double = MAX_BBOX_DEG): Bbox {
        val lonC = (west + east) / 2
        val latC = (south + north) / 2
        val halfLon = minOf(east - west, max) / 2
        val halfLat = minOf(north - south, max) / 2
        return Bbox(lonC - halfLon, latC - halfLat, lonC + halfLon, latC + halfLat)
    }
}

const val MAX_BBOX_DEG = 0.2

/** Facilities for one map area. [truncated] means the server left some out, so zooming in shows more. */
data class FacilityPage(val layers: MapLayers, val truncated: Boolean)

/** Facilities on the home map: the server's `/api/pois` when configured, otherwise example data. */
interface FacilityRepository {
    /** Throws [ApiException]. */
    suspend fun load(mode: UserMode, extras: Set<String>, bbox: Bbox): FacilityPage

    /** Crosswalk positions in [bbox], for marking the ones a route crosses. Throws [ApiException]. */
    suspend fun crosswalks(bbox: Bbox): List<LatLon> = emptyList()

    /** True when the facilities are made up (no server). */
    val isExample: Boolean

    companion object {
        fun create(): FacilityRepository = if (ServerApi.isConfigured) ServerFacilityRepository() else FakeFacilityRepository()
    }
}

class ServerFacilityRepository : FacilityRepository {
    override val isExample = false

    override suspend fun load(mode: UserMode, extras: Set<String>, bbox: Bbox): FacilityPage {
        val shown = mode.shownKinds(extras)
        val kinds = serverKinds(shown)
        if (kinds.isEmpty()) return FacilityPage(MapLayers(emptyList(), emptyList()), truncated = false)
        val b = bbox.clamped()
        suspend fun fetch(kinds: Set<String>) = ServerApi.get(
            "/api/pois",
            mapOf(
                "bbox" to "%.6f,%.6f,%.6f,%.6f".format(java.util.Locale.US, b.west, b.south, b.east, b.north),
                "kinds" to kinds.sorted().joinToString(","),
                // The default is 500; the server sends the ones nearest the middle first.
                "limit" to 1000,
            ),
        )
        val json = try {
            fetch(kinds)
        } catch (e: ApiException) {
            // A server not yet updated rejects subway entrances; show the rest instead of nothing.
            if (e.code == "BAD_PARAM" && "subway_entrance" in e.message) fetch(kinds - "subway_entrance") else throw e
        }
        return parsePois(json, shown, mode)
    }

    override suspend fun crosswalks(bbox: Bbox): List<LatLon> {
        val b = bbox.clamped()
        val json = ServerApi.get(
            "/api/pois",
            mapOf(
                "bbox" to "%.6f,%.6f,%.6f,%.6f".format(java.util.Locale.US, b.west, b.south, b.east, b.north),
                "kinds" to "crosswalk",
                "limit" to 1000,
            ),
        )
        val pois = json.optJSONArray("pois") ?: return emptyList()
        return (0 until pois.length()).map { i -> pois.getJSONObject(i).let { LatLon(it.getDouble("lat"), it.getDouble("lon")) } }
    }
}

/** The area around a route with a small margin, for loading what lies along it. */
fun bboxAround(path: List<LatLon>, marginDeg: Double = 0.0005) = Bbox(
    west = path.minOf { it.lon } - marginDeg,
    south = path.minOf { it.lat } - marginDeg,
    east = path.maxOf { it.lon } + marginDeg,
    north = path.maxOf { it.lat } + marginDeg,
)

class FakeFacilityRepository : FacilityRepository {
    override val isExample = true

    override suspend fun load(mode: UserMode, extras: Set<String>, bbox: Bbox) =
        FacilityPage(FakeFacilities.layersFor(mode, extras), truncated = false)
}

/** Server `kinds` needed to show these facility kinds. */
internal fun serverKinds(shown: Set<FacilityKind>): Set<String> = shown.mapNotNull {
    when (it) {
        FacilityKind.CCTV -> "cctv"
        FacilityKind.POLICE -> "police"
        FacilityKind.GUARD_HOUSE -> "guard_house"
        FacilityKind.EMERGENCY_BELL -> "bell"
        FacilityKind.CONVENIENCE_STORE -> "store"
        FacilityKind.HEAT_SHELTER, FacilityKind.COLD_SHELTER -> "shelter"
        FacilityKind.TOILET, FacilityKind.ACCESSIBLE_TOILET -> "toilet"
        FacilityKind.CURB_RAMP -> "crosswalk"
        FacilityKind.ELEVATOR, FacilityKind.OVERPASS -> "footbridge"
        FacilityKind.STAIRS -> "stairs"
        FacilityKind.SCHOOL_ZONE -> "school_zone"
        FacilityKind.SUBWAY -> "subway"
        FacilityKind.SUBWAY_ENTRANCE -> "subway_entrance"
    }
}.toSet()

/**
 * Which map kind a server place is drawn as, or null to leave it out.
 * A wheelchair toilet counts as a toilet too, so it is drawn as whichever of the two is [shown].
 */
internal fun poiKind(kind: String, sub: String?, wheelchair: Boolean?, shown: Set<FacilityKind>): FacilityKind? {
    val candidates = when (kind) {
        "cctv" -> listOf(FacilityKind.CCTV)
        "police" -> listOf(FacilityKind.POLICE)
        "guard_house" -> listOf(FacilityKind.GUARD_HOUSE)
        "bell" -> listOf(FacilityKind.EMERGENCY_BELL)
        "store" -> listOf(FacilityKind.CONVENIENCE_STORE)
        "shelter" -> when (sub) {
            "무더위쉼터" -> listOf(FacilityKind.HEAT_SHELTER)
            "한파쉼터" -> listOf(FacilityKind.COLD_SHELTER)
            else -> emptyList()
        }
        "toilet" -> if (wheelchair == true) listOf(FacilityKind.ACCESSIBLE_TOILET, FacilityKind.TOILET) else listOf(FacilityKind.TOILET)
        // Lowered curbs are shown; unknown ones as a gray dot (see [poiLook]); known high curbs are left out.
        "crosswalk" -> if (wheelchair != false) listOf(FacilityKind.CURB_RAMP) else emptyList()
        "footbridge" -> if (wheelchair == true) listOf(FacilityKind.ELEVATOR) else listOf(FacilityKind.OVERPASS)
        "stairs" -> listOf(FacilityKind.STAIRS)
        "school_zone" -> listOf(FacilityKind.SCHOOL_ZONE)
        "subway" -> listOf(FacilityKind.SUBWAY)
        "subway_entrance" -> listOf(FacilityKind.SUBWAY_ENTRANCE)
        else -> emptyList()
    }
    return candidates.firstOrNull { it in shown }
}

/** Modes that care about elevators: stations get an elevator badge, entrances with one are highlighted. */
private val ELEVATOR_MODES = setOf(UserMode.SENIOR, UserMode.DISABLED)

/**
 * Tag text and look for one place, per the team's rules (11/5).
 * For a station, [wheelchair] says whether an exit has an elevator down from the street,
 * and [count] is how many such exits there are.
 */
internal fun poiLabel(kind: FacilityKind, wheelchair: Boolean?, count: Int?, mode: UserMode): Pair<String?, FacilityLook> =
    when (kind) {
        FacilityKind.CURB_RAMP -> null to if (wheelchair == true) FacilityLook.NORMAL else FacilityLook.DOT
        FacilityKind.SUBWAY -> when {
            mode !in ELEVATOR_MODES -> null to FacilityLook.NORMAL
            wheelchair == true -> (if (count != null && count > 0) "지하철역 · 엘리베이터 출구 ${count}곳" else "지하철역 · 엘리베이터 출구 있음") to FacilityLook.NORMAL
            wheelchair == false -> "지하철역 · 엘리베이터 출구 없음" to
                if (mode == UserMode.DISABLED) FacilityLook.GRAY else FacilityLook.NORMAL
            else -> "지하철역 · 엘리베이터 확인 안 됨" to FacilityLook.NORMAL
        }
        FacilityKind.SUBWAY_ENTRANCE -> when {
            mode !in ELEVATOR_MODES -> null to FacilityLook.NORMAL
            wheelchair == true -> "역 입구 · 엘리베이터" to FacilityLook.NORMAL
            // Not confirmed, so grayed out to let the elevator entrances stand out.
            else -> null to FacilityLook.GRAY
        }
        else -> null to FacilityLook.NORMAL
    }

internal fun parsePois(json: JSONObject, shown: Set<FacilityKind>, mode: UserMode): FacilityPage {
    val pois = json.optJSONArray("pois")
    val facilities = if (pois == null) emptyList() else (0 until pois.length()).mapNotNull { i ->
        val p = pois.getJSONObject(i)
        val wheelchair = if (p.isNull("wheelchair")) null else p.optBoolean("wheelchair")
        val kind = poiKind(
            p.optString("kind"),
            p.optString("sub").takeUnless { p.isNull("sub") },
            wheelchair,
            shown,
        ) ?: return@mapNotNull null
        val (text, look) = poiLabel(kind, wheelchair, if (p.isNull("count")) null else p.optInt("count"), mode)
        Facility(
            kind, p.getDouble("lat"), p.getDouble("lon"), text = text, look = look,
            name = p.optString("name").takeUnless { p.isNull("name") || it.isBlank() },
            detail = p.optString("detail").takeUnless { p.isNull("detail") || it.isBlank() },
        )
    }
    return FacilityPage(MapLayers(facilities, zones = emptyList()), truncated = json.optBoolean("truncated"))
}
