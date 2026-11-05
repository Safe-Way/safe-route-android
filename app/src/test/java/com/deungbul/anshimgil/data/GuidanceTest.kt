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

    @Test
    fun cctvIsNotAHelpPlace() {
        // A CCTV right beside the walker and a guard house farther away: only the guard house is help.
        val cctv = Facility(FacilityKind.CCTV, 35.14041, 126.9318)
        val guard = Facility(FacilityKind.GUARD_HOUSE, 35.1403, 126.9314)
        assertEquals(FacilityKind.GUARD_HOUSE, Guidance(path, listOf(cctv, guard)).update(start).nearestHelp?.kind)
        assertEquals(null, Guidance(path, listOf(cctv)).update(start).nearestHelp)
    }

    @Test
    fun helpListShowsPlacesAheadNearestFirst() {
        // A straight route north, about 220 m long.
        val path = listOf(LatLon(35.1400, 126.9300), LatLon(35.1420, 126.9300))
        val store = Facility(FacilityKind.CONVENIENCE_STORE, 35.1415, 126.93015) // ~165 m along, a little east
        val police = Facility(FacilityKind.POLICE, 35.1405, 126.92985) // ~55 m along, a little west
        val cctv = Facility(FacilityKind.CCTV, 35.1410, 126.9300) // not a help place
        val far = Facility(FacilityKind.EMERGENCY_BELL, 35.1410, 126.9320) // ~180 m off the route
        val guidance = Guidance(path, listOf(store, police, cctv, far))

        val list = guidance.helpAhead(0.0)
        assertEquals(listOf(FacilityKind.POLICE, FacilityKind.CONVENIENCE_STORE), list.map { it.kind })
        assertEquals(Turn.LEFT, list[0].side)
        assertEquals(Turn.RIGHT, list[1].side)
        // Walked past the police box: only the store is left, about 55 m ahead.
        val later = guidance.helpAhead(110.0)
        assertEquals(listOf(FacilityKind.CONVENIENCE_STORE), later.map { it.kind })
        assertTrue(later[0].aheadM in 45..65)
    }
}
