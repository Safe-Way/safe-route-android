package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstSetupTest {
    @Test
    fun notNowChangesNothing() {
        val (next, a) = answerSetup(SetupStep.START, 1, SetupAnswers())
        assertNull(next)
        assertEquals(SetupAnswers(), a)
    }

    @Test
    fun threeQuestionsThenWhatWasSet() {
        var step: SetupStep? = SetupStep.START
        var a = SetupAnswers()
        listOf(0, 2, 0, 1).forEach { i -> answerSetup(step!!, i, a).let { (n, x) -> step = n; a = x } }
        assertEquals(SetupStep.DONE, step)
        assertEquals(SetupAnswers(TextSize.LARGE, 1.15f, voiceGuide = true, sosButton = false), a)
        assertEquals(
            "이렇게 맞췄어요: 글씨 크게(한 단계 더), 음성 안내 켬, SOS 버튼 없음. 설정에서나 저에게 말해서 언제든 바꿀 수 있어요.",
            setupQuestion(SetupStep.DONE, a),
        )
        assertNull(answerSetup(SetupStep.DONE, 0, a).first)
    }

    @Test
    fun readableTextIsLeftAsItIs() {
        // "잘 보여요" leaves the text as it is, also for a walker already on 크게.
        val (_, a) = answerSetup(SetupStep.TEXT, 0, SetupAnswers())
        assertNull(a.textSize)
        assertNull(a.textBoost)
        assertEquals(
            "이렇게 맞췄어요: 글씨 그대로, 음성 안내 끔, SOS 버튼 켬. 설정에서나 저에게 말해서 언제든 바꿀 수 있어요.",
            setupSummary(a.copy(voiceGuide = false, sosButton = true)),
        )
        assertEquals(TextSize.LARGE, answerSetup(SetupStep.TEXT, 1, SetupAnswers()).second.textSize)
    }

    @Test
    fun fromBigTextASmallAnswerGoesOneStepFurther() {
        // 어르신 starts on 크게 (사용성 점검표 13번), so "조금 작아요" has to make it bigger still.
        val (_, a) = answerSetup(SetupStep.TEXT, 1, SetupAnswers(), current = TextSize.LARGE)
        assertEquals(1.15f, a.textBoost)
        val (_, b) = answerSetup(SetupStep.TEXT, 2, SetupAnswers(), current = TextSize.LARGE)
        assertEquals(1.3f, b.textBoost)
        assertTrue(setupSummary(b).startsWith("이렇게 맞췄어요: 글씨 크게(두 단계 더)"))
    }

    @Test
    fun answersCanBeSaid() {
        assertEquals(0, setupAnswerFromSpeech(SetupStep.START, "네 좋아요"))
        assertEquals(1, setupAnswerFromSpeech(SetupStep.START, "아니 다음에 할게"))
        assertEquals(2, setupAnswerFromSpeech(SetupStep.TEXT, "글씨가 너무 작아"))
        assertEquals(1, setupAnswerFromSpeech(SetupStep.TEXT, "조금 작아요"))
        assertEquals(0, setupAnswerFromSpeech(SetupStep.TEXT, "잘 보여요"))
        assertEquals(0, setupAnswerFromSpeech(SetupStep.VOICE, "소리로 알려 줘"))
        assertEquals(1, setupAnswerFromSpeech(SetupStep.VOICE, "화면만 볼게"))
        assertEquals(0, setupAnswerFromSpeech(SetupStep.SOS, "응 둬"))
        assertEquals(1, setupAnswerFromSpeech(SetupStep.SOS, "필요 없어요"))
        assertEquals(SETUP_UNDO_INDEX, setupAnswerFromSpeech(SetupStep.DONE, "처음대로 되돌려 줘"))
        assertNull(setupAnswerFromSpeech(SetupStep.TEXT, "오늘 날씨 어때"))
    }

    @Test
    fun aChangedModeIsAskedTheSameWay() {
        assertEquals(SetupStep.TEXT, answerSetup(SetupStep.CHANGED, 0, SetupAnswers()).first)
        assertNull(answerSetup(SetupStep.CHANGED, 1, SetupAnswers()).first)
        assertEquals(SetupAnswers(), answerSetup(SetupStep.CHANGED, 1, SetupAnswers()).second)
    }
}
