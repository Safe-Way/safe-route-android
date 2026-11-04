package com.deungbul.anshimgil.data

/**
 * Facility types shown on the map. [color] is the label's border and text color (ARGB).
 * [avoid] marks places the route steers away from rather than toward, so they are never offered as help.
 */
enum class FacilityKind(val label: String, val color: Long, val avoid: Boolean = false) {
    CCTV("CCTV", 0xFF1F6B42),
    POLICE("파출소", 0xFF1D4ED8),
    GUARD_HOUSE("지킴이집", 0xFFC2410C),
    EMERGENCY_BELL("비상벨", 0xFFB42318),
    CONVENIENCE_STORE("편의점", 0xFF0F766E),
    HEAT_SHELTER("무더위쉼터", 0xFFC2410C),
    COLD_SHELTER("한파쉼터", 0xFF1D4ED8),
    TOILET("화장실", 0xFF0F766E),
    ACCESSIBLE_TOILET("장애인 화장실", 0xFF7C2D12),
    CURB_RAMP("턱 낮춘 횡단보도", 0xFF1D4ED8),
    /** Overpass with an elevator. */
    ELEVATOR("엘리베이터 육교", 0xFF6D28D9),
    OVERPASS("육교", 0xFFB42318, avoid = true),
    STAIRS("계단", 0xFFB42318, avoid = true),
}

data class Facility(val kind: FacilityKind, val lat: Double, val lon: Double)

/** An area drawn on the map, such as a child protection zone. Points go around the edge in order. */
data class Zone(val label: String, val fillColor: Long, val strokeColor: Long, val points: List<Pair<Double, Double>>)

/** What one mode shows on the map. */
data class MapLayers(val facilities: List<Facility>, val zones: List<Zone>)

/** Shown in every mode: CCTV, police, emergency bells and convenience stores (team list, 11/4). */
val COMMON_FACILITY_KINDS: Set<FacilityKind> =
    setOf(FacilityKind.CCTV, FacilityKind.POLICE, FacilityKind.EMERGENCY_BELL, FacilityKind.CONVENIENCE_STORE)

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
