package com.deungbul.anshimgil.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantTest {
    private val now = AssistantContext(UserMode.SENIOR, AppSettings())
    private fun rule(said: String) = runBlocking { RuleAppAssistant.reply(listOf(ChatLine(true, said)), now) }

    @Test
    fun nearestToiletRoutes() {
        val a = rule("근처 화장실로 안내해줘")
        assertEquals("toilet", (a as AssistantAction.GoNearby).kind)
    }

    @Test
    fun cannotSeeTextMakesItLarger() {
        assertEquals(TextSize.LARGE, (rule("글씨가 잘 안 보여요") as AssistantAction.ChangeSettings).textSize)
    }

    @Test
    fun busesAndReports() {
        assertTrue(rule("버스 언제 와") is AssistantAction.ShowBuses)
        assertTrue(rule("여기 보도블록이 부서졌어요") is AssistantAction.OpenReport)
        assertEquals(false, (rule("음성 안내 좀 꺼 줘") as AssistantAction.ChangeSettings).voiceGuide)
    }

    @Test
    fun urgentWordsAskAbout112First() {
        assertTrue(rule("넘어져서 다리가 아파요") is AssistantAction.Emergency)
        assertTrue(rule("누가 계속 따라와요") is AssistantAction.Emergency)
    }

    @Test
    fun saysWhatItWillDoNotWhatItDid() {
        assertTrue(!rule("글씨가 안 보여").say.contains("했어요") && !rule("글씨가 안 보여").say.contains("바꿨어요"))
    }

    @Test
    fun unknownAsksBack() {
        assertTrue(rule("음…") is AssistantAction.Ask)
    }

    private fun gemini(json: String) = JSONObject().put(
        "candidates",
        org.json.JSONArray().put(JSONObject().put("content", JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", json))))),
    )

    @Test
    fun parsesGeminiNearbyAndRejectsBadKind() {
        val ok = parseAssistantReply(gemini("""{"action":"nearby","say":"가까운 편의점으로 안내할게요.","nearbyKind":"store"}"""))
        assertEquals("store", (ok as AssistantAction.GoNearby).kind)
        assertNull(parseAssistantReply(gemini("""{"action":"nearby","say":"안내할게요.","nearbyKind":"hospital"}""")))
        assertNull(parseAssistantReply(gemini("""{"action":"answer","say":"好的 我来帮你"}""")))
    }

    /** Writes Gemini requests for the eval script when ASSISTANT_DUMP is set; does nothing otherwise. */
    @Test
    fun dumpEvalRequests() {
        val out = System.getenv("ASSISTANT_DUMP") ?: return
        val cases = listOf(
            "toilet" to "근처 화장실로 안내해줘", "toilet2" to "화장실 급해요", "store" to "가까운 편의점 어디야",
            "police" to "무서워요 파출소 가고 싶어", "text" to "글씨가 너무 작아서 안 보여요", "text2" to "화면이 잘 안 보여",
            "voiceoff" to "말소리 좀 꺼 줘", "wheel" to "휠체어 모드로 바꿔 줘", "bell" to "지도에 비상벨도 보여 줘",
            "goto" to "조선대병원 가고 싶어", "goto2" to "광주역까지 길 알려줘", "bus" to "버스 언제 와요",
            "report" to "여기 보도블록이 다 깨졌어요", "vague" to "도와줘", "howto" to "SOS는 어떻게 켜요?",
            "danger" to "누가 따라와요 무서워요", "fall" to "넘어져서 무릎이 아파요", "weather" to "오늘 날씨 어때?",
            "taxi" to "택시 불러 줘", "offtopic" to "점심 메뉴 추천해 줘", "sos" to "SOS 버튼이 안 보여요",
        )
        java.io.File(out).printWriter().use { w ->
            cases.forEach { (id, said) ->
                w.println(JSONObject().put("id", id).put("said", said).put("request", assistantRequest(listOf(ChatLine(true, said)), now)))
            }
        }
    }
}
