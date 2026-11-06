package com.deungbul.anshimgil.data

import com.deungbul.anshimgil.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/*
 * 말로 하기: an assistant on the map screen. The walker says what they want ("글씨가 안 보여",
 * "근처 화장실로 안내해 줘", "조선대병원 가고 싶어") and the app does it: change a setting, find the
 * nearest place of a kind, search a place, show buses, or open the report screen. Gemini picks the
 * action (in a fixed shape); the app's own word rules do it when there is no key or Gemini fails.
 * Only the words are sent, never the position.
 */

/** Nearby place kinds the server knows (`/api/places/nearby`), with their names. */
val NEARBY_KINDS = mapOf("toilet" to "화장실", "store" to "편의점", "police" to "파출소", "shelter" to "쉼터", "subway" to "지하철역")

/** What the assistant asks the app to do; [say] is what it tells the walker. */
sealed interface AssistantAction {
    val say: String

    /** Change settings; null fields stay as they are. */
    data class ChangeSettings(
        override val say: String,
        val mode: UserMode? = null,
        val textSize: TextSize? = null,
        val voiceGuide: Boolean? = null,
        val sosButton: Boolean? = null,
        val addMapItems: Set<String> = emptySet(),
        val removeMapItems: Set<String> = emptySet(),
    ) : AssistantAction

    /** Route to the nearest place of a kind ([NEARBY_KINDS] key). */
    data class GoNearby(override val say: String, val kind: String) : AssistantAction

    /** Search for a place by name and route there. */
    data class GoTo(override val say: String, val query: String) : AssistantAction

    data class ShowBuses(override val say: String) : AssistantAction
    data class OpenReport(override val say: String) : AssistantAction

    /** Ask back, with ready answers. */
    data class Ask(override val say: String, val choices: List<String>) : AssistantAction

    /** Something urgent (a fall, pain, danger): ask first whether to call 112 or tell a guardian. */
    data class Emergency(override val say: String) : AssistantAction

    /** Just an answer, nothing to do. */
    data class Answer(override val say: String) : AssistantAction
}

/** What the assistant knows about the app right now, so "글씨 더 크게" builds on the current setting. */
data class AssistantContext(val mode: UserMode, val settings: AppSettings)

interface AppAssistant {
    suspend fun reply(history: List<ChatLine>, context: AssistantContext): AssistantAction

    companion object {
        fun create(): AppAssistant =
            if (BuildConfig.GEMINI_API_KEY.isNotBlank()) GeminiAppAssistant(RuleAppAssistant) else RuleAppAssistant
    }
}

/** Word rules for the common requests; also the fallback when Gemini cannot answer. */
object RuleAppAssistant : AppAssistant {
    override suspend fun reply(history: List<ChatLine>, context: AssistantContext): AssistantAction {
        val said = history.lastOrNull { it.fromUser }?.text.orEmpty()
        val s = said.replace(" ", "")
        fun has(vararg w: String) = w.any { s.contains(it.replace(" ", "")) }
        if (has("넘어", "다쳤", "다쳐", "아파", "아퍼", "쓰러", "위험", "따라와", "따라오", "살려", "무서워", "도와주세요")) {
            return AssistantAction.Emergency("괜찮으세요? 112에 전화하거나 보호자에게 알릴까요?")
        }
        val wantsGo = has("안내", "가고싶", "가자", "가줘", "어디", "가까운", "근처", "찾아", "길")
        NEARBY_KINDS.entries.firstOrNull { (_, name) -> s.contains(name) }?.let { (kind, name) ->
            if (wantsGo || s.length <= name.length + 3) return AssistantAction.GoNearby("가까운 ${name}으로 길을 찾을게요.", kind)
        }
        return when {
            has("안보여", "안 보여", "작아", "크게") && has("글", "글씨", "글자", "안보여", "작아") ->
                AssistantAction.ChangeSettings("글씨를 크게 바꿀게요.", textSize = TextSize.LARGE)
            has("글씨", "글자") && has("작게", "커", "너무 커") -> AssistantAction.ChangeSettings("글씨를 작게 바꿀게요.", textSize = TextSize.SMALL)
            has("음성", "소리", "말소리") && has("꺼", "끄", "시끄") -> AssistantAction.ChangeSettings("음성 안내를 끌게요.", voiceGuide = false)
            has("음성", "소리", "읽어") && has("켜", "들려") -> AssistantAction.ChangeSettings("음성 안내를 켤게요.", voiceGuide = true)
            has("휠체어") -> AssistantAction.ChangeSettings("장애인(휠체어) 모드로 바꿀게요.", mode = UserMode.DISABLED)
            has("버스") -> AssistantAction.ShowBuses("가까운 정류장에 곧 오는 버스를 보여 드릴게요.")
            has("신고", "망가", "부서", "공사", "막혀") -> AssistantAction.OpenReport("불편 신고 화면을 열게요.")
            else -> AssistantAction.Ask(
                "무엇을 도와드릴까요?",
                listOf("근처 화장실로 안내해 줘", "글씨 크게 해 줘", "가까운 버스 보여 줘", "가까운 편의점"),
            )
        }
    }
}

