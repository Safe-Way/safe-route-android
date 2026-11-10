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
}
