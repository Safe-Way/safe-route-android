package com.deungbul.anshimgil.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MyInfoTest {
    @Test
    fun theServersInfoIsReadAndShownAsTheNearestChoice() {
        val i = parseMyInfo(JSONObject("""{"known":true,"modeNow":"senior","mode":"senior","stairsOk":1,"maxDetour":1.3,
            "restEveryM":500,"preferTransit":null,"consentTrips":true,"trips":4,"tempActive":false,"tempMode":"wheelchair"}"""))
        assertEquals(1, i.stairsOk)
        assertEquals(1.2, nearestChoice(i.maxDetour, DETOUR_CHOICES))
        assertEquals(400, nearestChoice(i.restEveryM, REST_CHOICES))
        assertNull(nearestChoice(i.preferTransit, TRANSIT_CHOICES))
        assertNull(i.tempMode) // only while it is in use
        assertEquals(4, i.trips)
    }
}
