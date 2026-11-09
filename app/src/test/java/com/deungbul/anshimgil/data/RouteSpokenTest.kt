package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteSpokenTest {
    @Test
    fun distancesAreSaidInWords() {
        assertEquals("825미터", spokenDistance(825))
        assertEquals("1.2킬로미터", spokenDistance(1234))
        assertEquals("2킬로미터", spokenDistance(2000))
    }

    @Test
    fun theRecommendationIsReadOut() {
        val result = RouteResult(
            conditions = emptyList(),
            routes = listOf(RouteOption(RouteType.SAFE, 825, 14, listOf(LatLon(35.14, 126.93), LatLon(35.141, 126.931)))),
            explanation = Explanation("계단을 피하고, 쉴 곳이 가까운 길이에요.", emptyList()),
            facilities = emptyList(),
            dataQuality = DataQuality(ok = true, message = null),
        )
        assertEquals(
            "안전한 길로 14분, 825미터예요. 계단을 피하고, 쉴 곳이 가까운 길이에요. 출발하려면 화면 아래 안내 시작을 눌러 주세요.",
            routeSpoken(result, RouteType.SAFE),
        )
    }
}
