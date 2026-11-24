package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardianTest {
    @Test
    fun storedGuardianComesBackTheSame() {
        val mom = Guardian("엄마", "01012345678")
        assertEquals(mom, Guardian.decode(mom.encode()))
        assertNull(Guardian.decode("이름만"))
    }

    @Test
    fun phoneNumbersKeepOnlyDigits() {
        assertEquals("01012345678", Guardian.cleanPhone("010-1234-5678"))
        assertEquals("+821012345678", Guardian.cleanPhone("+82 10 1234 5678"))
        assertNull(Guardian.cleanPhone("12"))
        assertNull(Guardian.cleanPhone("abc"))
    }

    /**
     * 역할: SOS 때 보호자에게 가는 문자에 앱 이름과 지금 위치의 카카오맵 링크가 바르게 들어가는지 확인한다.
     * 위치를 모를 때(null)도 길 벗어남 알림 문자는 만들어져야 한다.
     */
    @Test
    fun messageCarriesAMapLinkToTheWalker() {
        val text = guardianMessage(GuardianAlert.SOS, LatLon(35.1404, 126.9318))
        assertTrue(text.startsWith("[세잎지도] 도움이 필요해요!"))
        assertTrue(text.contains("https://map.kakao.com/link/map/지금위치,35.140400,126.931800"))
        assertTrue(guardianMessage(GuardianAlert.OFF_ROUTE, null).contains("정한 길에서 벗어났어요"))
    }
}
