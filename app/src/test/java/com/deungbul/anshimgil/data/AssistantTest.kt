package com.deungbul.anshimgil.data

// 시험에 쓰는 도구들
import kotlinx.coroutines.runBlocking   // runBlocking { … }: 기다려야 하는(suspend) 앱 함수를 시험 안에서 끝날 때까지 기다려 부름
import org.json.JSONObject              // 서버 답(JSON)을 코드로 만들 때 씀
import org.junit.Assert.assertEquals    // assertEquals(기대값, 실제값): 둘이 같아야 통과
import org.junit.Assert.assertNull      // assertNull(값): 값이 null(없음)이어야 통과
import org.junit.Assert.assertTrue      // assertTrue(조건): 조건이 참이어야 통과
import org.junit.Test                   // @Test가 붙은 함수 하나하나가 시험 하나

/**
 * AI 비서(말로 묻고 답하는 도우미) 시험.
 *
 * 사용자가 "근처 화장실로 안내해줘", "넘어졌어요"처럼 말하거나 글로 치면, 비서는 그 말을 앱이 할 일
 * 하나(AssistantAction)로 바꾼다. 예: 가까운 곳 안내(GoNearby), 설정 바꾸기(ChangeSettings),
 * 위급 안내(Emergency), 되묻기(Ask) 등.
 * 비서는 몇 겹으로 되어 있다.
 * - RuleAppAssistant: 폰 안의 낱말 규칙. 인터넷 없이 바로 답한다.
 * - ServerAppAssistant: 팀 서버(POST /api/assistant)에 먼저 묻고, 서버가 실패하면 폰 규칙으로 돌아간다.
 *   다만 급한 말·설정 바꾸기·버스·신고·가까운 곳 안내처럼 폰이 맡는 일은 서버에 묻지 않고 폰 규칙의 답을 바로 쓴다.
 * - FavoriteAppAssistant: "집", "학교" 같은 자주 가는 곳을 폰에 저장된 위치로 바꿔 준다.
 *
 * 여기서는 진짜 서버나 GPS를 쓰지 않는다. 사용자의 말을 직접 넣어 비서의 reply()를 부르거나,
 * 서버 답 모양의 JSON을 직접 만들어 앱의 읽기 함수(parseServerAssistantReply 등)에 넣고,
 * 돌려받은 할 일(AssistantAction)의 종류와 내용이 맞는지만 본다.
 * reply()는 대화 기록과 지금 앱 상태를 받아 할 일 하나(AssistantAction)를 돌려준다.
 */
class AssistantTest {
    /** 지금 앱 상태: 어르신 모드(UserMode.SENIOR)에 설정은 모두 기본값. 여러 시험이 함께 쓴다. */
    private val now = AssistantContext(UserMode.SENIOR, AppSettings())

    /**
     * 도우미 함수: 사용자가 [said]라고 한 번 말했을 때 폰 규칙 비서(RuleAppAssistant)가 내놓는 할 일을 돌려준다.
     * ChatLine(true, …)의 true는 "사용자가 한 말"이라는 뜻이다.
     */
    private fun rule(said: String) = runBlocking { RuleAppAssistant.reply(listOf(ChatLine(true, said)), now) }

    /** 역할: "근처 화장실로 안내해줘"라고 하면 앱 규칙이 가까운 화장실(toilet) 길찾기로 알아듣는지 확인한다. */
    @Test
    fun nearestToiletRoutes() {
        val a = rule("근처 화장실로 안내해줘")
        assertEquals("toilet", (a as AssistantAction.GoNearby).kind)
    }

    /** 역할: "글씨가 잘 안 보여요"라고 하면 글씨 크기 설정을 '크게'로 바꾸자고 하는지 확인한다. */
    @Test
    fun cannotSeeTextMakesItLarger() {
        assertEquals(TextSize.LARGE, (rule("글씨가 잘 안 보여요") as AssistantAction.ChangeSettings).textSize)
    }

