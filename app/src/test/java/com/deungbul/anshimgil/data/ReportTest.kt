package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
        assertEquals(listOf("steep", "construction", "curb", "broken", "obstacle", "other"), ReportType.entries.map { it.code })
    }
}
