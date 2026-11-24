package com.deungbul.anshimgil.data.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.deungbul.anshimgil.data.route.LatLon
import com.deungbul.anshimgil.data.user.UserMode

class ReportTest {
    @Test
    fun formFieldsUseTheServersCodes() {
        val f = reportFields(ReportType.CURB, LatLon(35.1404, 126.9318), null, UserMode.DISABLED, "  턱이 높아요  ")
        assertEquals("curb", f["type"])
        assertEquals("35.140400", f["lat"])
        assertEquals("wheelchair", f["mode"])
        assertEquals("턱이 높아요", f["memo"])
        assertNull(reportFields(ReportType.OTHER, LatLon(35.0, 126.0), null, UserMode.GENERAL, "   ")["memo"])
    }

    @Test
    fun reportTypesMatchTheServer() {
        assertEquals(listOf("steep", "construction", "curb", "broken", "obstacle", "accident", "other"), ReportType.entries.map { it.code })
    }

    @Test
    fun theGuestNumberGoesWithAReport() {
        // Server c17 ③ keeps HMAC(secret, guestId) to tell one person reporting again (결정 57, 033).
        val f = reportFields(ReportType.CURB, LatLon(35.14, 126.93), null, UserMode.SENIOR, "") + ("guestId" to "abc")
        assertEquals("abc", f["guestId"])
    }
}