    /** 역할: 버스 질문은 버스 보기로, "보도블록이 부서졌어요"는 불편 신고로, "음성 안내 꺼 줘"는 음성 안내 끄기로 알아듣는지 확인한다. */
    @Test
    fun busesAndReports() {
        assertTrue(rule("버스 언제 와") is AssistantAction.ShowBuses)
        assertTrue(rule("여기 보도블록이 부서졌어요") is AssistantAction.OpenReport)
        assertEquals(false, (rule("음성 안내 좀 꺼 줘") as AssistantAction.ChangeSettings).voiceGuide)
    }

    /** 역할: "넘어져서 다리가 아파요", "누가 계속 따라와요" 같은 급한 말을 위급 안내(Emergency)로 알아듣는지 확인한다. */
    @Test
    fun urgentWordsAskAbout112First() {
        assertTrue(rule("넘어져서 다리가 아파요") is AssistantAction.Emergency)
        assertTrue(rule("누가 계속 따라와요") is AssistantAction.Emergency)
    }

    /** 역할: 비서의 대답이 "바꿨어요"(이미 한 일)가 아니라 앞으로 할 일을 말하는지 확인한다. */
    @Test
    fun saysWhatItWillDoNotWhatItDid() {
        assertTrue(!rule("글씨가 안 보여").say.contains("했어요") && !rule("글씨가 안 보여").say.contains("바꿨어요"))
    }

    /** 역할: "음…"처럼 뜻을 모르는 말에는 무엇을 도와드릴지 되묻는(Ask) 대답을 하는지 확인한다. */
    @Test
    fun unknownAsksBack() {
        assertTrue(rule("음…") is AssistantAction.Ask)
    }

    private fun gemini(json: String) = JSONObject(json)

    /** 역할: Gemini가 보낸 JSON 답에서 약속된 가까운 곳 종류(store)는 받아들이고, 약속에 없는 종류(hospital)나 중국어 대답은 버리는지 확인한다. */
    @Test
    fun parsesGeminiNearbyAndRejectsBadKind() {
        val ok = parseAssistantJson(gemini("""{"action":"nearby","say":"가까운 편의점으로 안내할게요.","nearbyKind":"store"}"""))
        assertEquals("store", (ok as AssistantAction.GoNearby).kind)
        assertNull(parseAssistantJson(gemini("""{"action":"nearby","say":"안내할게요.","nearbyKind":"hospital"}""")))
        assertNull(parseAssistantJson(gemini("""{"action":"answer","say":"好的 我来帮你"}""")))
    }

    // --- server assistant (POST /api/assistant), shapes copied from the live server 11/6 ---

    /** 역할: 서버가 버튼(선택지)과 함께 되물으면, 같은 문장은 한 번만 말하고 버튼 이름·값과 대화 번호를 그대로 지니는지 확인한다. */
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

    /** 역할: 서버가 처음 쓰는 폰에 "어떻게 다니세요?"(걸어서·휠체어 버튼)라고 묻는 첫 설정 질문을 앱이 알아보는지 확인한다. */
    @Test
    fun serverFirstTimeSetupIsRecognised() {
        val json = JSONObject(
            """{"say":"처음 오셨네요. 어떻게 다니세요?","ask":"처음 오셨네요. 어떻게 다니세요?",
               "choices":[{"value":"walk","label":"걸어서"},{"value":"wheelchair","label":"휠체어"}],
               "tools":["get_my_info"],"conversationId":"c2"}""",
        )
        assertTrue(parseServerAssistantReply(json)!!.setupQuestion)
    }

    /** 역할: 서버가 추천한 길(pick)이 오면 그 목적지(남광주시장)로 길안내를 시작하는 동작이 되는지 확인한다. */
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

    /** 역할: 서버가 가까운 장소(place)를 알려 주면 그 장소로 가는 동작이 되고 이름·거리가 그대로 들어가는지 확인한다. */
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

