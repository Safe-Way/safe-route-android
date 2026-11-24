package com.deungbul.anshimgil.data.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechFillersTest {
    @Test fun stillThinking() {
        // 채림 11/13: "어 그 머시기 뭐더라…" 하고 멈추면 이어서 듣는다.
        listOf("어", "어 그 머시기 뭐더라…", "음..", "남광주 그 뭐더라", "문화전당 가자 잠깐만~", "저기요?").forEach { assertTrue(it, waitsForMore(it)) }
    }

    @Test fun doneSpeaking() {
        listOf("문화전당 가자", "어 문화전당 가자", "아파", "어린이 모드로 바꿔 줘", "그래").forEach { assertFalse(it, waitsForMore(it)) }
    }

    @Test fun onlyFillersSayNothing() {
        assertFalse(saysSomething("어 음 그 뭐더라"))
        assertTrue(saysSomething("어 남광주시장"))
    }
}
