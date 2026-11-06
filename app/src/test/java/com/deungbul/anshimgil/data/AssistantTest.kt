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

    // --- server assistant (POST /api/assistant), shapes copied from the live server 11/6 ---

    @Test
    fun serverAskWithButtonsBecomesAskAndKeepsValues() {
        val json = JSONObject(
            """{"say":"지하철역 엘리베이터 출구 말씀이세요, 아니면 엘리베이터 있는 육교 말씀이세요?",
               "ask":"지하철역 엘리베이터 출구 말씀이세요, 아니면 엘리베이터 있는 육교 말씀이세요?",
               "choices":[{"value":"subway","label":"지하철역 엘리베이터 출구"},{"value":"footbridge","label":"엘리베이터 육교"}],
               "pick":null,"place":null,"tools":[],"ai":false,"conversationId":"c1"}""",
        )
        val reply = parseServerAssistantReply(json)!!
        val ask = reply.action as AssistantAction.Ask
        // say and ask are the same sentence, so it is said once
        assertEquals("지하철역 엘리베이터 출구 말씀이세요, 아니면 엘리베이터 있는 육교 말씀이세요?", ask.say)
        assertEquals(listOf("지하철역 엘리베이터 출구", "엘리베이터 육교"), ask.choices)
        assertEquals(listOf("subway" to "지하철역 엘리베이터 출구", "footbridge" to "엘리베이터 육교"), reply.choices)
        assertEquals("c1", reply.conversationId)
        assertTrue(!reply.setupQuestion)
    }

    @Test
    fun serverFirstTimeSetupIsRecognised() {
        val json = JSONObject(
            """{"say":"처음 오셨네요. 어떻게 다니세요?","ask":"처음 오셨네요. 어떻게 다니세요?",
               "choices":[{"value":"walk","label":"걸어서"},{"value":"wheelchair","label":"휠체어"}],
               "tools":["get_my_info"],"conversationId":"c2"}""",
        )
        assertTrue(parseServerAssistantReply(json)!!.setupQuestion)
    }

    @Test
    fun serverPickRoutesToItsDestination() {
        val json = JSONObject(
            """{"say":"남광주시장까지 걸어서 12분이에요.","ask":null,"choices":[],
               "pick":{"type":"walk","summary":"걸어서 12분, 820m","minutes":12,"route":"safe",
                 "request":{"origin":{"lat":35.14,"lon":126.91,"name":"현재 위치"},
                            "destination":{"lat":35.1395,"lon":126.9245,"name":"남광주시장"},"mode":"senior","night":"auto"}},
               "place":null,"tools":["search_place","recommend_trip"],"conversationId":"c3"}""",
        )
        val go = parseServerAssistantReply(json)!!.action as AssistantAction.GoPlace
        assertEquals("남광주시장", go.place.name)
        assertEquals(35.1395, go.place.lat, 1e-9)
        assertEquals("남광주시장까지 걸어서 12분이에요.", go.say)
    }

    @Test
    fun serverNearbyPlaceRoutesThere() {
        val json = JSONObject(
            """{"say":"가장 가까운 장애인 화장실은 120m예요.","place":{"kind":"toilet","name":"동구청 화장실","lat":35.146,"lon":126.923,"distanceM":120,"status":"open"},
               "tools":["find_nearby"]}""",
        )
        val go = parseServerAssistantReply(json)!!.action as AssistantAction.GoPlace
        assertEquals("동구청 화장실", go.place.name)
        assertEquals(120, go.place.distanceM)
    }

    @Test
    fun serverPlainAnswerAndEmptySay() {
        assertEquals(
            AssistantAction.Answer("저는 길 안내를 도와드려요."),
            parseServerAssistantReply(JSONObject("""{"say":"저는 길 안내를 도와드려요.","tools":[]}"""))!!.action,
        )
        assertNull(parseServerAssistantReply(JSONObject("""{"say":"","tools":[]}""")))
    }

    @Test
    fun serverRequestCarriesConversationAndNoPositionWhenUnknown() {
        val body = serverAssistantRequest(
            guestId = "g", text = "엘리베이터 육교", mode = UserMode.DISABLED, where = null,
            now = java.time.LocalDateTime.of(2026, 11, 6, 12, 5, 33), conversationId = "c1", choice = "footbridge",
            favorites = listOf("집" to LatLon(35.1, 126.9)),
        )
        assertEquals("2026-11-06T12:05", body.getString("now"))
        assertEquals("footbridge", body.getString("choice"))
        assertEquals("wheelchair", body.getString("mode"))
        assertTrue(body.isNull("lat") && body.isNull("lon"))
        assertEquals("집", body.getJSONArray("favorites").getJSONObject(0).getString("name"))
    }

    @Test
    fun urgentWordsNeverGoToTheServer() = runBlocking {
        // Not configured for a real server in tests: if it were asked, the call would fail and fall back,
        // so check the rule answer comes straight back as Emergency.
        val assistant = ServerAppAssistant(RuleAppAssistant, { "g" }, { emptyList() })
        assertTrue(assistant.reply(listOf(ChatLine(true, "넘어졌어요")), now) is AssistantAction.Emergency)
    }

    // --- saved places (자주 가는 곳) ---

    private val home = Favorite("집", Place(null, "우리집 주소", "", 35.1395, 126.9245, null), builtIn = true)
    private val school = Favorite("학교", null, builtIn = true)
    private val work = Favorite("직장", Place(null, "회사", "", 35.15, 126.91, null), builtIn = true)
    private val saved = listOf(home, school, work)

    @Test
    fun savedPlaceNamesAreHeardAsWords() {
        assertEquals(home, favoriteIn("집에 가자", saved))
        assertEquals(home, favoriteIn("우리집 가는 길", saved))
        assertEquals(home, favoriteIn("집", saved))
        assertEquals(work, favoriteIn("회사 가는 길 알려줘", saved))
        assertEquals(school, favoriteIn("학교까지 얼마나 걸려", saved))
        assertNull(favoriteIn("근처 국밥집 가고 싶어", saved))
        assertNull(favoriteIn("편의점 어디야", saved))
    }

    @Test
    fun koreanParticles() {
        assertEquals("집이", withSubject("집"))
        assertEquals("학교가", withSubject("학교"))
        assertEquals("집으로", withDirection("집"))
        assertEquals("학교로", withDirection("학교"))
        assertEquals("서울로", withDirection("서울"))
    }

    private fun favoriteAssistant(inner: AssistantAction) =
        FavoriteAppAssistant(object : AppAssistant {
            override suspend fun reply(history: List<ChatLine>, context: AssistantContext) = inner
        }, { saved })

    @Test
    fun savedPlaceGoesThereWhenTheServerCannot() = runBlocking {
        // The phone's own assistant would search the map for "집"
        val go = favoriteAssistant(AssistantAction.GoTo("집을 찾아볼게요.", "집")).reply(listOf(ChatLine(true, "집에 가자")), now)
        go as AssistantAction.GoPlace
        assertEquals("집", go.place.name)
        assertEquals(35.1395, go.place.lat, 1e-9)
        assertEquals("집으로 길을 찾을게요.", go.say)
    }

    @Test
    fun serverRecommendationForASavedPlaceIsKept() = runBlocking {
        val server = AssistantAction.GoPlace("집까지 지하철로 15분이에요.", home.place!!.copy(name = "집"), transit = true)
        assertEquals(server, favoriteAssistant(server).reply(listOf(ChatLine(true, "집에 가자")), now))
    }

    @Test
    fun placeNotSavedYetIsToldNotSearched() = runBlocking {
        val answer = favoriteAssistant(AssistantAction.GoTo("", "학교")).reply(listOf(ChatLine(true, "학교 가자")), now)
        assertEquals(AssistantAction.Answer("학교가 아직 등록되지 않았어요. 설정의 '자주 가는 곳'에서 등록하면 바로 안내해 드릴게요."), answer)
    }

    @Test
    fun serverTransitPickOpensTheTransitTab() {
        val json = JSONObject(
            """{"say":"집까지 지하철 광주 1호선으로 15분 걸려요.","pick":{"type":"transit","summary":"","minutes":15,"route":null,
               "request":{"destination":{"lat":35.1395,"lon":126.9245,"name":"집"}}},"tools":["recommend_trip"]}""",
        )
        assertTrue((parseServerAssistantReply(json)!!.action as AssistantAction.GoPlace).transit)
    }

    // --- 불편 신고 by voice ---

    @Test
    fun reportKindIsHeard() {
        assertEquals(ReportType.CONSTRUCTION, reportTypeIn("여기 공사 중이라 못 지나가"))
        assertEquals(ReportType.BROKEN, reportTypeIn("보도블록이 깨졌어"))
        assertEquals(ReportType.STEEP, reportTypeIn("길이 너무 가팔라요"))
        assertEquals(ReportType.CURB, reportTypeIn("턱이 높아서 휠체어가 못 올라가"))
        assertEquals(ReportType.OBSTACLE, reportTypeIn("킥보드가 길을 막고 있어"))
        assertNull(reportTypeIn("신고하고 싶어"))
        assertNull(reportTypeIn("주차장 가자"))
    }

    @Test
    fun reportOpensFilledIn() {
        val r = rule("여기 공사 중이라 못 지나가") as AssistantAction.OpenReport
        assertEquals(ReportType.CONSTRUCTION, r.type)
        assertEquals("여기 공사 중이라 못 지나가", r.memo)
        assertTrue(r.say.startsWith("'공사' 신고를 지금 위치로 채워 둘게요."))
        // Just asking to report: open it empty, without a memo
        val plain = rule("신고할래") as AssistantAction.OpenReport
        assertNull(plain.type)
        assertEquals("", plain.memo)
    }

    // --- 11/6 phone test: "…몇층에있어 안보여" changed the text size ---

    @Test
    fun cannotFindAPlaceIsNotTextSize() {
        assertTrue(rule("이마트24조대글로벌점 몇층에있어 안보여") !is AssistantAction.ChangeSettings)
        assertTrue(rule("편의점이 안 보여") !is AssistantAction.ChangeSettings)
        assertEquals(TextSize.LARGE, (rule("글씨가 잘 안 보여요") as AssistantAction.ChangeSettings).textSize)
        assertEquals(TextSize.LARGE, (rule("화면이 잘 안 보여") as AssistantAction.ChangeSettings).textSize)
        assertEquals(TextSize.LARGE, (rule("잘 안 보여요") as AssistantAction.ChangeSettings).textSize)
    }

    @Test
    fun questionAboutThePlaceJustFound() {
        val store = Place("1", "이마트24조대글로벌점", "광주 동구 필문대로 309", 35.14, 126.93, 120)
        val a = placeDetailAnswer("그곳 몇층에있어", store)!!
        assertTrue(a.text, a.text.startsWith("이마트24조대글로벌점 주소는 광주 동구 필문대로 309 이에요."))
        assertTrue(a.text.contains("몇 층인지는 지도 자료에 없어서"))
        assertTrue(!a.showOnMap)
        // "안 보여" here means the building cannot be seen: show it on the map and guide there.
        val lost = placeDetailAnswer("이마트24조대글로벌점 몇층에있어 안보여", store)!!
        assertTrue(lost.showOnMap)
        assertTrue(lost.text, lost.text.endsWith("지도에서 위치를 보여 드리고 길을 안내할게요."))
        assertTrue(placeDetailAnswer("거기 못 찾겠어", store)!!.showOnMap)
        assertTrue(placeDetailAnswer("거기 주소 알려줘", store)!!.text.contains("필문대로"))
        assertNull(placeDetailAnswer("가까운 화장실", store))
        assertNull(placeDetailAnswer("글씨 크게 해 줘", store))
    }
}