/** Gemini picks the action in a fixed JSON shape; anything unusable falls back to the rules. */
class GeminiAppAssistant(private val fallback: AppAssistant) : AppAssistant {
    override suspend fun reply(history: List<ChatLine>, context: AssistantContext): AssistantAction =
        runCatching { ask(history, context) }.getOrNull() ?: fallback.reply(history, context)

    private suspend fun ask(history: List<ChatLine>, context: AssistantContext): AssistantAction? = withContext(Dispatchers.IO) {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${BuildConfig.GEMINI_MODEL}:generateContent")
        val body = assistantRequest(history, context).toString().toByteArray()
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
            outputStream.use { it.write(body) }
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            parseAssistantReply(JSONObject(connection.inputStream.bufferedReader().use { it.readText() }))
        } catch (e: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }
}

private val ASSISTANT_INSTRUCTION = """
너는 '세잎지도' 걷기 길안내 앱의 지도 화면 비서야. 사용자의 한국어 말을 보고 앱이 할 일 하나를 골라.
action:
- emergency: 넘어짐, 다침, 아픔, 위험, 누가 따라옴, 길을 잃어 무서움 같은 급한 말. 다른 일보다 먼저 이것을 골라. say는 "괜찮으세요? 112에 전화하거나 보호자에게 알릴까요?"처럼 묻기만 해.
- settings: 설정 바꾸기. 글씨가 안 보이거나 작다 → textSize를 한 단계 크게(SMALL→NORMAL→LARGE). 소리를 끄거나 켜기 → voiceGuide. 모드 바꾸기 → mode. 지도에 더 보기/빼기 → addMapItems/removeMapItems. SOS 버튼이 안 보이거나 켜 달라 → sosButton=true.
- nearby: 가까운 곳으로 길찾기. nearbyKind는 ${NEARBY_KINDS.entries.joinToString(", ") { "${it.key}(${it.value})" }} 중 하나.
- goto: 이름이 있는 장소로 길찾기. placeQuery에 검색할 장소 이름만.
- buses: 가까운 정류장의 버스 보기. report: 길이 망가졌거나 불편한 곳 신고하기.
- ask: 무엇을 원하는지 모를 때 짧게 되묻기, choices에 고를 답 2~4개.
- answer: 앱 사용법을 짧게 답하거나, 할 수 없는 일을 알릴 때.
지킬 규칙:
1. 앱이 아직 하지 않은 일을 했다고 말하지 마. say는 "~할게요", "~찾아볼게요"처럼 앞으로 할 일로 말해. "~했어요"는 쓰지 마. 결과는 앱이 따로 알려.
2. 위 action으로 할 수 없는 일(전화 걸기, 택시 부르기, 예약, 날씨 알려 주기 등)은 answer로 못 한다고 말하고, 앱으로 할 수 있는 다른 방법을 하나만 제안해.
3. 길 안내와 상관없는 질문에는 answer로 한 문장만 짧게 답하고 길 안내로 돌려.
바꾸지 않을 설정 칸은 지금 값 그대로 채워. nearbyKind를 안 쓰면 none, 쓰지 않는 글자 칸은 빈 글자, 목록은 빈 목록.
say에는 사용자에게 할 말을 존댓말 한국어 한두 문장(80자 안쪽)으로. 한자나 다른 언어는 쓰지 마.
지도 항목: ${SETUP_MAP_ITEMS.entries.joinToString(", ") { "${it.key}(${it.value})" }}.
""".trimIndent()

