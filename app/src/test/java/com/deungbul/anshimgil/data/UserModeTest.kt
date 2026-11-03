package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UserModeTest {
    @Test
    fun keysMatchRouteEngine() {
        assertEquals(listOf("child", "senior", "wheelchair", "adult"), UserMode.entries.map { it.key })
    }

    @Test
    fun oldSavedKeysStillLoad() {
        assertEquals(UserMode.DISABLED, UserMode.fromKey("disabled"))
        assertEquals(UserMode.GENERAL, UserMode.fromKey("general"))
    }
}
