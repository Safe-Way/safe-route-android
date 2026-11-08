package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun readableTextStaysNormalWithoutTheAiPart() {
        val (_, a) = answerSetup(SetupStep.TEXT, 0, SetupAnswers())
        assertEquals(TextSize.NORMAL, a.textSize)
        assertNull(a.textBoost)
        assertEquals(TextSize.LARGE, answerSetup(SetupStep.TEXT, 1, SetupAnswers()).second.textSize)
    }
}
