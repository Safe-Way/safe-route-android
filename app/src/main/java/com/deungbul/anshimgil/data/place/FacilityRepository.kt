package com.deungbul.anshimgil.data.place

import org.json.JSONObject
import com.deungbul.anshimgil.data.route.LatLon
import com.deungbul.anshimgil.data.route.stationName
import com.deungbul.anshimgil.data.server.ApiException
import com.deungbul.anshimgil.data.server.ServerApi
import com.deungbul.anshimgil.data.user.UserMode

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
        // An elevator footbridge is one whose sub says 승강기 (the team's rule, safe-route #244 map_layer); a ramp or a
        // wheelchair flag alone is not an elevator (11/13: 9 shown as elevators, 3 had one).
        "footbridge" -> if (sub?.contains("승강기") == true) listOf(FacilityKind.ELEVATOR) else listOf(FacilityKind.OVERPASS)
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
internal fun poiLabel(kind: FacilityKind, wheelchair: Boolean?, count: Int?, mode: UserMode, name: String? = null): Pair<String?, FacilityLook> =
    when (kind) {
        FacilityKind.CURB_RAMP -> null to if (wheelchair == true) FacilityLook.NORMAL else FacilityLook.DOT
        FacilityKind.SUBWAY -> when {
            mode !in ELEVATOR_MODES -> null to FacilityLook.NORMAL
            // The elevator shows as an icon on the tag; which exits have one is in the tap card.
            wheelchair == true -> "지하철역" to FacilityLook.NORMAL
            wheelchair == false -> "지하철역 · 엘리베이터 출구 없음" to
                if (mode == UserMode.DISABLED) FacilityLook.GRAY else FacilityLook.NORMAL
            else -> "지하철역 · 엘리베이터 확인 안 됨" to FacilityLook.NORMAL
        }
        FacilityKind.SUBWAY_ENTRANCE -> when {
            mode !in ELEVATOR_MODES -> exitNumber(name)?.let { "${it}번 출구" } to FacilityLook.NORMAL
            // With the exit number when the name has one ("남광주역 3번 출구"), so the walker knows which exit.
            wheelchair == true -> (exitNumber(name)?.let { "${it}번 출구" } ?: "역 입구") to FacilityLook.NORMAL
            // Not confirmed, so grayed out to let the elevator entrances stand out.
            else -> exitNumber(name)?.let { "${it}번 출구" } to FacilityLook.GRAY
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
        val name = p.optString("name").takeUnless { p.isNull("name") || it.isBlank() }
        val (text, look) = poiLabel(kind, wheelchair, if (p.isNull("count")) null else p.optInt("count"), mode, name)
        Facility(
            kind, p.getDouble("lat"), p.getDouble("lon"), text = text, look = look,
            name = name,
            detail = p.optString("detail").takeUnless { p.isNull("detail") || it.isBlank() },
            elevator = wheelchair == true && mode in ELEVATOR_MODES &&
                (kind == FacilityKind.SUBWAY || kind == FacilityKind.SUBWAY_ENTRANCE),
        )
    }
    // The server gives each child protection zone as the school's point, so the zone is drawn as the same
    // 300 m circle as on the route map and in guidance: an approximate area, not the legal boundary.
    val zones = facilities.filter { it.kind == FacilityKind.SCHOOL_ZONE }.map { schoolZoneCircle(it.lat, it.lon) }
    return FacilityPage(MapLayers(facilities, zones = zones), truncated = json.optBoolean("truncated"))
}

/** The exit number in a subway exit's name, e.g. 3 for "남광주역 3번 출구"; null when there is none. */
fun exitNumber(name: String?): Int? = name?.let { Regex("""(\d+)번\s*출구""").find(it)?.groupValues?.get(1)?.toIntOrNull() }

/**
 * The exits of [station] among [facilities], in number order, each as "3번 출구 · 엘리베이터 있음".
 * Exits are matched by name ("남광주역 3번 출구" belongs to "남광주역").
 */
fun stationExits(station: Facility, facilities: List<Facility>): List<String> {
    val stationName = station.name ?: return emptyList()
    return facilities
        .filter { it.kind == FacilityKind.SUBWAY_ENTRANCE && it.name?.startsWith(stationName) == true }
        .mapNotNull { exit -> exitNumber(exit.name)?.let { it to exit } }
        .distinctBy { it.first }
        .sortedBy { it.first }
        .map { (number, exit) ->
            val what = exit.detail?.substringAfter(" · ", "")?.takeIf { it.isNotBlank() }
                ?: if (exit.look == FacilityLook.GRAY) "엘리베이터 없음" else "엘리베이터 있음"
            "${number}번 출구 · $what"
        }
}

/** The area a child protection zone covers around its school, as the route engine counts it. */
private const val SCHOOL_ZONE_RADIUS_M = 300.0

/** A circle of [SCHOOL_ZONE_RADIUS_M] around a school, the same on every map (the server says to draw it so). */
fun schoolZoneCircle(lat: Double, lon: Double): Zone {
    val dLat = SCHOOL_ZONE_RADIUS_M / 111_320.0
    val dLon = SCHOOL_ZONE_RADIUS_M / (111_320.0 * kotlin.math.cos(Math.toRadians(lat)))
    return Zone(
        label = "어린이 보호구역",
        fillColor = 0x30EAB308,
        strokeColor = 0xFFA16207,
        points = (0 until 36).map { i ->
            val a = Math.toRadians(i * 10.0)
            lat + dLat * kotlin.math.sin(a) to lon + dLon * kotlin.math.cos(a)
        },
    )
}

