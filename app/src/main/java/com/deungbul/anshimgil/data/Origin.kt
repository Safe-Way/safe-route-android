package com.deungbul.anshimgil.data

/** Where a route starts. [fromGps] is false when the demo point stands in for the phone's position. */
data class Origin(val point: LatLon, val label: String, val fromGps: Boolean)

/** Used when the phone's position is unknown or outside Gwangju: Chosun University. */
val DEMO_ORIGIN_POINT = LatLon(35.1404, 126.9318)

/** Rough box around Gwangju. The server answers OUT_OF_AREA outside the city, so the demo point is used instead. */
fun isInGwangju(p: LatLon): Boolean = p.lat in 35.05..35.26 && p.lon in 126.64..127.02

/** The phone's position if it is in Gwangju, otherwise the demo point with a label saying so. */
fun chooseOrigin(fix: LatLon?): Origin = when {
    fix == null -> Origin(DEMO_ORIGIN_POINT, "조선대 (위치 모름)", fromGps = false)
    !isInGwangju(fix) -> Origin(DEMO_ORIGIN_POINT, "조선대 (광주 밖)", fromGps = false)
    else -> Origin(fix, "현재 위치", fromGps = true)
}
