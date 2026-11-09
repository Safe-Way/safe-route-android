package com.deungbul.anshimgil.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupChatTest {
    private fun say(vararg lines: String) = lines.map { ChatLine(fromUser = true, text = it) }

    @Test
    fun motherInHerSeventiesWithBadLegsGetsSeniorSettings() = runBlocking {
        val reply = RuleSetupAssistant.reply(say("우리 엄마가 70대인데 다리가 좀 불편해요")) as SetupReply.Pick
        assertEquals(UserMode.SENIOR, reply.pick.mode)
        assertEquals(TextSize.LARGE, reply.pick.textSize)
        assertTrue("STAIRS" in reply.pick.extraMapItems)
        assertTrue(reply.reason.contains("계단"))
    }

    @Test
    fun asksOnceWhenItCannotTellWhoWalks() = runBlocking {
        assertTrue(RuleSetupAssistant.reply(say("잘 부탁해요")) is SetupReply.Ask)
        val second = RuleSetupAssistant.reply(say("잘 부탁해요", "음 그냥요")) as SetupReply.Pick
        assertEquals(UserMode.GENERAL, second.pick.mode)
    }

    @Test
    fun wheelchairAndChildAreRecognised() {
        assertEquals(UserMode.DISABLED, RuleSetupAssistant.modeFrom("휠체어를 타요"))
        assertEquals(UserMode.CHILD, RuleSetupAssistant.modeFrom("초등학생 딸이 학원 다녀요"))
    }

    private fun gemini(json: String) = JSONObject(json)

    @Test
    fun readsGeminiPickAndDropsUnknownValues() {
        val reply = parseSetupJson(
            gemini("""{"type":"pick","mode":"SENIOR","textSize":"LARGE","voiceGuide":true,"sosButton":true,"extraMapItems":["TOILET","SPACESHIP"],"reason":"천천히 걸으셔서요"}"""),
        ) as SetupReply.Pick
        assertEquals(UserMode.SENIOR, reply.pick.mode)
        assertEquals(setOf("TOILET"), reply.pick.extraMapItems)
        assertNull(parseSetupJson(gemini("""{"type":"pick","mode":"ROBOT"}""")))
        assertTrue(parseSetupJson(gemini("""{"type":"ask","question":"누가 걸으세요?","choices":["저요","부모님"]}""")) is SetupReply.Ask)
    }

    @Test
    fun garbledRepliesAreDropped() {
        assertTrue(looksLikeKorean("다리가 불편하신 어르신이 편안하게 걸으실 수 있도록 맞췄어요."))
        assertTrue(!looksLikeKorean("刀傳效析 刀傳 刀傳 刀傳 刀傳 刀傳 刀傳 刀傳 刀傳"))
        assertTrue(!looksLikeKorean("좋아요 좋아요 좋아요 좋아요 좋아요 좋아요"))
        assertNull(parseSetupJson(gemini("""{"type":"ask","question":"刀傳效析 刀傳 刀傳 刀傳"}""")))
    }

    @Test
    fun serverWindowGetsOnlyWordsAndSettings() {
        val long = "가".repeat(400)
        val lines = (1..15).map { ChatLine(fromUser = it % 2 == 1, text = if (it == 15) long else "말 $it") }
        val body = appChatRequest(lines, 12, settingsJson(UserMode.SENIOR, TextSize.LARGE, true, false, setOf("TOILET")))
        val history = body.getJSONArray("history")
        assertEquals(12, history.length())
        assertEquals(300, history.getJSONObject(11).getString("text").length)
        assertEquals("SENIOR", body.getJSONObject("settings").getString("mode"))
        assertEquals(setOf("history", "settings"), body.keys().asSequence().toSet())
        assertTrue(appChatRequest(lines, 12, null).isNull("settings"))
    }
}
