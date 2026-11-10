package com.deungbul.anshimgil.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkerChangeTest {
    private fun reply(said: String, mode: UserMode, before: List<ChatLine> = emptyList()) =
        runBlocking { RuleAppAssistant.reply(before + ChatLine(true, said), AssistantContext(mode, AppSettings())) }

    private val today = java.time.LocalDate.of(2026, 11, 10)
    private fun walk(said: String, mode: UserMode, before: List<ChatLine> = emptyList(), usual: UserMode = mode) =
        walkerChange(said, before + ChatLine(true, said), mode, usual, today)

    @Test
    fun todayWithoutTheWheelchairFollowsTheServer() {
        // RuleBrain 오늘만: walking today, the usual way back tomorrow.
        val a = walk("오늘은 휠체어 안 타", UserMode.DISABLED)
        assertEquals("오늘은 걸어서 다니시는 걸로 안내할까요? 내일은 다시 평소대로 할게요. 맞으세요?", a!!.say)
        assertEquals(YES_NO, (a as AssistantAction.Ask).choices)
        val yes = walk("네", UserMode.DISABLED, listOf(ChatLine(true, "오늘은 휠체어 안 타"), ChatLine(false, a.say)))
        yes as AssistantAction.ChangeSettings
        assertEquals(UserMode.GENERAL, yes.mode)
        assertEquals(today, yes.tempUntil)
    }

    @Test
    fun anInjuryIsForAWhileWithAnEndDay() {
        // RuleBrain.tempBody: until when, never an emergency.
        val asked = walk("다리를 다쳐서 휠체어 타고 다녀", UserMode.SENIOR)
        assertEquals("많이 불편하시겠어요. 언제까지 휠체어를 타세요? 날짜나 '이번 달까지'처럼 말씀해 주세요.", asked!!.say)
        // Not known: 7 days (MeService.TEMP_DEFAULT_DAYS), then checked.
        val talk = listOf(ChatLine(true, "다리를 다쳐서 휠체어 타고 다녀"), ChatLine(false, asked.say))
        val check = walk("잘 모르겠어", UserMode.SENIOR, talk)
        assertEquals(
            "그럼 일주일 동안(11월 17일까지) 휠체어로 안내하고, 그때 다시 여쭤볼게요. 그동안 장애인 화장실과 보도턱 낮춘 횡단보도도 지도에 보여 드릴게요. 맞으세요?",
            check!!.say,
        )
        val done = walk("네", UserMode.SENIOR, talk + ChatLine(true, "잘 모르겠어") + ChatLine(false, check.say)) as AssistantAction.ChangeSettings
        assertEquals(UserMode.DISABLED, done.mode)
        assertEquals(java.time.LocalDate.of(2026, 11, 17), done.tempUntil)
        assertEquals("다리 다침", done.tempReason)
        // Said with the day: straight to the check.
        assertTrue(walk("다리를 다쳐서 이번 달까지 휠체어 타", UserMode.SENIOR)!!.say.startsWith("11월 30일까지 휠체어로 안내하고"))
        // Healed: back to the usual way.
        val healed = walk("이제 다 나았어", UserMode.DISABLED, usual = UserMode.SENIOR)
        assertEquals("다행이에요! 이제 평소처럼 어르신 걸음으로 다니시는 걸로 바꿀까요? 맞으세요?", healed!!.say)
        // Anything urgent still offers 112.
        assertTrue(reply("휠체어에서 넘어졌어 살려줘", UserMode.DISABLED) is AssistantAction.Emergency)
        assertTrue(reply("넘어져서 다쳤어", UserMode.SENIOR) is AssistantAction.Emergency)
    }

    @Test
    fun endDayWordsAreTheServers() {
        assertEquals(today, untilIn("오늘만", today))
        assertEquals(java.time.LocalDate.of(2026, 11, 30), untilIn("이번 달까지", today))
        assertEquals(java.time.LocalDate.of(2026, 11, 15), untilIn("이번 주", today))
        assertEquals(java.time.LocalDate.of(2026, 11, 13), untilIn("3일 동안", today))
        assertEquals(java.time.LocalDate.of(2026, 11, 24), untilIn("두 주", today))
        assertEquals(java.time.LocalDate.of(2026, 12, 25), untilIn("12월 25일까지", today))
        assertNull(untilIn("잘 모르겠어", today))
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
