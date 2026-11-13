package com.deungbul.anshimgil.ui.guide

import org.junit.Assert.assertEquals
import org.junit.Test

class NoLocationTest {
    @Test fun whyTheGuidanceWaits() {
        assertEquals("위치 권한이 없어요. 허용해야 안내할 수 있어요.", noLocationText(hasPermission = false, locationOn = true))
        assertEquals("위치가 꺼져 있어요. 켜야 안내할 수 있어요.", noLocationText(hasPermission = true, locationOn = false))
        assertEquals("현재 위치를 찾는 중이에요…", noLocationText(hasPermission = true, locationOn = true))
    }
}
