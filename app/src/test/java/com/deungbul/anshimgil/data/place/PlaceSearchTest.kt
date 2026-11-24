package com.deungbul.anshimgil.data.place

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.deungbul.anshimgil.data.route.LatLon

class PlaceSearchTest {
    private val here = LatLon(35.1415, 126.9300) // 조선대
    private fun p(id: String, name: String, lat: Double, lon: Double, d: Int? = null) = Place(id, name, "", lat, lon, d)

    @Test fun aStationFarAwayIsListedFirst() = runBlocking {
        // 11/13 폰: 조선대에서 "상무역"을 찾으면 근처 10곳에 상무역이 없었다.
        val near = listOf(p("1", "투루카 쌍촌역 1번출구", 35.1500, 126.8650, 6100))
        val anywhere = listOf(p("2", "상무역 광주1호선", 35.1527, 126.8510), p("3", "상무역 1번출구", 35.1530, 126.8500))
        val r = mergedSearch("상무역", near, anywhere, here)
        assertEquals("상무역 광주1호선", r.first().name)
        assertTrue(r.first().distanceM!! in 7000..7600)
        assertEquals(3, r.size)
    }

    @Test fun aChainNearbyFirstNotTheFarBranch() = runBlocking {
        val near = listOf(p("1", "다이소 조선대점", 35.1430, 126.9290, 200), p("2", "다이소 충장점", 35.1480, 126.9180, 1300))
        val anywhere = listOf(p("3", "다이소 첨단점", 35.2200, 126.8500), p("1", "다이소 조선대점", 35.1430, 126.9290))
        val r = mergedSearch("다이소", near, anywhere, here)
        assertEquals(listOf("다이소 조선대점", "다이소 충장점", "다이소 첨단점"), r.map { it.name })
    }

    @Test fun outsideGwangjuIsLeftOut() = runBlocking {
        val anywhere = listOf(p("1", "광천버스터미널", 36.50, 126.62), p("2", "유스퀘어광주종합버스터미널", 35.1604, 126.8793))
        val r = mergedSearch("광천터미널", emptyList(), anywhere, here)
        assertEquals(listOf("유스퀘어광주종합버스터미널"), r.map { it.name })
    }

    @Test fun theCityHallUnderItsNewName() = runBlocking {
        val near = listOf(p("1", "광주광역시청새마을금고 본점", 35.1600, 126.8520, 7000))
        val anywhere = listOf(p("2", "전남광주통합특별시청 광주청사", 35.1601, 126.8514), p("1", "광주광역시청새마을금고 본점", 35.1600, 126.8520))
        assertEquals("전남광주통합특별시청 광주청사", mergedSearch("광주광역시청", near, anywhere, here).first().name)
    }

    @Test fun busLinesStayOnceAndInTheServerOrder() = runBlocking {
        // 채림 #258: "버스 03"은 위치가 있든 없든 같은 노선 목록을 준다. 두 번 보이지 않고 서버 순서 그대로.
        val lines = listOf(
            p("line:1", "03번 버스", 35.2000, 126.8500),
            p("line:2", "03-1번 버스", 35.1600, 126.8800),
        ).map { it.copy(address = "첨단 ↔ 조선대 · 첫차 05:30 · 막차 22:30") }
        val r = mergedSearch("버스 03", lines, lines, here)
        assertEquals(listOf("03번 버스", "03-1번 버스"), r.map { it.name })
        assertTrue(r.all { isBusResult(it) })
    }
}
