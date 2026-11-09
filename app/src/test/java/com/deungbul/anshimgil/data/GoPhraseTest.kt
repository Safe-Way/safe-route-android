package com.deungbul.anshimgil.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoPhraseTest {
    @Test
    fun aPlaceToGoIsFoundInTheWords() {
        assertEquals("문화전당", placeToGo("문화전당 가자"))
        assertEquals("문화전당", placeToGo("문화전당으로 가 줘"))
        assertEquals("남광주역", placeToGo("남광주역까지 데려다 줘"))
        assertEquals("조선대병원", placeToGo("조선대병원 가고 싶어요."))
        // A street's 로 stays: it is the name, not "to".
        assertEquals("충장로", placeToGo("충장로 가자"))
        assertEquals("금남로", placeToGo("금남로로 가 줘"))
        assertNull(placeToGo("같이 가자"))
        assertNull(placeToGo("집에 가자"))
        assertNull(placeToGo("글씨 크게 해 줘"))
    }

    @Test
    fun theLandmarkWinsOverTheNearestNameAlike() {
        fun p(name: String) = Place(null, name, "", 35.14, 126.92, null)
        // The server's search for "문화전당", nearest first.
        val results = listOf(
            p("문화전당오피스텔"), p("국립아시아문화전당 GATE3"), p("내몸에필라테스&클래식 문화전당점"),
            p("국립아시아문화전당 부설주차장 공중화장실"), p("국립아시아문화전당 복합전시6관"),
        )
        assertEquals("국립아시아문화전당 GATE3", pickLandmark(results)!!.name)
        assertEquals("남광주역 광주1호선", pickLandmark(listOf(p("남광주역 광주1호선"), p("그램버거")))!!.name)
        assertNull(pickLandmark(emptyList()))
    }

    @Test
    fun saidToTheAssistantItGoesStraightThere() {
        val a = runBlocking { RuleAppAssistant.reply(listOf(ChatLine(true, "문화전당 가자")), AssistantContext(UserMode.SENIOR, AppSettings())) }
        assertTrue(a is AssistantAction.GoTo && a.onPhone && a.query == "문화전당")
        assertEquals("문화전당으로 길을 찾을게요.", a.say)
    }
}
