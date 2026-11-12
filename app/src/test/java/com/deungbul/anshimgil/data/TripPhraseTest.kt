package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripPhraseTest {
    private fun read(said: String) = tripIn(said)?.let { t -> listOf(t.start?.query) + t.stops.map { it.query } + t.destination.query }

    @Test fun startAndViaFromThePhoneTest() {
        // 11/12 폰 시험: 출발지와 경유지를 버리고 문화전당역으로 갔다.
        assertEquals(listOf("문화전당역", "서석동3", "한국예술종합교육원"), read("문화전당역에서 서석동3으로 이동후에 한국예술종합교육원으로 갈거야"))
    }

    @Test fun viaWords() {
        assertEquals(listOf(null, "서석초", "남광주시장"), read("서석초 거쳐서 남광주시장 갈래"))
        assertEquals(listOf(null, "광주천", "양림동"), read("광주천 경유해서 양림동으로 가자"))
        assertEquals(listOf(null, "충장로", "금남로"), read("충장로 갔다가 금남로 가고 싶어"))
        assertEquals(listOf(null, "전남대", "북구청"), read("전남대 간 다음에 북구청 가줘"))
        assertEquals(listOf(null, "서석동", "양림마을"), read("서석동을 거쳐 양림마을로 가자"))
    }

    @Test fun startOnly() {
        assertEquals(listOf("학교", "집"), read("학교에서 집에 갈래"))
        assertEquals(listOf("광주역", "대인시장"), read("광주역에서 출발해서 대인시장까지 안내해 줘"))
    }

    @Test fun startWithStopWords() {
        assertEquals(listOf("양림동", "다이소", "집"), read("양림동에서 다이소 들렀다가 집에 갈래"))
    }

    @Test fun notATrip() {
        assertNull(read("다이소 들렀다가 집 갈래")) // the server's 들를 곳 묶기
        assertNull(read("여기에서 남광주시장 가자")) // just a destination
        assertNull(read("문화전당 가자"))
        assertNull(read("어디에서 버스 타?"))
        assertNull(read("문화전당역에서 내려서 걸어갈래"))
        assertNull(read("한 시간 후에 집에 갈래"))
    }

    @Test fun savedPlaces() {
        val home = Place("h", "우리집 주소", "", 35.1, 126.9, null)
        val t = tripIn("학교에서 집에 갈래")!!.withSaved(listOf(Favorite("집", home, true)))
        assertEquals("집", t.destination.place?.name)
        assertNull(t.start?.place)
    }

    @Test fun foundSay() {
        assertEquals("문화전당역에서 출발해 서석동을 거쳐 예술원으로 가는 길을 찾았어요.", tripFoundSay("문화전당역", listOf("서석동"), "예술원"))
        assertEquals("집으로 가는 길을 찾았어요.", tripFoundSay(null, emptyList(), "집"))
    }

    @Test fun theNamedPlaceOverTheNearest() {
        fun p(n: String) = Place(n, n, "", 35.1, 126.9, null)
        val near = listOf(p("T world 광주모바일대리점 우체국점"), p("LG유플러스 충장로 우체국점"), p("광주충장로우체국"))
        assertEquals("광주충장로우체국", pickNamed("충장로우체국", near)?.name)
        assertNull(pickNamed("광주극장", listOf(p("CGV 광주금남로"), p("국립아시아문화전당 어린이극장"))))
        assertEquals("광주극장", pickNamed("광주극장", listOf(p("광주극장"), p("광주극장 주차장")))?.name)
    }
}
