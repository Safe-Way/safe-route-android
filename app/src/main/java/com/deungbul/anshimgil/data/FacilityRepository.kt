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
        val json = ServerApi.get(
            "/api/pois",
            mapOf(
                "bbox" to "%.6f,%.6f,%.6f,%.6f".format(java.util.Locale.US, b.west, b.south, b.east, b.north),
                "kinds" to kinds.sorted().joinToString(","),
            ),
        )
        return parsePois(json, shown)
    }
}

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
        // Only crossings known to have lowered curbs; the rest are unknown.
        "crosswalk" -> if (wheelchair == true) listOf(FacilityKind.CURB_RAMP) else emptyList()
        "footbridge" -> if (wheelchair == true) listOf(FacilityKind.ELEVATOR) else listOf(FacilityKind.OVERPASS)
        "stairs" -> listOf(FacilityKind.STAIRS)
        "school_zone" -> listOf(FacilityKind.SCHOOL_ZONE)
        else -> emptyList()
    }
    return candidates.firstOrNull { it in shown }
}

internal fun parsePois(json: JSONObject, shown: Set<FacilityKind>): FacilityPage {
    val pois = json.optJSONArray("pois")
    val facilities = if (pois == null) emptyList() else (0 until pois.length()).mapNotNull { i ->
        val p = pois.getJSONObject(i)
        val kind = poiKind(
            p.optString("kind"),
            p.optString("sub").takeUnless { p.isNull("sub") },
            if (p.isNull("wheelchair")) null else p.optBoolean("wheelchair"),
            shown,
        ) ?: return@mapNotNull null
        Facility(kind, p.getDouble("lat"), p.getDouble("lon"))
    }
    return FacilityPage(MapLayers(facilities, zones = emptyList()), truncated = json.optBoolean("truncated"))
}
