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

    @Test fun withoutSpaces() {
        // 11/12 폰 시험: 붙여 쓴 말이라 서버로 넘어가 문화전당역을 도착지로 잡았다.
        assertEquals(listOf("문화전당역", "푸른길", "조선대"), read("문화전당역에서 푸른길에간다음 조선대로갈거야"))
        assertEquals(listOf("문화전당역", "푸른길", "조선대"), read("문화전당역에서푸른길에간다음조선대로갈거야"))
        assertEquals(listOf(null, "충장로", "광주극장"), read("충장로거쳐서 광주극장갈래"))
        assertEquals(listOf("광주역", "충장로"), read("광주역에서 충장로갈래"))
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
        assertNull(read("한시간후에 집에 갈래"))
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

    @Test fun theMeantPlace() = kotlinx.coroutines.runBlocking {
        fun p(n: String) = Place(n, n, "", 35.1, 126.9, null)
        // 11/12 폰 시험의 실제 검색 결과
        val postNear = listOf(p("T world 광주모바일대리점 우체국점"), p("LG유플러스 충장로 우체국점"), p("광주충장로우체국"))
        assertEquals("광주충장로우체국", pickTripPlace("충장로우체국", postNear) { emptyList() }?.name)
        val univNear = listOf(p("조선대학교 총동창회"), p("도야짬뽕 광주조선대점"), p("굽네치킨 조선대점"))
        assertEquals("조선대학교", pickTripPlace("조선대", univNear) { listOf(p("조선대학교"), p("조선대학교병원")) }?.name)
        val theaterNear = listOf(p("CGV 광주금남로"), p("국립아시아문화전당 어린이극장"))
        assertEquals("광주극장", pickTripPlace("광주극장", theaterNear) { listOf(p("광주극장"), p("CGV 광주충장로")) }?.name)
        // 역의 주차장이 아니라 역 (11/13 시연)
        val stationNear = listOf(p("양동시장역환승주차장"), p("양동시장역 광주1호선"), p("양동시장역교차로"))
        assertEquals("양동시장역 광주1호선", pickTripPlace("양동시장역", stationNear) { emptyList() }?.name)
        assertEquals("남광주시장 공영주차장", pickTripPlace("남광주시장 공영주차장", listOf(p("남광주시장 공영주차장"))) { emptyList() }?.name)
        // 이름이 그 말로 시작하는 다른 가게가 아니라 그곳 (11/13 시연: 광주광역시청 → 새마을금고)
        val hallNear = listOf(p("광주광역시청새마을금고 본점"), p("광주광역시청새마을금고 ATM"), p("광주광역시청 민원실"))
        assertEquals(
            "전남광주통합특별시청 광주청사",
            pickTripPlace("광주광역시청", hallNear.take(2)) { listOf(p("전남광주통합특별시청 광주청사"), p("광주광역시청새마을금고 본점")) }?.name,
        )
        // 근처에 지점이 하나라도 있으면 먼 곳 대신 그 지점
        assertEquals("스타벅스 충장로점", pickTripPlace("스타벅스", listOf(p("스타벅스 충장로점"))) { listOf(p("스타벅스 첨단점")) }?.name)
        // 지점이 많은 곳은 가까운 지점 (멀리 있는 지점으로 가지 않음)
        val chainNear = listOf(p("다이소 충장점"), p("다이소 금남로점"))
        assertEquals("다이소 충장점", pickTripPlace("다이소", chainNear) { listOf(p("다이소 첨단점")) }?.name)
    }
}
