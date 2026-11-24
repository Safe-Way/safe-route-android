package com.deungbul.anshimgil.data.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.deungbul.anshimgil.data.route.LatLon

class OriginTest {
    @Test
    fun gpsInGwangju_isUsed() {
        val fix = LatLon(35.1460, 126.9230) // 남광주역 근처
        val origin = chooseOrigin(fix)
        assertEquals(fix, origin.point)
        assertTrue(origin.fromGps)
        assertEquals("현재 위치", origin.label)
    }

    @Test
    fun noFix_usesDemoPoint() {
        val origin = chooseOrigin(null)
        assertEquals(DEMO_ORIGIN_POINT, origin.point)
        assertFalse(origin.fromGps)
    }

    @Test
    fun outsideGwangju_usesDemoPoint() {
        // Emulator default (California) and Seoul
        assertEquals(DEMO_ORIGIN_POINT, chooseOrigin(LatLon(37.4220, -122.0841)).point)
        assertEquals(DEMO_ORIGIN_POINT, chooseOrigin(LatLon(37.5665, 126.9780)).point)
        assertEquals(OriginIssue.OUTSIDE_GWANGJU, chooseOrigin(LatLon(37.5665, 126.9780)).issue)
        assertTrue(isInGwangju(LatLon(35.2200, 126.8500))) // 첨단
    }

    @Test
    fun noFix_saysWhy() {
        val off = chooseOrigin(null, OriginIssue.LOCATION_OFF)
        assertEquals(OriginIssue.LOCATION_OFF, off.issue)
        assertEquals("조선대 (위치 꺼짐)", off.label)
        assertEquals(OriginIssue.NO_FIX, chooseOrigin(null).issue)
    }
}