    /** 역할: 서버의 그냥 대답은 대답(Answer)이 되고, 할 말(say)이 비어 있으면 서버 답을 버리는지(null) 확인한다. */
    @Test
    fun serverPlainAnswerAndEmptySay() {
        assertEquals(
            AssistantAction.Answer("저는 길 안내를 도와드려요."),
            parseServerAssistantReply(JSONObject("""{"say":"저는 길 안내를 도와드려요.","tools":[]}"""))!!.action,
        )
        assertNull(parseServerAssistantReply(JSONObject("""{"say":"","tools":[]}""")))
    }

    /** 역할: 서버에 보내는 질문에 시각(분까지)·고른 버튼 값·모드·자주 가는 곳이 담기고, 위치를 모르면 lat·lon이 빈 값인지 확인한다. */
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

    /** 역할: 말로 한 질문은 음성 인식의 다른 후보들도 서버에 함께 보내고, 글로 친 질문은 후보 목록이 비어 있는지 확인한다. */
    @Test
    fun serverRequestCarriesTheOtherGuessesOfWhatWasSaid() {
        // 채림 11/12: 후보 여러 개를 보내면 서버가 장소가 찾아지는 후보를 고른다.
        val spoken = serverAssistantRequest(
            guestId = "g", text = "남광주 시장 가자", mode = UserMode.SENIOR, where = null,
            now = java.time.LocalDateTime.of(2026, 11, 12, 18, 0), conversationId = null, choice = null,
            favorites = emptyList(), alternatives = listOf("남광주시장 가자", "남광주 시장 가자고"),
        )
        val a = spoken.getJSONArray("alternatives")
        assertEquals(listOf("남광주시장 가자", "남광주 시장 가자고"), (0 until a.length()).map { a.getString(it) })
        val typed = serverAssistantRequest(
            guestId = "g", text = "문화전당 가자", mode = UserMode.SENIOR, where = null,
            now = java.time.LocalDateTime.of(2026, 11, 12, 18, 0), conversationId = null, choice = null, favorites = emptyList(),
        )
        assertEquals(0, typed.getJSONArray("alternatives").length())
    }

    /** 역할: 서버가 알아들은 내용(heard)을 "출발 → 들를 곳 → 도착" 한 줄로 바르게 요약하고, heard가 없으면 비워 두는지 확인한다. */
    @Test
    fun whatTheServerUnderstood() {
        // 채림 #243: heard{text, alternative, origin, stops, destination}
        val json = JSONObject(
            """{"say":"'광주역에서 다이소 갔다가 문화전당 갈래'라고 알아들었어요. 30분 걸려요.",
                "pick":{"type":"walk","request":{"origin":{"name":"광주역","lat":35.165,"lon":126.909},
                  "destination":{"name":"국립아시아문화전당","lat":35.146,"lon":126.92},
                  "stops":[{"name":"다이소 광주역점","lat":35.163,"lon":126.91}]}},
                "heard":{"text":"광주역에서 다이소 갔다가 문화전당 갈래","alternative":true,"origin":"광주역",
                  "stops":["다이소 광주역점"],"destination":"국립아시아문화전당"}}""",
        )
        val heard = parseServerAssistantReply(json)!!.action.heard!!
        assertTrue(heard.alternative)
        assertEquals("출발 광주역 → 들를 곳 다이소 광주역점 → 도착 국립아시아문화전당", heard.summary())
        // No route: just the words. Here: no start.
        assertEquals("글씨 크게 해 줘", Heard("글씨 크게 해 줘", false, null, emptyList(), null).summary())
        assertEquals("도착 집", Heard("집 가자", false, null, emptyList(), "집").summary())
        assertNull(parseServerAssistantReply(JSONObject("""{"say":"안녕하세요"}"""))!!.action.heard)
    }

