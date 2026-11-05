package com.deungbul.anshimgil.data

/** Why the demo point stands in for the phone's position. [message] is shown on the route screen. */
enum class OriginIssue(val short: String, val message: String) {
    NO_PERMISSION("위치 권한 없음", "위치 권한이 없어서 조선대에서 출발해요."),
    LOCATION_OFF("위치 꺼짐", "휴대폰 위치가 꺼져 있어서 조선대에서 출발해요."),
    NO_FIX("위치 모름", "현재 위치를 찾지 못해서 조선대에서 출발해요."),
    OUTSIDE_GWANGJU("광주 밖", "광주 밖이라 조선대에서 출발해요. 길찾기는 광주 안에서만 돼요."),
}

/** Where a route starts. [issue] is set when the demo point stands in for the phone's position. */
data class Origin(val point: LatLon, val label: String, val issue: OriginIssue? = null) {
    val fromGps: Boolean get() = issue == null
}

/** Used when the phone's position is unknown or outside Gwangju: Chosun University. */
val DEMO_ORIGIN_POINT = LatLon(35.1404, 126.9318)

/** Rough box around Gwangju. The server answers OUT_OF_AREA outside the city, so the demo point is used instead. */
fun isInGwangju(p: LatLon): Boolean = p.lat in 35.05..35.26 && p.lon in 126.64..127.02

/**
 * The phone's position if it is in Gwangju, otherwise the demo point with the reason.
 * [whyNoFix] says why [fix] is null (no permission, location off, or simply not found).
 */
fun chooseOrigin(fix: LatLon?, whyNoFix: OriginIssue = OriginIssue.NO_FIX): Origin {
    val issue = when {
        fix == null -> whyNoFix
        !isInGwangju(fix) -> OriginIssue.OUTSIDE_GWANGJU
        else -> null
    }
    return if (issue == null) Origin(fix!!, "현재 위치") else Origin(DEMO_ORIGIN_POINT, "조선대 (${issue.short})", issue)
}
