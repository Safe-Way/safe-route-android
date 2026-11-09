package com.deungbul.anshimgil.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkerChangeTest {
    private fun reply(said: String, mode: UserMode, before: List<ChatLine> = emptyList()) =
        runBlocking { RuleAppAssistant.reply(before + ChatLine(true, said), AssistantContext(mode, AppSettings())) }

    @Test
    fun notTodayAsksWhichModeInsteadOfTurningWheelchairOn() {
        // 점검표 9번: it used to switch to the wheelchair mode, without asking.
        val a = reply("오늘은 휠체어 안 타", UserMode.DISABLED)
        assertTrue(a is AssistantAction.Ask && a.onPhone)
        assertEquals(listOf("어르신 모드로 바꿔 줘", "일반 모드로 바꿔 줘", KEEP_AS_IS), (a as AssistantAction.Ask).choices)
        // Then the pick changes it.
        assertEquals(UserMode.SENIOR, (reply("어르신 모드로 바꿔 줘", UserMode.DISABLED) as AssistantAction.ChangeSettings).mode)
        assertTrue(reply(KEEP_AS_IS, UserMode.DISABLED).let { it is AssistantAction.Answer && it.onPhone })
        // Not in the wheelchair mode anyway: nothing to change.
        assertTrue(reply("오늘은 휠체어 안 타", UserMode.SENIOR) is AssistantAction.Answer)
    }

    @Test
    fun anInjuryIsAskedHowLongNotTakenForAnEmergency() {
        // 점검표 10번: "다쳐서" used to bring up 112.
        val asked = reply("다리를 다쳐서 휠체어 타", UserMode.SENIOR)
        assertTrue(asked is AssistantAction.Ask && "언제까지" in asked.say)
        val then = reply("며칠 동안", UserMode.SENIOR, listOf(ChatLine(true, "다리를 다쳐서 휠체어 타"), ChatLine(false, asked.say)))
        assertEquals(UserMode.DISABLED, (then as AssistantAction.ChangeSettings).mode)
        // Anything urgent still offers 112.
        assertTrue(reply("휠체어에서 넘어졌어 살려줘", UserMode.DISABLED) is AssistantAction.Emergency)
        assertTrue(reply("넘어져서 다쳤어", UserMode.SENIOR) is AssistantAction.Emergency)
    }

    @Test
    fun aRemarkIsAskedBackAndACommandIsDone() {
        assertTrue(reply("휠체어 타고 다녀", UserMode.SENIOR).let { it is AssistantAction.Ask && it.onPhone })
        assertEquals(UserMode.DISABLED, (reply("휠체어 모드로 바꿔 줘", UserMode.SENIOR) as AssistantAction.ChangeSettings).mode)
        assertNull(walkerChange("가까운 편의점", emptyList(), UserMode.SENIOR))
    }

    @Test
    fun theParticleFollowsTheWord() {
        assertEquals("가까운 화장실로 길을 찾을게요.", reply("화장실 어디야", UserMode.SENIOR).say)
        assertEquals("가까운 편의점으로 길을 찾을게요.", reply("편의점 어디야", UserMode.SENIOR).say)
    }
}
