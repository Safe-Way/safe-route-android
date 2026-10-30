package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceTest {
    // Walk west about 110 m, then turn left (south) for about 110 m.
    private val start = LatLon(35.1404, 126.9318)
    private val corner = LatLon(35.1404, 126.9306)
    private val end = LatLon(35.1394, 126.9306)
    private val path = listOf(start, corner, end)

    @Test
    fun atStart_nextTurnIsLeftAtCorner() {
        val g = Guidance(path, emptyList()).update(start)
        assertEquals(Turn.LEFT, g.nextTurn)
        assertTrue("about 109 m to the corner, got ${g.metersToNextTurn}", g.metersToNextTurn in 100..120)
        assertEquals(0, g.offRouteM)
    }

    @Test
    fun afterCorner_headsToArrival() {
        val guidance = Guidance(path, emptyList())
        val g = guidance.atProgress(guidance.totalM - 50)
        assertEquals(Turn.ARRIVE, g.nextTurn)
        assertEquals(50, g.remainingM)
    }

    @Test
    fun offRoutePosition_isSnappedAndMeasured() {
        val g = Guidance(path, emptyList()).update(LatLon(35.1408, 126.9312)) // ~45 m north of the first leg
        assertTrue("off route ${g.offRouteM}", g.offRouteM in 35..55)
        assertEquals(35.1404, g.snapped.lat, 1e-5)
    }

    @Test
    fun nearestHelp_reportsSide() {
        // A guard house just south of the first leg: walking west, south is on the left.
        val help = Facility(FacilityKind.GUARD_HOUSE, 35.1403, 126.9314)
        val g = Guidance(path, listOf(help)).update(start)
        assertEquals(FacilityKind.GUARD_HOUSE, g.nearestHelp?.kind)
        assertEquals(Turn.LEFT, g.nearestHelp?.side)
    }
}
