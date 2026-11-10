package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenNumbersTest {
    @Test
    fun emergencyNumbersAreReadDigitByDigit() {
        assertEquals(
            "괜찮으세요? 위험하면 바로 일일이에 전화하세요. 일일이에 전화할까요, 보호자에게 알릴까요, 가까운 파출소로 안내할까요?",
            spokenNumbers(EMERGENCY_SAY),
        )
        assertEquals("일일구 불러 줘", spokenNumbers("119 불러 줘"))
        assertEquals("1120m 앞", spokenNumbers("1120m 앞")) // other numbers stay
    }
}
