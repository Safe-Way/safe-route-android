package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SidewalkLineOffTest {
    @Test
    fun theLineIsDrawnAsTheServerSendsIt() {
        val path = listOf(LatLon(35.146, 126.92), LatLon(35.1465, 126.92), LatLon(35.147, 126.92))
        val r = RouteOption(RouteType.SAFE, 100, 2, path, sidewalks = listOf(Sidewalk(0, 2, SidewalkSide.RIGHT, offsetM = 8.0)))
        assertEquals(path, drawnPath(r))
        assertEquals(path[1], drawnPoint(r, path[1], 1))
    }
}
