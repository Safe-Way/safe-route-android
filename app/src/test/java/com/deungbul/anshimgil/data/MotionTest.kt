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
        // Standing still: where the phone points.
        assertEquals(180f, guideHeading(onRoute = true, moving = false, routeAhead = 0f, course = null, compass = 180f))
        assertEquals(0f, guideHeading(onRoute = true, moving = false, routeAhead = 0f, course = null, compass = null))
    }
}