    /**
     * 역할: "넘어졌어요" 같은 급한 말은 서버 AI를 기다리지 않고 앱이 바로 위급 안내(Emergency)를 내는지 확인한다.
     * 서버가 느리거나 끊겨도 위급 안내가 늦어지면 안 되기 때문.
     */
    @Test
    fun urgentWordsNeverGoToTheServer() = runBlocking {
        // reply()는 기다려야 하는(suspend) 함수라서 시험 전체를 runBlocking { … } 안에서 돌린다.
        // Not configured for a real server in tests: if it were asked, the call would fail and fall back,
        // so check the rule answer comes straight back as Emergency.
        // 서버 비서를 만든다. 넣는 것 세 가지:
        //  - RuleAppAssistant: 서버가 실패했을 때 대신 답할 폰 규칙 비서
        //  - { "g" }: 손님 번호를 알려 주는 함수(시험용 가짜 번호 "g")
        //  - { emptyList() }: 자주 가는 곳 목록을 알려 주는 함수(시험에서는 없음)
        val assistant = ServerAppAssistant(RuleAppAssistant, { "g" }, { emptyList() })
        // 사용자가 "넘어졌어요"라고 한 번 말했을 때의 답을 받는다.
        // 앱 코드에서 서버 비서는 먼저 폰 규칙에 물어보고, 답이 위급 안내(Emergency)면 서버에 묻지 않고 그 답을 바로 돌려준다.
        // 돌려받은 답이 위급 안내(Emergency)여야 통과한다.
        assertTrue(assistant.reply(listOf(ChatLine(true, "넘어졌어요")), now) is AssistantAction.Emergency)
    }

    // --- saved places (자주 가는 곳) ---

    private val home = Favorite("집", Place(null, "우리집 주소", "", 35.1395, 126.9245, null), builtIn = true)
    private val school = Favorite("학교", null, builtIn = true)
    private val work = Favorite("직장", Place(null, "회사", "", 35.15, 126.91, null), builtIn = true)
    private val saved = listOf(home, school, work)

    /** 역할: 말 속의 "집", "회사", "학교"를 자주 가는 곳으로 알아듣되, "국밥집"처럼 낱말 일부인 경우는 아니라고 보는지 확인한다. */
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

    /** 역할: 장소 이름 뒤에 붙는 조사(이/가, 으로/로)를 받침에 맞게 고르는지 확인한다. */
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

    /** 역할: "집에 가자"를 서버가 처리하지 못해도, 폰에 저장한 집 위치로 바로 길을 찾는지 확인한다. */
    @Test
    fun savedPlaceGoesThereWhenTheServerCannot() = runBlocking {
        // The phone's own assistant would search the map for "집"
        val go = favoriteAssistant(AssistantAction.GoTo("집을 찾아볼게요.", "집")).reply(listOf(ChatLine(true, "집에 가자")), now)
        go as AssistantAction.GoPlace
        assertEquals("집", go.place.name)
        assertEquals(35.1395, go.place.lat, 1e-9)
        assertEquals("집으로 길을 찾을게요.", go.say)
    }

    /** 역할: 서버가 자주 가는 곳까지의 길(지하철 등)을 추천했으면 그 추천을 바꾸지 않고 그대로 쓰는지 확인한다. */
    @Test
    fun serverRecommendationForASavedPlaceIsKept() = runBlocking {
        val server = AssistantAction.GoPlace("집까지 지하철로 15분이에요.", home.place!!.copy(name = "집"), transit = true)
        assertEquals(server, favoriteAssistant(server).reply(listOf(ChatLine(true, "집에 가자")), now))
    }

    /** 역할: "집에 하트 아이콘 뭐야"처럼 지도 그림을 묻는 말을 '저장한 집'으로 잘못 알아듣지 않는지 확인한다. */
    @Test
    fun aQuestionAboutAMapPictureIsNotAboutTheSavedHome() = runBlocking {
        // 11/12 phone test: "집에 하트 아이콘 뭐야" got "집이 아직 등록되지 않았어요".
        val inner = AssistantAction.Answer("집 안 하트는 아동안전지킴이집이에요.")
        assertEquals(inner, favoriteAssistant(inner).reply(listOf(ChatLine(true, "집에 하트 아이콘 뭐야")), now))
    }

