package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenNumbersTest {
    /**
     * 역할: 음성 안내가 112·119를 "백십이"가 아니라 "일일이"처럼 한 자씩 읽는지 확인한다.
     * 1120m 같은 다른 숫자는 바꾸지 않아야 한다.
     */
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
