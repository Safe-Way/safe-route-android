package com.deungbul.anshimgil.data

/**
 * Facility types shown on the map. [color] is the label's border and text color (ARGB).
 * [avoid] marks places the route steers away from rather than toward, so they are never offered as help.
 */
enum class FacilityKind(val label: String, val color: Long, val avoid: Boolean = false, val title: String = label) {
    CCTV("CCTV", 0xFF1F6B42),
    POLICE("파출소", 0xFF1D4ED8, title = "경찰서·파출소"),
    GUARD_HOUSE("지킴이집", 0xFFC2410C, title = "아동안전지킴이집"),
    EMERGENCY_BELL("비상벨", 0xFFB42318),
    CONVENIENCE_STORE("편의점", 0xFF0F766E),
    HEAT_SHELTER("무더위쉼터", 0xFFC2410C),
    COLD_SHELTER("한파쉼터", 0xFF1D4ED8),
    TOILET("화장실", 0xFF0F766E, title = "공중화장실"),
    ACCESSIBLE_TOILET("장애인 화장실", 0xFF7C2D12),
    CURB_RAMP("턱 낮춘 횡단보도", 0xFF1D4ED8, title = "보도턱 낮춘 횡단보도"),
    /** Overpass with an elevator. */
    ELEVATOR("엘리베이터 육교", 0xFF6D28D9, title = "엘리베이터 있는 육교"),
    OVERPASS("육교", 0xFFB42318, avoid = true, title = "육교 (피할 곳)"),
    STAIRS("계단", 0xFFB42318, avoid = true, title = "계단 (피할 곳)"),
    SUBWAY("지하철역", 0xFF1D4ED8),
    SUBWAY_ENTRANCE("역 입구", 0xFF1D4ED8),
    /** A child protection zone from the server, which gives a point rather than an area. Shown with [CHILD_ZONE_KEY]. */
    SCHOOL_ZONE("보호구역", 0xFFA16207, title = "어린이 보호구역"),
}

/** How a facility tag is drawn: normal, grayed out (e.g. a station without elevators), or a small gray dot (e.g. curb unknown). */
enum class FacilityLook { NORMAL, GRAY, DOT }

/**
 * [route] is the route it lies along; null means both routes or not route-specific.
 * [text] replaces the kind's label, e.g. "지하철역 · 엘리베이터 3대".
 */
data class Facility(
    val kind: FacilityKind,
    val lat: Double,
    val lon: Double,
    val route: RouteType? = null,
    val text: String? = null,
    val look: FacilityLook = FacilityLook.NORMAL,
    /** The place's own name, e.g. "문화전당역", when the server gives one. */
    val name: String? = null,
    /** The server's one-line summary, e.g. which subway exits have elevators; shown when the tag is tapped. */
    val detail: String? = null,
    /** A station or exit with an elevator from the street, marked with a small elevator icon on its tag. */
    val elevator: Boolean = false,
) {
    val label: String get() = text ?: kind.label
}

/** An area drawn on the map, such as a child protection zone. Points go around the edge in order. */
data class Zone(val label: String, val fillColor: Long, val strokeColor: Long, val points: List<Pair<Double, Double>>)

/** What one mode shows on the map. */
data class MapLayers(val facilities: List<Facility>, val zones: List<Zone>)

/** Shown in every mode: CCTV, police, emergency bells, convenience stores (team list, 11/4) and subway stations and entrances (11/5). */
val COMMON_FACILITY_KINDS: Set<FacilityKind> = setOf(
    FacilityKind.CCTV, FacilityKind.POLICE, FacilityKind.EMERGENCY_BELL, FacilityKind.CONVENIENCE_STORE,
    FacilityKind.SUBWAY, FacilityKind.SUBWAY_ENTRANCE,
)

/** Which facility types each mode shows first on the map (issue #3, team list 11/4). Child protection zones are drawn as areas. */
val UserMode.facilityKinds: Set<FacilityKind>
    get() = when (this) {
        UserMode.CHILD -> setOf(FacilityKind.GUARD_HOUSE)
        UserMode.SENIOR -> setOf(FacilityKind.HEAT_SHELTER, FacilityKind.COLD_SHELTER, FacilityKind.TOILET, FacilityKind.STAIRS)
        UserMode.DISABLED -> setOf(
            FacilityKind.ACCESSIBLE_TOILET, FacilityKind.CURB_RAMP, FacilityKind.ELEVATOR,
            FacilityKind.OVERPASS, FacilityKind.STAIRS,
        )
        UserMode.GENERAL -> emptySet()
    } + COMMON_FACILITY_KINDS

/** Places a walker can go to or use to get help (the server's "help" group plus emergency bells); CCTV only watches. */
val HELP_KINDS: Set<FacilityKind> =
    setOf(FacilityKind.POLICE, FacilityKind.GUARD_HOUSE, FacilityKind.CONVENIENCE_STORE, FacilityKind.EMERGENCY_BELL)

/** Settings key for child protection zones, which are areas rather than a [FacilityKind]. */
const val CHILD_ZONE_KEY = "CHILD_ZONE"

/** Something a user can add to the map in settings: a [FacilityKind] name or [CHILD_ZONE_KEY]. */
data class MapItem(val key: String, val title: String)

/** Items this mode does not show by default, in the order settings lists them. */
val UserMode.optionalMapItems: List<MapItem>
    get() = buildList {
        if (this@optionalMapItems != UserMode.CHILD) add(MapItem(CHILD_ZONE_KEY, "어린이 보호구역"))
        FacilityKind.entries.filter { it !in facilityKinds && it != FacilityKind.SCHOOL_ZONE }.forEach { add(MapItem(it.name, it.title)) }
    }

/** Facility kinds shown for this mode plus the ones the user added in settings. */
fun UserMode.shownKinds(extras: Set<String>): Set<FacilityKind> =
    facilityKinds + FacilityKind.entries.filter { it.name in extras } +
        (if (showsChildZones(extras)) setOf(FacilityKind.SCHOOL_ZONE) else emptySet())

fun UserMode.showsChildZones(extras: Set<String>): Boolean = this == UserMode.CHILD || CHILD_ZONE_KEY in extras
