package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransitGuideTest {
    private fun stop(name: String?) = TransitStop(name, LatLon(35.14, 126.92))
    private fun walk(m: Int, path: List<LatLon> = emptyList()) =
        TransitLeg(LegType.WALK, m, m / 60, stop(null), stop("문화전당역"), emptyList(), null, null, path, emptyList(), emptyList())
    private val bus = TransitLeg(
        LegType.BUS, 3200, 12, stop("문화전당역"), stop("남광주역"), listOf("21", "518"), null, 6, emptyList(),
        listOf(BusComing("518", 7, 3, false), BusComing("21", 3, 2, true)), emptyList(),
    )
    private val subway = TransitLeg(
        LegType.SUBWAY, 2000, 5, stop("문화전당역"), stop("남광주역"), listOf("광주 1호선"), "녹동", 2, emptyList(),
        emptyList(), listOf(Train("녹동행", 4, null)),
    )

    @Test
    fun tinyWalksBetweenRidesAreSkipped() {
        val route = TransitRoute(30, 1400, 1, 500, listOf(walk(300), bus, walk(10), subway, walk(200)))
        assertEquals(listOf("걷기", "21번", "광주 1호선", "걷기"), guideLegs(route).map(::legLabel))
    }

    @Test
    fun rideWords() {
        assertEquals("21번, 518번 버스를 타요", rideTitle(bus))
        assertEquals("문화전당역에서 타서 6정거장 뒤 남광주역에서 내려요", rideDetail(bus))
        assertEquals("21번 저상버스 3분 뒤 도착 (찾을 때 기준)", nextRideText(bus))
        assertEquals("광주 1호선 녹동 방면을 타요", rideTitle(subway))
        assertEquals("녹동행 4분 뒤 도착 (찾을 때 기준)", nextRideText(subway))
    }

    @Test
    fun walkGoesToTheStopOrTheDestination() {
        assertEquals("문화전당역까지 걸어요", walkTitle(walk(300), last = false, destinationName = "집"))
        assertEquals("집까지 걸어요", walkTitle(walk(300), last = true, destinationName = "집"))
    }

    @Test
    fun walkWithoutALineNeedsOurOwnRoute() {
        assertNull(walkResult(walk(300)))
        val line = listOf(LatLon(35.14, 126.91), LatLon(35.141, 126.912))
        assertEquals(line, walkResult(walk(300, line))!!.routes.single().path)
    }
}
