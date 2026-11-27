package com.deungbul.anshimgil.data.server

import com.deungbul.anshimgil.data.guide.newGuestToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 요청에 손님 번호와 토큰 헤더(X-Guest-Id, X-Guest-Token)를 붙이는 규칙 시험.
 *
 * 서버는 AI 주소(비서와 /api/ai/ 아래 주소)를 한 제한으로 세는데, 손님 번호가 없으면 같은 와이파이 전체를 한 사람처럼 센다
 * (safe-route #333). 그래서 AI 요청에만 두 헤더를 함께 붙인다. 나중에 서버가 토큰 확인을 켜면 토큰 없는 번호는 막히므로
 * 둘 중 하나만 보내지 않는다.
 */
class GuestHeadersTest {
    /** 역할: 비서와 /api/ai/ 아래 주소만 AI 주소로 본다. */
    @Test
    fun onlyTheAssistantAndAiWindowsAreAiPaths() {
        for (p in listOf("/api/assistant", "/api/ai/map-chat", "/api/ai/setup-chat", "/api/ai/pick-answer", "/api/ai/map-chat?x=1")) {
            assertTrue(p, isAiPath(p))
        }
        for (p in listOf("/api/route", "/api/me", "/api/reports", "/api/assistants", "/api/aid")) {
            assertFalse(p, isAiPath(p))
        }
    }

    /** 역할: AI 요청에는 두 헤더가 함께 붙는다. */
    @Test
    fun anAiRequestCarriesBothHeaders() {
        assertEquals(
            mapOf("X-Guest-Id" to "g-1", "X-Guest-Token" to "t-1"),
            guestHeadersFor("/api/ai/map-chat", "g-1", "t-1"),
        )
    }

    /** 역할: 길 찾기 같은 다른 요청에는 붙지 않는다. */
    @Test
    fun otherRequestsCarryNone() {
        assertEquals(emptyMap<String, String>(), guestHeadersFor("/api/route", "g-1", "t-1"))
    }

    /** 역할: 번호나 토큰 중 하나라도 없으면 아무것도 붙이지 않는다(한쪽만 보내면 나중에 막힘). */
    @Test
    fun neverOnlyOneOfTheTwo() {
        assertEquals(emptyMap<String, String>(), guestHeadersFor("/api/assistant", "g-1", null))
        assertEquals(emptyMap<String, String>(), guestHeadersFor("/api/assistant", null, "t-1"))
        assertEquals(emptyMap<String, String>(), guestHeadersFor("/api/assistant", "", "t-1"))
    }

    /** 역할: 토큰은 64자리 16진수 무작위 값이고, 만들 때마다 다르다. */
    @Test
    fun tokensAreLongRandomHex() {
        val a = newGuestToken()
        val b = newGuestToken()
        assertTrue(a, Regex("[0-9a-f]{64}").matches(a))
        assertNotEquals(a, b)
    }
}
