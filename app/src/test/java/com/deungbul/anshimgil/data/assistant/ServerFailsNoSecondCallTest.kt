package com.deungbul.anshimgil.data.assistant

import com.deungbul.anshimgil.data.server.ApiException
import com.deungbul.anshimgil.data.user.AppSettings
import com.deungbul.anshimgil.data.user.ChatLine
import com.deungbul.anshimgil.data.user.UserMode
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * 서버 비서(/api/assistant)가 실패하면 지도 대화(/api/ai/map-chat)를 한 번 더 부르지 않고,
 * 바로 폰 규칙 비서로 답하는지 보는 시험.
 *
 * 서버는 AI 주소들을 하나의 제한(1분 20번·하루 300번)으로 묶어 세고, 답 하나에 8초 한도를 둔다(safe-route #333).
 * 실패한 뒤에 지도 대화를 또 부르면 한 문장이 2번으로 세어지고, 사용자는 최대 16초를 기다리게 된다.
 */
class ServerFailsNoSecondCallTest {
    /** 지도 대화 자리에 끼우는 가짜 비서. 불린 횟수만 세고 정해 둔 답을 낸다. */
    private class CountingMapChat : AppAssistant {
        var calls = 0
        override suspend fun reply(history: List<ChatLine>, context: AssistantContext): AssistantAction {
            calls++
            return AssistantAction.Answer("지도 대화의 답")
        }
    }

    private val context = AssistantContext(UserMode.SENIOR, AppSettings())
    private val said = "고양이 좋아해?"

    private fun askWith(mapChat: CountingMapChat, post: suspend (String, JSONObject) -> JSONObject): AssistantAction =
        runBlocking { ServerAppAssistant(mapChat, { "g" }, { emptyList() }, post).reply(listOf(ChatLine(true, said)), context) }

    private val phoneOnly get() = runBlocking { RuleAppAssistant.reply(listOf(ChatLine(true, said)), context) }

    /** 역할: 서버가 바쁘다고(503) 하거나 너무 많이 불렀다고(429) 하면, 지도 대화를 부르지 않고 폰 규칙으로 답한다. */
    @Test
    fun aServerErrorDoesNotCallTheMapChat() {
        for (code in listOf("AI_UNAVAILABLE", "TOO_MANY_REQUESTS")) {
            val mapChat = CountingMapChat()
            val reply = askWith(mapChat) { _, _ -> throw ApiException(code, "실패") }
            assertEquals(0, mapChat.calls)
            assertEquals(phoneOnly, reply)
        }
    }

    /** 역할: 인터넷이 끊기거나 10초 안에 답이 없을 때도 지도 대화를 부르지 않는다. */
    @Test
    fun noNetworkDoesNotCallTheMapChat() {
        val mapChat = CountingMapChat()
        val reply = askWith(mapChat) { _, _ -> throw IOException("시간 초과") }
        assertEquals(0, mapChat.calls)
        assertEquals(phoneOnly, reply)
    }

    /** 역할: 서버가 답은 했는데 앱이 쓸 수 없는 답이면, 예전처럼 지도 대화가 대신 답한다(실패가 아니라서). */
    @Test
    fun anUnusableAnswerStillUsesTheMapChat() {
        val mapChat = CountingMapChat()
        val reply = askWith(mapChat) { _, _ -> JSONObject("""{"say":"저장했어요.","tools":["update_my_info"]}""") }
        assertEquals(1, mapChat.calls)
        assertEquals(AssistantAction.Answer("지도 대화의 답"), reply)
    }
}
