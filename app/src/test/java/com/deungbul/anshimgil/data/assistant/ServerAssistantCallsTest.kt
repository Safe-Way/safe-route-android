package com.deungbul.anshimgil.data.assistant

import kotlinx.coroutines.runBlocking   // runBlocking { … }: 기다려야 하는(suspend) 앱 함수를 시험 안에서 끝날 때까지 기다려 부름
import org.json.JSONObject              // 서버 답(JSON)을 코드로 만들 때 씀
import org.junit.Assert.assertEquals    // assertEquals(기대값, 실제값): 둘이 같아야 통과
import org.junit.Assert.assertTrue      // assertTrue(조건): 조건이 참이어야 통과
import org.junit.Test                   // @Test가 붙은 함수 하나하나가 시험 하나
import com.deungbul.anshimgil.data.user.AppSettings
import com.deungbul.anshimgil.data.user.ChatLine
import com.deungbul.anshimgil.data.user.UserMode
import java.io.IOException

/**
 * 서버 비서(ServerAppAssistant)가 서버를 몇 번 부르는지 세는 시험.
 *
 * AssistantTest의 urgentWordsNeverGoToTheServer는 답이 위급 안내인지만 본다. 그런데 서버가 실패해도
 * 폰 규칙 비서가 같은 위급 안내를 내기 때문에, 그 시험만으로는 "서버에 가지 않았다"를 증명하지 못한다.
 * 여기서는 진짜 서버 대신 부른 횟수를 세는 가짜 서버(FakeServer)를 넣어서, 급한 말은 서버를 0번 부르는지
 * 직접 센다.
 */
class ServerAssistantCallsTest {
    /**
     * 가짜 서버. 앱이 서버에 보내는 요청을 받는 대신 횟수(calls)와 경로(paths)만 적고, 정해 둔 답을 돌려준다.
     * [fail]이 true면 인터넷이 끊긴 것처럼 IOException을 던진다.
     */
    private class FakeServer(private val answer: String = """{"say":"저는 길 안내를 도와드려요.","tools":[]}""", private val fail: Boolean = false) {
        var calls = 0
        val paths = mutableListOf<String>()
        val post: suspend (String, JSONObject) -> JSONObject = { path, _ ->
            calls++                                   // 부를 때마다 1씩 센다
            paths += path                             // 어느 주소로 보냈는지 적는다
            if (fail) throw IOException("인터넷 끊김")  // 실패 흉내
            JSONObject(answer)                        // 정해 둔 답
        }
    }

    /** 가짜 서버를 끼운 서버 비서. 손님 번호는 "g", 자주 가는 곳은 없음. */
    private fun assistantWith(server: FakeServer) =
        ServerAppAssistant(RuleAppAssistant, { "g" }, { emptyList() }, server.post)

    /** 시험용 상황: 노인 모드, 기본 설정, 위치 모름. */
    private val context = AssistantContext(UserMode.SENIOR, AppSettings())

    /** 사용자가 [said] 한 마디를 했을 때 비서의 답. */
    private fun ask(assistant: ServerAppAssistant, said: String) =
        runBlocking { assistant.reply(listOf(ChatLine(true, said)), context) }

    /** 역할: 급한 말은 서버를 한 번도 부르지 않고 바로 위급 안내가 나오는지, 부른 횟수를 직접 세어 확인한다. */
    @Test
    fun urgentWordsCallTheServerZeroTimes() {
        // 사람이 실제로 할 법한 급한 말들 (팀 서버와 같은 급한 낱말 목록 URGENT_WORDS에 걸리는 말)
        val urgent = listOf("길에서 넘어졌어요", "누가 따라와요", "살려 주세요", "피가 나요", "119 불러 줘", "숨이 안 쉬어져요", "다쳤어요")
        for (said in urgent) {
            val server = FakeServer()
            val reply = ask(assistantWith(server), said)
            // 답은 위급 안내여야 한다 (틀리면 어떤 말에서 틀렸는지 메시지에 나온다)
            assertTrue("\"$said\" → $reply", reply is AssistantAction.Emergency)
            // 그리고 서버는 0번 불려야 한다
            assertEquals("\"$said\"에 서버를 불렀어요", 0, server.calls)
        }
    }

    /** 역할: 급하지 않은 말은 서버를 정확히 한 번 부르고 서버 답을 그대로 쓰는지 확인한다. 가짜 서버가 제대로 끼워졌는지도 이걸로 안다. */
    @Test
    fun anOrdinaryQuestionGoesToTheServerOnce() {
        val server = FakeServer()
        val reply = ask(assistantWith(server), "고양이 좋아해?")
        assertEquals(1, server.calls)                                          // 한 번만 불렀다
        assertEquals(listOf("/api/assistant"), server.paths)                   // 비서 주소로 보냈다
        assertEquals(AssistantAction.Answer("저는 길 안내를 도와드려요."), reply)  // 서버 답을 그대로 썼다
    }

    /** 역할: 서버가 끊겨도 앱이 멈추지 않고, 폰 규칙 비서의 답으로 대신 답하는지 확인한다. */
    @Test
    fun whenTheServerFailsThePhoneAnswers() {
        val server = FakeServer(fail = true)
        val reply = ask(assistantWith(server), "고양이 좋아해?")
        // 폰 규칙 비서가 같은 말에 혼자 했을 답
        val phoneOnly = runBlocking { RuleAppAssistant.reply(listOf(ChatLine(true, "고양이 좋아해?")), context) }
        assertEquals(1, server.calls)      // 서버를 한 번 불렀지만 실패했다
        assertEquals(phoneOnly, reply)     // 그래서 폰 답으로 대신했다
    }
}
