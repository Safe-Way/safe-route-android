package com.deungbul.anshimgil.data.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.deungbul.anshimgil.data.place.Place
import com.deungbul.anshimgil.data.route.LatLon
import com.deungbul.anshimgil.data.route.RouteType
import com.deungbul.anshimgil.data.route.StopPlace

class GuideStoreTest {
    private val guide = SavedGuide(
        destination = Place("p1", "상무역 광주1호선", "서구", 35.1527, 126.8510, null),
        start = Place(null, "전남광주통합특별시청 광주청사", "", 35.1601, 126.8514, null),
        stops = listOf(StopPlace("CU 상무점", LatLon(35.155, 126.852))),
        type = RouteType.SAFE,
        arriveAt = "상무역 4번 출구 엘리베이터",
        from = LatLon(35.1601, 126.8514),
        to = LatLon(35.1530, 126.8505),
        savedAtMs = 1_000L,
    )

    @Test fun keptAndReadBack() {
        assertEquals(guide, savedGuideFromJson(savedGuideToJson(guide)))
    }

    @Test fun fromHereWithoutStopsOrExit() {
        val g = guide.copy(start = null, stops = emptyList(), arriveAt = null)
        val back = savedGuideFromJson(savedGuideToJson(g))
        assertNull(back.start)
        assertNull(back.arriveAt)
        assertEquals(emptyList<StopPlace>(), back.stops)
    }
}
