package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OffRouteTest {
    @Test
    fun aCrossingAllowsMore() {
        val turn = TurnMark(LatLon(35.1460, 126.9200), left = true)
        val near = LatLon(35.1460 + 20 / 111_320.0, 126.9200)
        val far = LatLon(35.1460 + 60 / 111_320.0, 126.9200)
        assertTrue(atCrossing(near, listOf(turn)))
        assertFalse(atCrossing(far, listOf(turn)))
        assertEquals(40, offRouteLimitM(atCrossing = true))
        assertEquals(25, offRouteLimitM(atCrossing = false))
    }

    @Test
    fun oneJumpIsNotLeavingTwoInARowIs() {
        val c = OffRouteCounter()
        assertFalse(c.update(30, 25)) // one fix past: maybe GPS
        assertFalse(c.update(10, 25)) // back on it
        assertFalse(c.update(30, 25))
        assertTrue(c.update(32, 25)) // two in a row: off
        assertFalse(c.update(35, 40)) // at a crossing 35 m is still on it
    }
}
