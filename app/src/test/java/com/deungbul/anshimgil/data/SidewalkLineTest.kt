package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SidewalkLineTest {
    private val start = LatLon(35.1460, 126.9200)
    private fun north(m: Double) = LatLon(start.lat + m / 111_320.0, start.lon)
    private val path = listOf(north(0.0), north(50.0), north(100.0))
    private fun eastM(p: LatLon, q: LatLon) = (q.lon - p.lon) * 111_320.0 * Math.cos(Math.toRadians(p.lat))

    @Test
    fun walkingNorthTheRightPavementIsEast() {
        val r = RouteOption(RouteType.SAFE, 100, 2, path, sidewalks = listOf(Sidewalk(0, 2, SidewalkSide.RIGHT)))
        val drawn = drawnPath(r)
        assertEquals(6.0, eastM(path[1], drawn[1]), 0.1)
        assertEquals(path[1].lat, drawn[1].lat, 1e-9)
    }

    @Test
    fun leftIsWestAndBothOrUnknownStay() {
        val left = RouteOption(RouteType.SAFE, 100, 2, path, sidewalks = listOf(Sidewalk(0, 2, SidewalkSide.LEFT)))
        assertEquals(-6.0, eastM(path[1], drawnPath(left)[1]), 0.1)
        val both = RouteOption(RouteType.SAFE, 100, 2, path, sidewalks = listOf(Sidewalk(0, 2, SidewalkSide.BOTH)))
        assertEquals(path, drawnPath(both))
        val none = RouteOption(RouteType.SAFE, 100, 2, path)
        assertTrue(drawnPath(none) === none.path)
    }

    @Test
    fun theMarkerMovesWithTheLine() {
        val r = RouteOption(RouteType.SAFE, 100, 2, path, sidewalks = listOf(Sidewalk(0, 2, SidewalkSide.RIGHT)))
        assertEquals(6.0, eastM(north(70.0), drawnPoint(r, north(70.0), 1)), 0.1)
    }
}
