package com.deungbul.anshimgil.data.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.deungbul.anshimgil.data.user.UserMode

class RouteCriteriaTest {
    @Test
    fun seniorDaytimeTalksAboutStairsAndRestNotLights() {
        val c = routeCriteria(UserMode.SENIOR, night = false)
        assertTrue(c.short.contains("계단") && c.short.contains("쉴 곳"))
        assertEquals(setOf("rest", "covered.rest"), c.counts)
    }

    @Test
    fun nightPutsBrightnessFirstForEveryone() {
        UserMode.entries.forEach { mode ->
            val c = routeCriteria(mode, night = true)
            assertEquals(setOf("light", "help", "cctv"), c.counts)
            assertTrue(mode.name, c.details.any { it.startsWith("1순위 밝기") })
        }
    }

    @Test
    fun childDaytimeUsesSchoolZoneAndHelp() {
        val c = routeCriteria(UserMode.CHILD, night = false)
        assertTrue(c.details.first().contains("어린이보호구역"))
        assertEquals(setOf("school", "help", "covered.school", "covered.help"), c.counts)
    }
}
