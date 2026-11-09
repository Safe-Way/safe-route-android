package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RideStopsTest {
    // Four stops 200 m apart going east.
    private val pts = (0..3).map { LatLon(35.0, 126.0 + it * 0.0022) }
    private val stops = pts.mapIndexed { i, p -> TransitStop("정류장$i", p) }
    private val bus = TransitLeg(
        LegType.BUS, 600, 5, stops.first(), TransitStop("시청", pts.last()), listOf("45"), null, 3,
        emptyList(), emptyList(), emptyList(), stops,
    )

    @Test
    fun nextStopMovesForwardOnly() {
        // At the stop where the walker got on: the next is the one after.
        assertEquals(1, nextStopIndex(pts, pts[0]))
        // Between stop 1 and 2, past 1.
        assertEquals(2, nextStopIndex(pts, LatLon(35.0, 126.0 + 0.0030)))
        // GPS jumps back: stays.
        assertEquals(2, nextStopIndex(pts, LatLon(35.0, 126.0 + 0.0005), before = 2))
        assertEquals(3, nextStopIndex(pts, pts[3]))
    }

    @Test
    fun stopsLeftWords() {
        assertEquals(3, stopsToAlight(stops, 1))
        assertEquals(1, stopsToAlight(stops, 3))
        assertEquals("내릴 곳까지 3정거장 남았어요", stopsLeftLine(bus, 3))
        assertEquals("다음 정거장 시청에서 내려요", stopsLeftLine(bus, 1))
        assertEquals("한 정거장 남았어요. 다음 정거장 시청에서 내릴 준비를 하세요.", oneStopLeftSpoken(bus))
        assertEquals("내릴 곳까지 3개 역 남았어요", stopsLeftLine(bus.copy(type = LegType.SUBWAY), 3))
    }
}
