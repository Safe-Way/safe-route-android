package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SidewalkLineTest {
    private val start = LatLon(35.1460, 126.9200)
    private fun north(m: Double) = LatLon(start.lat + m / 111_320.0, start.lon)
    private val path = listOf(north(0.0), north(50.0), north(100.0))
    private fun east(p: LatLon, m: Double) = LatLon(p.lat, p.lon + m / (111_320.0 * Math.cos(Math.toRadians(p.lat))))
    private fun eastM(p: LatLon, q: LatLon) = (q.lon - p.lon) * 111_320.0 * Math.cos(Math.toRadians(p.lat))

    @Test
    fun walkingNorthTheRightPavementIsEast() {
        val r = RouteOption(RouteType.SAFE, 100, 2, path, sidewalks = listOf(Sidewalk(0, 2, SidewalkSide.RIGHT, highway = "tertiary")))
        val drawn = drawnPath(r)
        assertEquals(6.0, eastM(path[1], drawn[1]), 0.1)
        assertEquals(path[1].lat, drawn[1].lat, 1e-9)
    }

    @Test
    fun leftIsWestAndBothOrUnknownStay() {
        val left = RouteOption(RouteType.SAFE, 100, 2, path, sidewalks = listOf(Sidewalk(0, 2, SidewalkSide.LEFT, highway = "tertiary")))
        assertEquals(-6.0, eastM(path[1], drawnPath(left)[1]), 0.1)
        val both = RouteOption(RouteType.SAFE, 100, 2, path, sidewalks = listOf(Sidewalk(0, 2, SidewalkSide.BOTH, highway = "tertiary")))
        assertEquals(path, drawnPath(both))
        val none = RouteOption(RouteType.SAFE, 100, 2, path)
        assertTrue(drawnPath(none) === none.path)
    }

    @Test
    fun theMarkerMovesWithTheLine() {
        val r = RouteOption(RouteType.SAFE, 100, 2, path, sidewalks = listOf(Sidewalk(0, 2, SidewalkSide.RIGHT, highway = "tertiary")))
        assertEquals(6.0, eastM(north(70.0), drawnPoint(r, north(70.0), 1)), 0.1)
    }

    @Test
    fun theServersOffsetIsUsedWhenItComes() {
        val r = RouteOption(RouteType.SAFE, 100, 2, path, sidewalks = listOf(Sidewalk(0, 2, SidewalkSide.RIGHT, offsetM = 12.4, highway = "tertiary")))
        assertEquals(12.4, eastM(path[1], drawnPath(r)[1]), 0.1)
        // An offset with no side known: not moved.
        val u = RouteOption(RouteType.SAFE, 100, 2, path, sidewalks = listOf(Sidewalk(0, 2, SidewalkSide.UNKNOWN, offsetM = 12.4, highway = "tertiary")))
        assertEquals(path, drawnPath(u))
        // A silly value: the old 6 m instead.
        val big = RouteOption(RouteType.SAFE, 100, 2, path, sidewalks = listOf(Sidewalk(0, 2, SidewalkSide.RIGHT, offsetM = 80.0, highway = "tertiary")))
        assertEquals(6.0, eastM(path[1], drawnPath(big)[1]), 0.1)
    }

    @Test
    fun readsTheProposedSidewalkFields() {
        val json = org.json.JSONObject(
            """{"routes":[{"kind":"safe","distanceM":100,"durationMin":2,"geometry":[[126.92,35.146],[126.92,35.147]],
            "sidewalks":[{"from":0,"to":1,"side":"right","highway":"primary","lanes":3,"oneway":true,"widthM":null,"offsetM":6.4},
            {"from":1,"to":1,"side":"unknown"}]}]}""",
        )
        val w = parseRouteResult(json, UserMode.GENERAL).routes.single().sidewalks
        assertEquals(6.4, w[0].offsetM!!, 0.001)
        assertEquals("primary", w[0].highway)
        assertEquals(3, w[0].lanes)
        assertEquals(true, w[0].oneway)
        assertEquals(null, w[0].widthM)
        assertEquals(null, w[1].offsetM)
        assertEquals(null, w[1].oneway)
    }

    @Test
    fun bothSidesGoesToTheWalkersSideOrRight() {
        val both = listOf(Sidewalk(0, 2, SidewalkSide.BOTH, offsetM = 8.0, highway = "tertiary"))
        val r = RouteOption(RouteType.SAFE, 100, 2, path, sidewalks = both)
        // No position yet: right (east when walking north).
        assertEquals(8.0, eastM(path[1], drawnPath(r)[1]), 0.1)
        // The walker is west of the line: left.
        val westOfLine = LatLon(north(50.0).lat, north(50.0).lon - 6 / (111_320.0 * Math.cos(Math.toRadians(35.146))))
        assertEquals(SidewalkSide.LEFT, walkerSide(path, westOfLine, 0))
        val onWalkersSide = BothSide { walkerSide(path, westOfLine, 0) ?: SidewalkSide.RIGHT }
        assertEquals(-8.0, eastM(path[1], drawnPath(r, onWalkersSide)[1]), 0.1)
        // On the line: cannot tell.
        assertEquals(null, walkerSide(path, north(50.0), 0))
        // "both" from an older server without offsetM: not moved.
        val old = RouteOption(RouteType.SAFE, 100, 2, path, sidewalks = listOf(Sidewalk(0, 2, SidewalkSide.BOTH, highway = "tertiary")))
        assertEquals(path, drawnPath(old))
    }

    @Test
    fun alleysAndUnknownRoadKindsStayPut() {
        for (kind in listOf("residential", "service", "living_street", "unclassified", "track", null)) {
            val r = RouteOption(RouteType.SAFE, 100, 2, path, sidewalks = listOf(Sidewalk(0, 2, SidewalkSide.RIGHT, offsetM = 4.8, highway = kind)))
            assertEquals("$kind", path, drawnPath(r))
        }
        val big = RouteOption(RouteType.SAFE, 100, 2, path, sidewalks = listOf(Sidewalk(0, 2, SidewalkSide.LEFT, offsetM = 6.4, highway = "primary_link")))
        assertEquals(-6.4, eastM(path[1], drawnPath(big)[1]), 0.1)
    }

    @Test
    fun theLineCrossesAtTheCrosswalk() {
        // North on a big road on the right (east) pavement, then on its left (west) pavement after point 2.
        val p = listOf(north(0.0), north(40.0), north(80.0), north(120.0), north(160.0))
        val walk = listOf(
            Sidewalk(0, 2, SidewalkSide.RIGHT, offsetM = 8.0, highway = "primary"),
            Sidewalk(2, 4, SidewalkSide.LEFT, offsetM = 8.0, highway = "primary"),
        )
        val crosswalk = north(70.0)
        val r = RouteOption(RouteType.SAFE, 160, 3, p, sidewalks = walk, crossAt = listOf(CrossAt(p[2], crosswalk, "crossing")))
        val d = drawnPath(r)
        assertEquals(7, d.size) // point 2 became three: east end, the crosswalk, west end
        assertEquals(8.0, eastM(crosswalk, d[2]), 0.1)
        assertEquals(crosswalk, d[3])
        assertEquals(-8.0, eastM(crosswalk, d[4]), 0.1)
        // Without crossAt: as before.
        assertEquals(5, drawnPath(r.copy(crossAt = emptyList())).size)
    }

    @Test
    fun readsCrossAtAsTheServerSendsIt() {
        // Server #231 (11/12): routes[].crossAt with `at` a point number; from/to null for now.
        val json = org.json.JSONObject(
            """{"routes":[{"kind":"safe","distanceM":222,"durationMin":3,"geometry":[[126.92,35.146],[126.92,35.147],[126.92,35.148]],
            "crossAt":[{"at":1,"lat":35.1471,"lon":126.9201,"kind":"crossing","from":null,"to":null},{"at":9,"lat":1,"lon":1}],
            "crossings":[{"fromM":150,"toM":170}]}]}""",
        )
        val r = parseRouteResult(json, UserMode.GENERAL).routes.single()
        val c = r.crossAt.single()
        assertEquals(LatLon(35.147, 126.92), c.along)
        assertEquals(LatLon(35.1471, 126.9201), c.crosswalk)
    }

}
