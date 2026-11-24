package com.deungbul.anshimgil.data.user

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import com.deungbul.anshimgil.data.place.RoadEvent
import com.deungbul.anshimgil.data.route.LatLon

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
        assertEquals("5시 33분에 해가 져요. 지금부터는 밝은 길로 먼저 안내할게요.", hint.text)
        assertNull(eveningHint(LocalDateTime.of(2026, 11, 6, 13, 0), sunset))
        assertNull(eveningHint(LocalDateTime.of(2026, 11, 6, 18, 0), sunset))
    }

    @Test
    fun nearbyEventIsMentionedFarOneIsNot() {
        val festival = RoadEvent(9, "행사", "금남로 차 없는 거리 걷자잉", null, 35.1461414, 126.9230951, 300, null)
        val biennale = RoadEvent(8, "행사", "광주비엔날레", null, 35.18309828, 126.8892087, 300, null)
        val hint = eventHint(here, listOf(biennale, festival))!!
        assertEquals("event-9", hint.key)
        assertTrue(hint.text, hint.text == "약 100m 떨어진 곳에서 '금남로 차 없는 거리 걷자잉' 행사가 열리고 있어요. 그쪽을 지나게 되면 미리 알려 드릴게요.")
        assertNull(eventHint(here, listOf(biennale)))
    }

    // --- today's suggestion (GET /api/suggestions/today), shapes from the live server 11/6 ---

    @Test
    fun greetingOnlyIsNotShown() {
        val json = JSONObject("""{"say":"어디 가실 곳이 있으세요? 말씀해 주시면 길을 찾아 드릴게요.","habit":null,"others":[],
            "weather":{"word":"clear","rain":false,"tempC":13.0,"weatherAvailable":true,"dustAvailable":true},"weatherHint":null,"pick":null,"because":[]}""")
        assertNull(todayHint(json, LocalDate.of(2026, 11, 6)))
    }

    @Test
    fun usualTripWithItsRoute() {
        val json = JSONObject("""{"say":"8시 반에 문화전당 가시죠? 오늘은 맑아서 걸어서 16분이에요.",
            "habit":{"toName":"국립아시아문화전당","lat":35.147,"lon":126.920,"slot":"08:30","times":3,"usual":"walk"},
            "weather":{"word":"clear","weatherAvailable":true},
            "pick":{"type":"walk","request":{"destination":{"lat":35.1467,"lon":126.9203,"name":"국립아시아문화전당"}}}}""")
        val hint = todayHint(json, LocalDate.of(2026, 11, 9))!!
        assertEquals("8시 반에 문화전당 가시죠? 오늘은 맑아서 걸어서 16분이에요.", hint.text)
        assertEquals(35.1467, hint.place!!.lat, 1e-9)
    }

    @Test
    fun badWeatherWithWhatTheyUsuallyDo() {
        val json = JSONObject("""{"say":"어디 가실 곳이 있으세요?","habit":null,
            "weather":{"word":"rain","rain":true,"tempC":11.4,"weatherAvailable":true},"weatherHint":"비 오는 날엔 보통 대중교통을 타셨어요"}""")
        assertEquals("지금 비가 와요 (11°C). 미끄러운 길을 조심하세요. 비 오는 날엔 보통 대중교통을 타셨어요", todayHint(json, LocalDate.of(2026, 11, 9))!!.text)
    }

    // --- when the app is opened ---

    @Test
    fun firstOpenOfTheDay() {
        val history = listOf("2026-11-05|08:30", "2026-11-05|17:00")
        assertEquals(OpenMoment.FIRST_TODAY, openMoment(history, LocalDateTime.of(2026, 11, 6, 9, 10)))
        assertEquals(OpenMoment.OTHER, openMoment(history + "2026-11-06|09:00", LocalDateTime.of(2026, 11, 6, 13, 0)))
    }

    @Test
    fun usualTimeAfterThreeEarlierDays() {
        val history = listOf("2026-11-03|08:30", "2026-11-04|08:30", "2026-11-05|08:30", "2026-11-06|07:00")
        assertEquals(OpenMoment.USUAL_TIME, openMoment(history, LocalDateTime.of(2026, 11, 6, 8, 45)))
        // Two days are not enough, and days over a month ago do not count.
        assertEquals(OpenMoment.OTHER, openMoment(history.drop(1), LocalDateTime.of(2026, 11, 6, 8, 45)))
        val old = listOf("2026-09-01|08:30", "2026-09-02|08:30", "2026-09-03|08:30", "2026-11-06|07:00")
        assertEquals(OpenMoment.OTHER, openMoment(old, LocalDateTime.of(2026, 11, 6, 8, 45)))
        assertEquals("08:30", halfHour(java.time.LocalTime.of(8, 59)))
    }
}
