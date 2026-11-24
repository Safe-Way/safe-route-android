package com.deungbul.anshimgil.data.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import com.deungbul.anshimgil.data.place.Place
import com.deungbul.anshimgil.data.place.mergedSearch
import com.deungbul.anshimgil.data.route.LatLon

class HereAddressTest {
    @Test fun addressAndBuilding() {
        assertEquals("광주 동구 필문대로 309 (조선대학교 본관)", hereLine(Place(null, "조선대학교 본관", "광주 동구 필문대로 309", 35.1, 126.9, null)))
        assertEquals("광주 동구 필문대로 309", hereLine(Place(null, "광주 동구 필문대로 309", "광주 동구 필문대로 309", 35.1, 126.9, null)))
        assertEquals("광주 동구 서석동 375", hereLine(Place(null, "", "광주 동구 서석동 375", 35.1, 126.9, null)))
    }

    @Test fun searchingForHereGivesOnlyWhereTheWalkerIs() = runBlocking {
        // 채림 #248: "현재 위치"를 치면 서버가 id "here" 한 곳만 준다. 위치 없는 이름 검색의 엉뚱한 결과("현재빌라")는 빼요.
        val here = Place("here", "현재 위치", "광주 동구 필문대로 309 (조선대학교 본관)", 35.1415, 126.93, 0)
        val r = mergedSearch("현재 위치", listOf(here), listOf(Place("1", "현재빌라", "", 35.15, 126.85, null)), LatLon(35.1415, 126.93))
        assertEquals(listOf(here), r)
    }
}