internal fun assistantRequest(history: List<ChatLine>, context: AssistantContext): JSONObject {
    val contents = JSONArray()
    history.takeLast(8).forEach { line ->
        contents.put(
            JSONObject().put("role", if (line.fromUser) "user" else "model")
                .put("parts", JSONArray().put(JSONObject().put("text", line.text))),
        )
    }
    fun str(vararg values: String) = JSONObject().put("type", "STRING").apply { if (values.isNotEmpty()) put("enum", JSONArray(values.toList())) }
    fun list(vararg values: String) = JSONObject().put("type", "ARRAY").put("items", str(*values))
    val fields = linkedMapOf(
        "action" to str("emergency", "settings", "nearby", "goto", "buses", "report", "ask", "answer"),
        "say" to str(),
        "mode" to str(*UserMode.entries.map { it.name }.toTypedArray()),
        "textSize" to str(*TextSize.entries.map { it.name }.toTypedArray()),
        "voiceGuide" to JSONObject().put("type", "BOOLEAN"),
        "sosButton" to JSONObject().put("type", "BOOLEAN"),
        "addMapItems" to list(*SETUP_MAP_ITEMS.keys.toTypedArray()),
        "removeMapItems" to list(*SETUP_MAP_ITEMS.keys.toTypedArray()),
        "nearbyKind" to str("none", *NEARBY_KINDS.keys.toTypedArray()),
        "placeQuery" to str(),
        "choices" to list(),
    )
    val schema = JSONObject().put("type", "OBJECT")
        .put("properties", JSONObject().apply { fields.forEach { (k, v) -> put(k, v) } })
        .put("required", JSONArray(fields.keys.toList()))
        .put("propertyOrdering", JSONArray(fields.keys.toList()))
    val s = context.settings
    val now = "\n지금 설정: mode=${context.mode.name}, textSize=${s.textSize.name}, voiceGuide=${s.voiceGuide}, " +
        "sosButton=${s.sosButton}, 지도에 더 보는 것=${s.extraMapItems.joinToString(",").ifEmpty { "없음" }}"
    return JSONObject()
        .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", ASSISTANT_INSTRUCTION + now))))
        .put("contents", contents)
        .put(
            "generationConfig",
            JSONObject().put("responseMimeType", "application/json").put("responseSchema", schema)
                .put("temperature", 0.2).put("maxOutputTokens", 400),
        )
}

/** Gemini's answer as an action, or null when the app cannot use it. */
internal fun parseAssistantReply(response: JSONObject): AssistantAction? {
    val text = response.optJSONArray("candidates")?.optJSONObject(0)
        ?.optJSONObject("content")?.optJSONArray("parts")?.let { parts ->
            (0 until parts.length()).joinToString("") { parts.optJSONObject(it)?.optString("text").orEmpty() }
        }?.takeIf { it.isNotBlank() } ?: return null
    val j = runCatching { JSONObject(text) }.getOrNull() ?: return null
    val say = j.optString("say").takeIf { looksLikeKorean(it) } ?: return null
    fun strings(key: String) = j.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
    return when (j.optString("action")) {
        "emergency" -> AssistantAction.Emergency(say)
        "settings" -> AssistantAction.ChangeSettings(
            say,
            mode = runCatching { UserMode.valueOf(j.optString("mode")) }.getOrNull(),
            textSize = runCatching { TextSize.valueOf(j.optString("textSize")) }.getOrNull(),
            voiceGuide = if (j.has("voiceGuide")) j.optBoolean("voiceGuide") else null,
            sosButton = if (j.has("sosButton")) j.optBoolean("sosButton") else null,
            addMapItems = strings("addMapItems").filter { it in SETUP_MAP_ITEMS }.toSet(),
            removeMapItems = strings("removeMapItems").filter { it in SETUP_MAP_ITEMS }.toSet(),
        )
        "nearby" -> j.optString("nearbyKind").takeIf { it in NEARBY_KINDS }?.let { AssistantAction.GoNearby(say, it) }
        "goto" -> j.optString("placeQuery").trim().takeIf { it.length >= 2 }?.let { AssistantAction.GoTo(say, it) }
        "buses" -> AssistantAction.ShowBuses(say)
        "report" -> AssistantAction.OpenReport(say)
        "ask" -> AssistantAction.Ask(say, strings("choices").filter { it.isNotBlank() && looksLikeKorean(it) }.take(4))
        "answer" -> AssistantAction.Answer(say)
        else -> null
    }
}
