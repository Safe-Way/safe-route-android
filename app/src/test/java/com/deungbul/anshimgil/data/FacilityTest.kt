package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FacilityTest {
    @Test
    fun everyModeShowsCommonFacilities() {
        UserMode.entries.forEach { assertTrue(it.facilityKinds.containsAll(COMMON_FACILITY_KINDS)) }
    }

    @Test
    fun optionalItemsLeaveOutWhatTheModeAlreadyShows() {
        val keys = UserMode.SENIOR.optionalMapItems.map { it.key }
        assertTrue(CHILD_ZONE_KEY in keys)
        assertTrue(FacilityKind.ACCESSIBLE_TOILET.name in keys)
        assertFalse(FacilityKind.TOILET.name in keys)
        assertFalse(FacilityKind.CCTV.name in keys)
        assertFalse(CHILD_ZONE_KEY in UserMode.CHILD.optionalMapItems.map { it.key })
    }

    @Test
    fun extrasAddToTheMap() {
        val extras = setOf(FacilityKind.ACCESSIBLE_TOILET.name, CHILD_ZONE_KEY)
        val layers = FakeFacilities.layersFor(UserMode.GENERAL, extras)
        assertTrue(layers.facilities.any { it.kind == FacilityKind.ACCESSIBLE_TOILET })
        assertEquals(1, layers.zones.size)
        assertTrue(FakeFacilities.layersFor(UserMode.GENERAL).zones.isEmpty())
    }
}
