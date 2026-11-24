package com.deungbul.anshimgil.data.user

import org.junit.Assert.assertEquals
import org.junit.Test

/** 처음 설정 answers said in everyday words (11/10: read more of what people say, not only the button words). */
class SetupSpeechTest {
    private fun r(step: SetupStep, said: String) = setupAnswerFromSpeech(step, said)

    @Test
    fun waysOfGettingAbout() {
        assertEquals(0, r(SetupStep.AID, "그냥 혼자 걸어 다녀요"))
        assertEquals(1, r(SetupStep.AID, "지팡이 짚고 다녀"))
        assertEquals(1, r(SetupStep.AID, "보행기 써요"))
        assertEquals(2, r(SetupStep.AID, "전동 휠체어 타요"))
        assertEquals(3, r(SetupStep.AID, "애기랑 유모차 끌고 다녀요"))
    }

    @Test
    fun stairs() {
        assertEquals(0, r(SetupStep.STAIRS, "계단은 좀 버거워요"))
        assertEquals(0, r(SetupStep.STAIRS, "계단은 자신 없어"))
        assertEquals(1, r(SetupStep.STAIRS, "짧으면 괜찮아"))
        assertEquals(1, r(SetupStep.STAIRS, "조금은 괜찮아"))
        assertEquals(2, r(SetupStep.STAIRS, "계단은 상관없어요"))
    }

    @Test
    fun yesAndNoReadTheNoFirst() {
        assertEquals(0, r(SetupStep.CONFIRM, "응 그렇게 해"))
        assertEquals(0, r(SetupStep.CONFIRM, "맞아요"))
        assertEquals(1, r(SetupStep.CONFIRM, "아니 그게 아니고"))
        assertEquals(0, r(SetupStep.CONSENT, "남겨도 돼요"))
        assertEquals(1, r(SetupStep.CONSENT, "기록은 남기지 마"))
        assertEquals(1, r(SetupStep.CONSENT, "별로 안 좋아"))
        assertEquals(null, r(SetupStep.CONSENT, "잘 모르겠어"))
        assertEquals(1, r(SetupStep.START, "괜찮아요"))
    }

    @Test
    fun textVoiceAndSos() {
        assertEquals(2, r(SetupStep.TEXT, "글씨가 흐릿해서 잘 안 보여"))
        assertEquals(1, r(SetupStep.TEXT, "좀 작네"))
        assertEquals(0, r(SetupStep.TEXT, "잘 읽혀요"))
        assertEquals(1, r(SetupStep.VOICE, "소리는 필요 없어"))
        assertEquals(1, r(SetupStep.VOICE, "시끄러워서 화면만 볼래"))
        assertEquals(0, r(SetupStep.VOICE, "말로 알려 줘"))
        assertEquals(0, r(SetupStep.SOS, "있으면 좋겠어"))
        assertEquals(1, r(SetupStep.SOS, "필요 없어요"))
        assertEquals(1, r(SetupStep.SOS, "괜찮아요"))
    }
}
