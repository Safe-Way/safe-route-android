package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    private val start = LatLon(35.1460, 126.9200)

    /** A point [meters] north of [start]. */
    private fun north(meters: Double) = LatLon(start.lat + meters / 111_320.0, start.lon)

    @Test
    fun walkingIsNotAVehicleAndHasACourse() {
        val m = MotionTracker()
        for (s in 0..30) m.add(north(s * 1.3), s * 1000L) // 1.3 m/s, a normal walk
        assertFalse(m.inVehicle)
        assertEquals(1.3, m.speedMps!!, 0.1)
        assertEquals(0f, m.courseDeg!!, 2f) // going north
    }

    /**
     * 역할: 차(버스)를 탔는지를 몇 초가 아니라 한동안 지켜보고 판단하는지 확인한다.
     * 내린 뒤에도 잠시 '차'로 유지해서, 버스가 빨간불에 설 때마다 걸음 안내가 켜졌다 꺼졌다 하지 않게 한다.
     */
    @Test
    fun aCarIsNoticedAfterAWhileAndWalkingAgainLater() {
        val m = MotionTracker()
        var t = 0L
        var at = 0.0
        repeat(5) { at += 10.0; t += 1000; m.add(north(at), t) } // 36 km/h
        assertFalse("not after only a few seconds", m.inVehicle)
        repeat(15) { at += 10.0; t += 1000; m.add(north(at), t) }
        assertTrue(m.inVehicle)
        // Off the bus, walking: stays "vehicle" for a while so a red light does not switch it.
        repeat(5) { at += 1.2; t += 1000; m.add(north(at), t) }
        assertTrue(m.inVehicle)
        repeat(30) { at += 1.2; t += 1000; m.add(north(at), t) }
        assertFalse(m.inVehicle)
    }

    @Test
    fun standingStillHasNoCourse() {
        val m = MotionTracker()
        for (s in 0..10) m.add(north(if (s % 2 == 0) 0.0 else 2.0), s * 1000L) // GPS jitter
        assertNull(m.courseDeg)
    }

    @Test
    fun headingFollowsTheRouteWhileWalkingOnIt() {
        // Walking north on the route; the compass says south (phone held upright, flipped): the route wins.
        assertEquals(0f, guideHeading(onRoute = true, moving = true, routeAhead = 0f, course = 5f, compass = 180f))
        // Off the route: the way they actually go.
        assertEquals(90f, guideHeading(onRoute = false, moving = true, routeAhead = 0f, course = 90f, compass = 180f))
        // On the route standing still too: the route ahead, not the compass (switching swung the map, 11/9).
        assertEquals(0f, guideHeading(onRoute = true, moving = false, routeAhead = 0f, course = null, compass = 180f))
        // Standing still off the route: where the phone points.
        assertEquals(180f, guideHeading(onRoute = false, moving = false, routeAhead = 0f, course = 90f, compass = 180f))
        assertEquals(0f, guideHeading(onRoute = false, moving = false, routeAhead = 0f, course = null, compass = null))
    }

    @Test
    fun movingDoesNotFlickAtACorner() {
        val m = MovingState()
        assertEquals(true, m.update(1.0, 0))
        // Slowing at a corner for a moment, or between the two speeds: still moving.
        assertEquals(true, m.update(0.2, 1_000))
        assertEquals(true, m.update(0.4, 2_000))
        assertEquals(true, m.update(0.2, 3_000))
        assertEquals(true, m.update(0.2, 5_500))
        // Slow for 3 s: stopped.
        assertEquals(false, m.update(0.1, 6_100))
        assertEquals(false, m.update(0.5, 7_000))
        assertEquals(true, m.update(0.7, 8_000))
    }

    @Test
    fun standingStillTheArrowTurnsWithTheWalker() {
        assertEquals(200f, arrowHeading(moving = false, heading = 0f, compass = 200f))
        assertEquals(200f, arrowHeading(moving = true, heading = 0f, compass = 200f))
        assertEquals(0f, arrowHeading(moving = false, heading = 0f, compass = null))
    }

    @Test
    fun walkingAnotherWayTheMapFollowsTheCourse() {
        assertEquals(90f, guideHeading(onRoute = true, moving = true, routeAhead = 0f, course = 90f, compass = null))
        assertEquals(0f, guideHeading(onRoute = true, moving = true, routeAhead = 0f, course = 30f, compass = null))
    }

    @Test
    fun roughOrJumpingFixesAreNotARide() {
        // m26 (영상 9:40): walking, with rough fixes far off now and then.
        val rough = MotionTracker()
        var t = 0L
        var at = 0.0
        repeat(40) { s ->
            at += 1.3; t += 1000
            if (s % 3 == 0) rough.add(north(at + 80), t, accuracyM = 45f) else rough.add(north(at), t, accuracyM = 6f)
        }
        assertFalse(rough.inVehicle)
        // Walking with a single 70 m jump that looks precise: one fast step, not a ride.
        val jump = MotionTracker()
        t = 0L; at = 0.0
        repeat(40) { s ->
            at += 1.3; t += 1000
            jump.add(north(if (s in 10..25) at + 70 else at), t, accuracyM = 8f)
        }
        assertFalse(jump.inVehicle)
    }
}
