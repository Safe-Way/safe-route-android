package com.deungbul.anshimgil.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MapHintsTest {
    private val here = LatLon(35.1468, 126.9190)

    @Test
    fun sunsetInGwangjuIsRight() {
        // Gwangju sets about 17:33 in early November and about 19:53 at midsummer
        val nov = sunsetAt(LocalDate.of(2026, 11, 6), 35.16, 126.85)!!
        assertTrue(nov.toString(), nov in LocalTime.of(17, 30)..LocalTime.of(17, 37))
        val jun = sunsetAt(LocalDate.of(2026, 6, 21), 35.16, 126.85)!!
        assertTrue(jun.toString(), jun in LocalTime.of(19, 49)..LocalTime.of(19, 57))
    }

    @Test
    fun eveningOnlyWithinTheHourBeforeSunset() {
        val sunset = LocalTime.of(17, 33)
        val hint = eveningHint(LocalDateTime.of(2026, 11, 6, 17, 0), sunset)!!
        assertEquals("5시 33분 해가 져요. 지금부터는 밝은 길을 먼저 골라 안내해요.", hint.text)
        assertNull(eveningHint(LocalDateTime.of(2026, 11, 6, 13, 0), sunset))
        assertNull(eveningHint(LocalDateTime.of(2026, 11, 6, 18, 0), sunset))
    }

    @Test
    fun nearbyEventIsMentionedFarOneIsNot() {
        val festival = RoadEvent(9, "행사", "금남로 차 없는 거리 걷자잉", null, 35.1461414, 126.9230951, 300, null)
        val biennale = RoadEvent(8, "행사", "광주비엔날레", null, 35.18309828, 126.8892087, 300, null)
        val hint = eventHint(here, listOf(biennale, festival))!!
        assertEquals("event-9", hint.key)
        assertTrue(hint.text, hint.text.startsWith("100m 거리 금남로 차 없는 거리 걷자잉에서 행사로 사람이 많아요"))
        assertNull(eventHint(here, listOf(biennale)))
    }

    @Test
    fun serverGreetingWithoutATripSaysNothing() {
        val generic = JSONObject("""{"say":"어디 가실 곳이 있으세요? 말씀해 주시면 길을 찾아 드릴게요.","ask":"어디 가실 곳이 있으세요?","pick":null,"tools":["get_my_info"]}""")
        assertNull(habitHint(generic, LocalDate.of(2026, 11, 6)))
    }

    @Test
    fun serverUsualTripOffersItsRoute() {
        val json = JSONObject(
            """{"say":"8시 반에 문화전당 가시죠? 오늘은 맑아서 걸어서 16분이에요.","ask":null,"choices":[],
               "pick":{"type":"walk","summary":"걸어서 16분","minutes":16,"route":"safe",
                 "request":{"origin":{"lat":35.14,"lon":126.91},"destination":{"lat":35.1467,"lon":126.9203,"name":"국립아시아문화전당"}}},
               "tools":["get_my_info","recommend_trip"]}""",
        )
        val hint = habitHint(json, LocalDate.of(2026, 11, 6))!!
        assertEquals("국립아시아문화전당", hint.place!!.name)
        assertTrue(hint.text.startsWith("8시 반에 문화전당 가시죠?"))
    }
}