    /** 역할: 아직 등록하지 않은 자주 가는 곳(학교)으로 가자고 하면 지도에서 찾지 않고 등록 방법을 알려 주는지 확인한다. */
    @Test
    fun placeNotSavedYetIsToldNotSearched() = runBlocking {
        val answer = favoriteAssistant(AssistantAction.GoTo("", "학교")).reply(listOf(ChatLine(true, "학교 가자")), now)
        assertEquals(AssistantAction.Answer("학교가 아직 등록되지 않았어요. 설정의 '자주 가는 곳'에서 등록하면 바로 안내해 드릴게요."), answer)
    }

    /** 역할: 서버가 대중교통 길(type transit)을 추천하면 대중교통 탭으로 열리는지 확인한다. */
    @Test
    fun serverTransitPickOpensTheTransitTab() {
        val json = JSONObject(
            """{"say":"집까지 지하철 광주 1호선으로 15분 걸려요.","pick":{"type":"transit","summary":"","minutes":15,"route":null,
               "request":{"destination":{"lat":35.1395,"lon":126.9245,"name":"집"}}},"tools":["recommend_trip"]}""",
        )
        assertTrue((parseServerAssistantReply(json)!!.action as AssistantAction.GoPlace).transit)
    }

    // --- 불편 신고 by voice ---

    /** 역할: 말로 한 불편 신고에서 종류(공사·파손·경사·턱·장애물·사고)를 알아듣고, "여기" 같은 말이 없으면 신고로 보지 않는지 확인한다. */
    @Test
    fun reportKindIsHeard() {
        // The team server's rule (RuleBrain.reportType): a "here" word, then the kind.
        assertEquals(ReportType.CONSTRUCTION, reportTypeIn("여기 공사 중이라 못 지나가"))
        assertEquals(ReportType.BROKEN, reportTypeIn("여기 보도블록이 깨져 있어"))
        assertEquals(ReportType.STEEP, reportTypeIn("여기 길이 너무 가팔라요"))
        assertEquals(ReportType.CURB, reportTypeIn("턱이 높아서 휠체어가 못 가"))
        assertEquals(ReportType.OBSTACLE, reportTypeIn("여기 킥보드가 길을 막아 놨어"))
        assertEquals(ReportType.ACCIDENT, reportTypeIn("여기 사고 났어"))
        // Without a "here" word it is not a report (as on the server).
        assertNull(reportTypeIn("보도블록이 깨졌어"))
        assertNull(reportTypeIn("신고하고 싶어"))
        assertNull(reportTypeIn("주차장 가자"))
    }

    /** 역할: 말로 신고하면 신고 화면이 종류와 메모가 채워진 채로 열리고, 그냥 "신고할래"는 빈 화면으로 열리는지 확인한다. */
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

    /** 역할: "편의점이 안 보여"처럼 장소가 안 보인다는 말은 글씨 크기 바꾸기로 잘못 알아듣지 않는지 확인한다(11/6 폰 시험에서 나온 문제). */
    @Test
    fun cannotFindAPlaceIsNotTextSize() {
        assertTrue(rule("이마트24조대글로벌점 몇층에있어 안보여") !is AssistantAction.ChangeSettings)
        assertTrue(rule("편의점이 안 보여") !is AssistantAction.ChangeSettings)
        assertEquals(TextSize.LARGE, (rule("글씨가 잘 안 보여요") as AssistantAction.ChangeSettings).textSize)
        assertEquals(TextSize.LARGE, (rule("화면이 잘 안 보여") as AssistantAction.ChangeSettings).textSize)
        assertEquals(TextSize.LARGE, (rule("잘 안 보여요") as AssistantAction.ChangeSettings).textSize)
    }

    /** 역할: 방금 찾은 장소에 대해 "몇층에있어", "못 찾겠어"라고 물으면 주소를 알려 주거나 지도에 보여 주는 대답을 하는지 확인한다. */
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

    private fun undo(said: String, usage: Usage, at: java.time.LocalDateTime = java.time.LocalDateTime.of(2026, 11, 8, 17, 10)) =
        undoAdaptation(said.replace(" ", ""), usage, at)

