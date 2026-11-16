package com.deungbul.anshimgil.ui.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

/** The answer to "지도에서 보여 드릴까요?" (11/10: the map waits for a yes). */
class GoAnswerTest {
    @Test
    fun buttonsAndWords() {
        assertEquals(true, goAnswer(GO_YES))
        assertEquals(false, goAnswer(GO_NO))
        assertEquals(true, goAnswer("응 보여 줘"))
        assertEquals(true, goAnswer("네"))
        assertEquals(true, goAnswer("그래 가자"))
        assertEquals(false, goAnswer("아니 괜찮아"))
        assertEquals(false, goAnswer("나중에 볼게"))
        // Something else: the chat carries on with it.
        assertEquals(null, goAnswer("근처에 화장실은 어디 있어"))
    }

    @Test
    fun answerWordsWithEndings() {
        assertEquals(true, goAnswer("네 지도에서 보여 주세요"))
        assertEquals(true, goAnswer("좋아요"))
        assertEquals(true, goAnswer("알겠어요"))
        assertEquals(true, goAnswer("부탁해요"))
        assertEquals(true, goAnswer("응 거기로 가자"))
        assertEquals(false, goAnswer("괜찮아요"))
        assertEquals(false, goAnswer("싫어요"))
    }

    /** 11/16 phone check: "동명동 가자" while asked opened the place found before. */
    @Test
    fun aNewPlaceIsANewRequest() {
        assertEquals(null, goAnswer("동명동 가자"))
        assertEquals(null, goAnswer("광주역 가자"))
        assertEquals(null, goAnswer("사직공원 보여 줘"))
        assertEquals(null, goAnswer("광주역 말고 동명동"))
        assertEquals(null, goAnswer("30분 뒤에 남광주시장 가자"))
    }
}
