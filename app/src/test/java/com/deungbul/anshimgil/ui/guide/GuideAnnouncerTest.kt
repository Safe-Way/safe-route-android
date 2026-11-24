package com.deungbul.anshimgil.ui.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.deungbul.anshimgil.data.guide.Guidance
import com.deungbul.anshimgil.data.route.LatLon

class GuideAnnouncerTest {
    // West about 110 m, then left (south) about 110 m.
    private val start = LatLon(35.1404, 126.9318)
    private val corner = LatLon(35.1404, 126.9306)
    private val end = LatLon(35.1394, 126.9306)
    private fun announcer() = GuideAnnouncer(Guidance(listOf(start, corner, end), emptyList()), "상무역")

    @Test fun saysTheNextTurnOnceUntilItChanges() {
        val a = announcer()
        val first = a.onFix(start)
        assertTrue(first.shown, first.shown.endsWith("m 앞 · 왼쪽으로 가세요"))
        assertTrue(first.speak!!.contains("왼쪽으로 가세요"))
        // A few meters on: the same bucket, shown again but not said again.
        val next = a.onFix(LatLon(35.1404, 126.93175))
        assertNull(next.speak)
        // Near the corner: closer bucket, said again.
        assertTrue(a.onFix(LatLon(35.1404, 126.93075)).speak != null)
    }

    @Test fun arrivalAndFarAway() {
        assertEquals("도착 · 목적지에 도착했어요", announcer().onFix(end).shown)
        val far = announcer().onFix(LatLon(35.1500, 126.9318))
        assertTrue(far.shown, far.shown.startsWith("길에서 "))
        assertNull(far.speak)
    }
}