    /** 역할: 앱이 알아서 바꾼 것(글씨 키우기, 캘린더 알림 등)을 "원래대로 해 줘"라고 말해 되돌릴 수 있는지 확인한다. */
    @Test
    fun aMistakenAnswerCanBeTakenBackByAsking() {
        val boosted = Usage(textBoost = 1.15f)
        // There are no big buttons any more: nothing to take back about buttons.
        assertNull(undo("버튼 원래대로 해 줘", boosted))
        assertTrue(undo("글씨 원래대로", boosted)!!.undoTextBoost)
        assertEquals(false, undo("캘린더 알림 꺼 줘", Usage(calendarOn = true))!!.calendar)
        assertEquals(false, undo("알아서 바꾸지 마", Usage())!!.adapt)
        // Through the whole assistant too.
        val ctx = AssistantContext(UserMode.SENIOR, AppSettings(), usage = boosted)
        val a = runBlocking { RuleAppAssistant.reply(listOf(ChatLine(true, "글씨 원래대로 돌려줘")), ctx) }
        assertTrue((a as AssistantAction.ChangeSettings).undoTextBoost)
    }

    /** 역할: "방금 거 원래대로"는 가장 최근에 바뀐 것을 되돌리고, 오래전 일이거나 바뀐 게 없으면 짐작하지 않는지 확인한다. */
    @Test
    fun justNowMeansTheLastChange() {
        val at = java.time.LocalDateTime.of(2026, 11, 8, 17, 0)
        val u = Usage(textBoost = 1.3f, lastChange = Adaptation.MISSED_TAPS to at)
        assertTrue(undo("방금 거 잘못 눌렀어 원래대로 해줘", u)!!.textBoostStepBack)
        assertTrue(undo("방금 거 잘못 눌렀어 원래대로 해줘", u.copy(lastChange = Adaptation.BIG_TEXT to at))!!.textBoostStepBack)
        assertEquals(false, undo("실수로 눌렀어", Usage(calendarOn = true, lastChange = Adaptation.CALENDAR to at))!!.calendar)
        // Long ago, or nothing changed: not guessed.
        assertNull(undo("원래대로 해줘", u, at.plusHours(2)))
        assertNull(undo("원래대로 해줘", Usage()))
    }

    /** 역할: "길 안내 취소해줘", "지도 원래대로" 같은 다른 말을 되돌리기로 잘못 알아듣지 않는지 확인한다. */
    @Test
    fun otherWordsAreNotTakenForAnUndo() {
        val u = Usage(textBoost = 1.3f, lastChange = Adaptation.MISSED_TAPS to java.time.LocalDateTime.of(2026, 11, 8, 17, 0))
        assertNull(undo("길 안내 취소해줘", u))
        assertNull(undo("지도 원래대로", u))
        assertNull(undo("가까운 편의점", u))
        // Without the AI's enlargement, "글씨 작게" is the text size setting as before.
        val small = runBlocking { RuleAppAssistant.reply(listOf(ChatLine(true, "글씨 작게 해줘")), now) }
        assertEquals(TextSize.SMALL, (small as AssistantAction.ChangeSettings).textSize)
    }

    /** 역할: "글씨가 너무 작아"라고 할 때마다 설정 '크게' → 비서의 추가 확대 → "가장 큰 글씨예요" 순서로 한 단계씩 커지는지 확인한다. */
    @Test
    fun textTooSmallGrowsStepByStep() {
        fun said(settings: AppSettings, usage: Usage = Usage()) =
            runBlocking { RuleAppAssistant.reply(listOf(ChatLine(true, "글씨가 너무 작아 안 보여")), AssistantContext(UserMode.SENIOR, settings, usage = usage)) }
        assertEquals(TextSize.LARGE, (said(AppSettings()) as AssistantAction.ChangeSettings).textSize)
        // Already 크게: AI 비서's own enlargement, the setting stays.
        val up = said(AppSettings(textSize = TextSize.LARGE)) as AssistantAction.ChangeSettings
        assertTrue(up.textBoostUp)
        assertNull(up.textSize)
        assertTrue((said(AppSettings(textSize = TextSize.LARGE), Usage(textBoost = 1.15f)) as AssistantAction.ChangeSettings).textBoostUp)
        // The biggest: said so, nothing pretends to change.
        assertTrue(said(AppSettings(textSize = TextSize.LARGE), Usage(textBoost = 1.3f)) is AssistantAction.Answer)
    }

