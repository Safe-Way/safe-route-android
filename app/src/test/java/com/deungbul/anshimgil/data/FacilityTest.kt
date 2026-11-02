package com.deungbul.anshimgil.data

import org.junit.Assert.assertTrue
import org.junit.Test

class FacilityTest {
    @Test
    fun everyMode_showsCctv() {
        UserMode.entries.forEach { mode ->
            assertTrue("$mode should show CCTV", FacilityKind.CCTV in mode.facilityKinds)
            assertTrue(
                "$mode should have example CCTV on the map",
                FakeFacilities.layersFor(mode).facilities.any { it.kind == FacilityKind.CCTV },
            )
        }
    }
}
