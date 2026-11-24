package com.deungbul.anshimgil.data.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDetourTest {
    private fun ll(lat: Double, lon: Double) = LatLon(lat, lon)

    // The server's adult route from beside IT융합대학 to the 조선대해오름관 stop (11/6): it goes 28 m past
    // the stop on the pavement and comes back up the road beside it, and starts with a 10 m step away and back.
    private val raw = listOf(
        ll(35.140457, 126.934092), ll(35.140481, 126.934198), ll(35.140538, 126.934122), ll(35.140698, 126.933923),
        ll(35.140728, 126.933877), ll(35.141505, 126.933628), ll(35.141485, 126.933440), ll(35.141436, 126.932873),
        ll(35.141417, 126.932689), ll(35.141392, 126.932515), ll(35.141359, 126.932274), ll(35.141327, 126.931933),
        ll(35.141270, 126.931941), ll(35.141262, 126.931864), ll(35.140992, 126.931879), ll(35.140863, 126.931896),
        ll(35.140838, 126.931900), ll(35.140712, 126.931917), ll(35.140329, 126.931971), ll(35.140582, 126.931998),
    )
    private val route = RouteOption(RouteType.SAFE, 442, 6, raw, sidewalks = listOf(Sidewalk(5, 10, SidewalkSide.LEFT), Sidewalk(16, 19, SidewalkSide.RIGHT)))

    @Test
    fun loopPastTheStopIsCut() {
        val fixed = route.withoutEndDetours()
        // After passing the stop it no longer goes on south (it went 28 m) and comes back.
        assertTrue(fixed.path.takeLast(3).joinToString { "%.6f".format(it.lat) }, fixed.path.takeLast(3).all { it.lat >= 35.140582 - 0.000045 })
        assertEquals(raw.last(), fixed.path.last())
        assertEquals(raw.first(), fixed.path.first())
        // About 50 m fewer.
        assertTrue(fixed.distanceM.toString(), fixed.distanceM in 385..395)
        // Sidewalk stretches still point inside the path.
        assertTrue(fixed.sidewalks.all { it.from >= 0 && it.to <= fixed.path.lastIndex && it.from < it.to })
    }

    @Test
    fun straightRouteIsLeftAlone() {
        val straight = RouteOption(RouteType.SAFE, 200, 3, listOf(ll(35.1400, 126.9300), ll(35.1409, 126.9300), ll(35.1418, 126.9300)))
        assertEquals(straight, straight.withoutEndDetours())
    }

    @Test
    fun arrivingAroundACornerIsLeftAlone() {
        // Passing 12 m from the end but only a short way around the corner: not worth cutting.
        val corner = RouteOption(
            RouteType.SAFE, 120, 2,
            listOf(ll(35.1400, 126.9300), ll(35.1410, 126.9300), ll(35.1410, 126.93012)),
        )
        assertEquals(corner, corner.withoutEndDetours())
    }
}
