package com.deungbul.anshimgil.data

/** Facility types shown on the map. [color] is the label's border and text color (ARGB). */
enum class FacilityKind(val label: String, val color: Long) {
    CCTV("CCTV", 0xFF1F6B42),
    POLICE("파출소", 0xFF1D4ED8),
    GUARD_HOUSE("지킴이집", 0xFFC2410C),
    EMERGENCY_BELL("비상벨", 0xFFB42318),
    CONVENIENCE_STORE("편의점", 0xFF0F766E),
    HEAT_SHELTER("무더위쉼터", 0xFFC2410C),
    COLD_SHELTER("한파쉼터", 0xFF1D4ED8),
    TOILET("화장실", 0xFF0F766E),
    ACCESSIBLE_TOILET("장애인 화장실", 0xFF7C2D12),
    ELEVATOR("엘리베이터", 0xFF6D28D9),
    STAIRS("계단", 0xFFB42318),
}

data class Facility(val kind: FacilityKind, val lat: Double, val lon: Double)

/** An area drawn on the map, such as a child protection zone. Points go around the edge in order. */
data class Zone(val label: String, val fillColor: Long, val strokeColor: Long, val points: List<Pair<Double, Double>>)

/** What one mode shows on the map. */
data class MapLayers(val facilities: List<Facility>, val zones: List<Zone>)

/** Which facility types each mode shows (issue #3). Every mode shows CCTV, as the team agreed. */
val UserMode.facilityKinds: Set<FacilityKind>
    get() = when (this) {
        UserMode.CHILD -> setOf(FacilityKind.GUARD_HOUSE)
        UserMode.SENIOR -> setOf(FacilityKind.HEAT_SHELTER, FacilityKind.COLD_SHELTER, FacilityKind.TOILET)
        UserMode.DISABLED -> setOf(FacilityKind.ACCESSIBLE_TOILET, FacilityKind.ELEVATOR, FacilityKind.STAIRS)
        UserMode.GENERAL -> setOf(FacilityKind.EMERGENCY_BELL, FacilityKind.POLICE, FacilityKind.CONVENIENCE_STORE)
    } + FacilityKind.CCTV
