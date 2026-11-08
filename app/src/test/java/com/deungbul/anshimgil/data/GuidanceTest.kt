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

    // Points given as meters east/north of a corner near 조선대.
    private fun m(east: Double, north: Double) = LatLon(35.14 + north / 111_320.0, 126.93 + east / (111_320.0 * kotlin.math.cos(Math.toRadians(35.14))))

    private fun turnsOf(g: Guidance): List<Pair<Int, Turn>> {
        val out = mutableListOf<Pair<Int, Turn>>()
        var p = 0.0
        while (p < g.totalM) {
            val s = g.atProgress(p)
            if (s.nextTurn == Turn.LEFT || s.nextTurn == Turn.RIGHT) {
                val at = (p + s.metersToNextTurn).toInt()
                if (out.none { kotlin.math.abs(it.first - at) <= 1 }) out += at to s.nextTurn
            }
            p += 1.0
        }
        return out
    }

    @Test
    fun aRoundedCornerIsOneTurn() {
        // North 100 m, then a right-angle corner drawn as four 22.5° bends, then east 100 m.
        val path = listOf(m(0.0, 0.0), m(0.0, 100.0), m(1.5, 103.5), m(4.0, 106.0), m(7.5, 108.5), m(11.0, 110.0), m(111.0, 110.0))
        assertEquals(listOf(Turn.RIGHT), turnsOf(Guidance(path, emptyList())).map { it.second })
    }

    @Test
    fun aStepAsideIsNoTurnButCrossingIsTwo() {
        // A 3 m step to the side on the pavement: walk on, nothing to say.
        val step = listOf(m(0.0, 0.0), m(0.0, 100.0), m(3.0, 103.0), m(3.0, 200.0))
        assertTrue(turnsOf(Guidance(step, emptyList())).isEmpty())
        // Crossing a road to the other side (10 m over), then on: right, then left.
        val cross = listOf(m(0.0, 0.0), m(0.0, 100.0), m(10.0, 100.0), m(10.0, 200.0))
        assertEquals(listOf(Turn.RIGHT, Turn.LEFT), turnsOf(Guidance(cross, emptyList())).map { it.second })
    }

    @Test
    fun aGentleBendIsNoTurn() {
        // A road curving 40° over 30 m (OSM's walking steps do not call it a turn either).
        val path = listOf(m(0.0, 0.0), m(0.0, 100.0), m(3.0, 110.0), m(8.0, 120.0), m(12.0, 128.0), m(70.0, 200.0))
        assertTrue(turnsOf(Guidance(path, emptyList())).isEmpty())
    }

    @Test
    fun aHairpinKeepsTheSideBeingWalked() {
        // Up 100 m, a hairpin, and back down 12 m to the east.
        val path = listOf(m(0.0, 0.0), m(0.0, 100.0), m(12.0, 100.0), m(12.0, 0.0))
        val g = Guidance(path, emptyList())
        // 40 m up, GPS 5 m off towards the other side: still 40 m along, not 170 m.
        val s = g.update(m(5.0, 40.0), nearM = 35.0)
        assertTrue("progress ${s.progressM}", s.progressM in 30.0..50.0)
        // Without knowing where the walker was, the nearest side wins as before.
        assertTrue(g.update(m(7.0, 40.0)).progressM > 150)
    }
}
