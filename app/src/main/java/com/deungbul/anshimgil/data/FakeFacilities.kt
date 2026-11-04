package com.deungbul.anshimgil.data

/**
 * Made-up facilities around Chosun University so the map can be built before the server API exists.
 * Replace with the server's facility API once it is ready. Positions are NOT real facility locations.
 */
object FakeFacilities {
    private val all = listOf(
        Facility(FacilityKind.CCTV, 35.1418, 126.9310),
        Facility(FacilityKind.CCTV, 35.1395, 126.9330),
        Facility(FacilityKind.CCTV, 35.1385, 126.9302),
        Facility(FacilityKind.POLICE, 35.1430, 126.9345),
        Facility(FacilityKind.GUARD_HOUSE, 35.1392, 126.9298),
        Facility(FacilityKind.GUARD_HOUSE, 35.1412, 126.9342),
        Facility(FacilityKind.EMERGENCY_BELL, 35.1408, 126.9300),
        Facility(FacilityKind.CONVENIENCE_STORE, 35.1380, 126.9325),
        Facility(FacilityKind.HEAT_SHELTER, 35.1388, 126.9295),
        Facility(FacilityKind.HEAT_SHELTER, 35.1375, 126.9318),
        Facility(FacilityKind.COLD_SHELTER, 35.1420, 126.9338),
        Facility(FacilityKind.TOILET, 35.1404, 126.9312),
        Facility(FacilityKind.ACCESSIBLE_TOILET, 35.1398, 126.9322),
        Facility(FacilityKind.ACCESSIBLE_TOILET, 35.1378, 126.9300),
        Facility(FacilityKind.ELEVATOR, 35.1425, 126.9328),
        Facility(FacilityKind.STAIRS, 35.1414, 126.9320),
        Facility(FacilityKind.CURB_RAMP, 35.1390, 126.9335),
        Facility(FacilityKind.OVERPASS, 35.1432, 126.9318),
    )

    private val childZone = Zone(
        label = "어린이 보호구역",
        fillColor = 0x38EAB308,
        strokeColor = 0xFFA16207,
        points = listOf(
            35.1400 to 126.9290, 35.1400 to 126.9312, 35.1384 to 126.9312, 35.1384 to 126.9290,
        ),
    )

    /** [extras] are the items the user added in settings (see [optionalMapItems]). */
    fun layersFor(mode: UserMode, extras: Set<String> = emptySet()): MapLayers {
        val kinds = mode.shownKinds(extras)
        return MapLayers(
            facilities = all.filter { it.kind in kinds },
            zones = if (mode.showsChildZones(extras)) listOf(childZone) else emptyList(),
        )
    }
}