    /**
     * 역할: 급한 말을 팀 서버와 같은 규칙으로 가려내고(같은 안내 문장 EMERGENCY_SAY), 급하지 않은 비슷한 말은 위급으로 보지 않는지 확인한다.
     * From the 11/9 test of 63 sentences (reports/assistant-eval-1109.html).
     */
    @Test
    fun emergencyFollowsTheTeamServerRule() {
        // The server's urgent words (RuleBrain.URGENT) and answer.
        val urgent = rule("넘어졌어 일어나기가 힘들어")
        assertTrue(urgent is AssistantAction.Emergency)
        assertEquals(EMERGENCY_SAY, (urgent as AssistantAction.Emergency).say)
        assertTrue(rule("어지러워 쓰러질 것 같아") is AssistantAction.Emergency)
        assertTrue(rule("너무 아파") is AssistantAction.Emergency)
        assertTrue(rule("숨이 차서 못 걷겠어") is AssistantAction.Emergency)
        assertTrue(rule("도와줘") is AssistantAction.Emergency)
        // "다쳐서" with a way of walking is about walking, not a fall (as on the server).
        assertTrue(rule("다리를 다쳐서 휠체어 타고 다녀") !is AssistantAction.Emergency)
        // Added on the server on 11/10 (safe-route #136), so on the phone too.
        assertTrue(rule("119 불러 줘") is AssistantAction.Emergency)
        assertTrue(rule("누가 자꾸 쫓아오는 것 같아") is AssistantAction.Emergency)
        assertTrue(rule("구급차 좀 불러 줘") is AssistantAction.Emergency)
        assertTrue(rule("누가 미행하는 것 같아") is AssistantAction.Emergency)
        assertTrue(rule("오늘 무릎이 아파") !is AssistantAction.Emergency)
        assertTrue(rule("집에 갈 건데 밤이라 무서워") !is AssistantAction.Emergency)
    }

    /** 역할: "보도턱 낮춘 횡단보도 보여 줘"처럼 지도에 보여 달라는 말은 신고로 알아듣지 않는지 확인한다. */
    @Test
    fun showingOnTheMapIsNotAReport() {
        assertTrue(rule("장애인 화장실이랑 보도턱 낮춘 횡단보도 보여 줘 이번 달까지") !is AssistantAction.OpenReport)
        assertTrue(rule("가까운 버스 보여 줘") is AssistantAction.ShowBuses)
        assertTrue(rule("여기 턱이 높아서 신고할래") is AssistantAction.OpenReport)
    }

    /** 역할: 서버가 추천한 길에 붙인 이유(because)를 대답 아래에 "· " 목록으로 덧붙여 보여 주는지 확인한다. */
    @Test
    fun whyTheTripCameFirstIsShown() {
        // The server's because (TripScorer reasons) under what it says, for a recommended trip.
        val json = org.json.JSONObject(
            """{"say":"문화전당까지 19분 걸려요.","pick":{"type":"walk","request":{"destination":{"name":"국립아시아문화전당","lat":35.1467,"lon":126.9203}}},
            "because":["지난번 이런 날씨에도 걸어가셨어요","내 걸음 속도로 계산"],"choices":[],"tools":["recommend_trip"]}""",
        )
        val action = parseServerAssistantReply(json)!!.action
        assertEquals("문화전당까지 19분 걸려요.\n· 지난번 이런 날씨에도 걸어가셨어요\n· 내 걸음 속도로 계산", action.say)
    }
}
