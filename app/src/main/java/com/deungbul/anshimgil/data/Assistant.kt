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
 * Only the words are sent to Gemini, never the position.
 *
 * With the team server configured, the server's assistant (`POST /api/assistant`) answers first: it
 * recommends walking or the bus with reasons, finds places and keeps the conversation. Settings, buses,
 * reports and anything urgent stay on the phone, since the app's settings live here.
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
        /** 쓰는 대로 맞추기, also to take back an answer pressed by mistake in a bubble. */
        val undoTextBoost: Boolean = false,
        /** One step of the AI's text enlargement back. */
        val textBoostStepBack: Boolean = false,
        /** One step more of the AI's text enlargement (the setting is already 크게). */
        val textBoostUp: Boolean = false,
        val calendar: Boolean? = null,
        val adapt: Boolean? = null,
    ) : AssistantAction

    /** Route to the nearest place of a kind ([NEARBY_KINDS] key). */
    data class GoNearby(override val say: String, val kind: String) : AssistantAction

    /** Search for a place by name and route there. */
    data class GoTo(override val say: String, val query: String) : AssistantAction

    /**
     * Route to a place the server already found (its recommendation or a nearby place) or a saved place.
     * [transit] opens the 대중교통 tab, when the recommendation was the bus or subway.
     */
    data class GoPlace(override val say: String, val place: Place, val transit: Boolean = false) : AssistantAction

    data class ShowBuses(override val say: String) : AssistantAction
    /** Open 불편 신고 filled in from what was said: the kind of problem when it is clear, and the words as the memo. */
    data class OpenReport(override val say: String, val type: ReportType? = null, val memo: String = "") : AssistantAction

    /** Ask back, with ready answers. */
    data class Ask(override val say: String, val choices: List<String>) : AssistantAction

    /** Something urgent (a fall, pain, danger): ask first whether to call 112 or tell a guardian. */
    data class Emergency(override val say: String) : AssistantAction

    /** Just an answer, nothing to do. */
    data class Answer(override val say: String) : AssistantAction
}

/** What the assistant knows about the app right now, so "글씨 더 크게" builds on the current setting. */
data class AssistantContext(
    val mode: UserMode,
    val settings: AppSettings,
    /** What 쓰는 대로 맞추기 has changed, so that it can be taken back by asking. */
    val usage: Usage = Usage(),
    /** The phone's position when there is a real fix; asked only by the server assistant. */
    val where: suspend () -> LatLon? = { null },
)

interface AppAssistant {
    suspend fun reply(history: List<ChatLine>, context: AssistantContext): AssistantAction

    companion object {
        /**
         * [guestId] goes to the server assistant only; [favorites] (자주 가는 곳) are matched on the phone and
         * the saved ones also go to the server (they hold home, which only the phone knows).
         */
        fun create(
            guestId: suspend () -> String? = { null },
            favorites: suspend () -> List<Favorite> = { emptyList() },
        ): AppAssistant {
            val local = if (BuildConfig.GEMINI_API_KEY.isNotBlank()) GeminiAppAssistant(RuleAppAssistant) else RuleAppAssistant
            val saved: suspend () -> List<Pair<String, LatLon>> = {
                favorites().mapNotNull { f -> f.place?.let { f.name to LatLon(it.lat, it.lon) } }
            }
            val inner = if (ServerApi.isConfigured) ServerAppAssistant(local, guestId, saved) else local
            return FavoriteAppAssistant(inner, favorites)
        }
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
        undoAdaptation(s, context.usage)?.let { return it }
        return when {
            // Only about the words on screen: "글씨가 안 보여", "화면이 잘 안 보여", or just "잘 안 보여".
            // "편의점이 안 보여" is about a place, not the text size.
            // Bigger step by step: the setting up to 크게, then AI 비서's own enlargement, then say it is the biggest.
            textTooSmall(s) -> when {
                context.settings.textSize != TextSize.LARGE -> AssistantAction.ChangeSettings("글씨를 크게 바꿀게요.", textSize = TextSize.LARGE)
                nextTextBoost(context.usage.textBoost) != null ->
                    AssistantAction.ChangeSettings("글씨를 한 단계 더 크게 할게요.", textBoostUp = true)
                else -> AssistantAction.Answer("지금이 앱에서 가장 큰 글씨예요. 폰 설정에서 글자 크기를 키우면 더 커져요.")
            }
            has("글씨", "글자") && has("작게", "커", "너무 커") -> AssistantAction.ChangeSettings("글씨를 작게 바꿀게요.", textSize = TextSize.SMALL)
            has("음성", "소리", "말소리") && has("꺼", "끄", "시끄") -> AssistantAction.ChangeSettings("음성 안내를 끌게요.", voiceGuide = false)
            has("음성", "소리", "읽어") && has("켜", "들려") -> AssistantAction.ChangeSettings("음성 안내를 켤게요.", voiceGuide = true)
            has("휠체어") -> AssistantAction.ChangeSettings("장애인(휠체어) 모드로 바꿀게요.", mode = UserMode.DISABLED)
            has("버스") -> AssistantAction.ShowBuses("가까운 정류장에 곧 오는 버스를 보여 드릴게요.")
            reportTypeIn(said) != null || has("신고", "불편") -> reportAction(said)
            else -> AssistantAction.Ask(
                "무엇을 도와드릴까요?",
                listOf("근처 화장실로 안내해 줘", "글씨 크게 해 줘", "가까운 버스 보여 줘", "가까운 편의점"),
            )
        }
    }
}

/** Words for each kind of problem on the way, for 불편 신고 by voice. */
private val REPORT_WORDS = listOf(
    ReportType.CONSTRUCTION to listOf("공사"),
    ReportType.STEEP to listOf("경사", "비탈", "가팔"),
    ReportType.CURB to listOf("턱"),
    ReportType.BROKEN to listOf("깨졌", "깨진", "파손", "부서", "망가", "패인", "파였", "움푹", "들떴", "보도블록"),
    ReportType.OBSTACLE to listOf("막혀", "막고", "막혔", "못 지나", "못지나", "쓰레기", "킥보드"),
)

/** The kind of problem [said] describes, e.g. 공사 in "여기 공사 중이라 못 지나가"; null when it is not clear. */
fun reportTypeIn(said: String): ReportType? {
    val s = said.replace(" ", "")
    return REPORT_WORDS.firstOrNull { (_, words) -> words.any { s.contains(it.replace(" ", "")) } }?.first
}

/** Opens 불편 신고 with the kind and the walker's words filled in; they still check it and press 보내기. */
fun reportAction(said: String, say: String? = null): AssistantAction.OpenReport {
    val type = reportTypeIn(said)
    val words = say ?: if (type != null) {
        "'${type.label}' 신고를 지금 위치로 채워 둘게요. 사진을 붙이고 보내기를 누르면 신고돼요."
    } else {
        "불편 신고 화면을 열게요. 어떤 불편인지 고르고 보내기를 누르면 신고돼요."
    }
    // Just "신고할래" is not worth keeping as a memo.
    val memo = if (said.replace(" ", "").length > 6) said.trim().take(REPORT_MAX_MEMO) else ""
    return AssistantAction.OpenReport(words, type, memo)
}

/** Whether [s] (spaces removed) says the text is too small to read. */
/** How long after a change "방금 거 원래대로" still means that change. */
const val UNDO_RECENT_MIN = 30L

/**
 * Taking back what 쓰는 대로 맞추기 changed, by asking AI 비서 (an answer in a bubble pressed by mistake):
 * "버튼 원래대로", "글씨 원래대로" (the AI's enlargement first), "캘린더 알림 꺼 줘", "알아서 바꾸지 마", or just
 * "방금 거 잘못 눌렀어, 원래대로" for the change made in the last [UNDO_RECENT_MIN] minutes. [s] has no spaces.
 */
internal fun undoAdaptation(s: String, usage: Usage, now: java.time.LocalDateTime = java.time.LocalDateTime.now()): AssistantAction.ChangeSettings? {
    fun has(vararg w: String) = w.any { s.contains(it) }
    val back = has("원래", "되돌", "돌려", "취소", "잘못눌", "실수로", "처음대로", "이전대로", "전대로")
    val off = has("꺼", "끄", "그만", "지마", "지말", "싫어", "필요없")
    val on = has("켜", "알려줘", "봐줘", "써줘")
    val smaller = has("작게", "줄여", "너무커", "너무크")
    return when {
        has("글씨", "글자") && (back || smaller) && usage.textBoost > 1f ->
            ChangeSettingsUndo("AI가 키운 글씨를 원래대로 돌릴게요. 설정의 글자 크기는 그대로예요.", undoTextBoost = true)
        has("캘린더", "일정", "달력") && off -> ChangeSettingsUndo("캘린더 일정 알림을 끌게요.", calendar = false)
        has("캘린더", "일정", "달력") && on && has("알림", "길", "캘린더") -> ChangeSettingsUndo("캘린더 일정으로 길을 알려 드릴게요.", calendar = true)
        has("알아서", "자동으로", "맞추기", "맞춰") && off -> ChangeSettingsUndo("쓰는 대로 맞추기를 끌게요. 설정에서 다시 켤 수 있어요.", adapt = false)
        back && !has("길", "지도", "모드", "소리", "음성") -> {
            // The change made a moment ago.
            val last = usage.lastChange?.takeIf { java.time.Duration.between(it.second, now).toMinutes() in 0..UNDO_RECENT_MIN }
            when (last?.first) {
                Adaptation.MISSED_TAPS, Adaptation.BIG_TEXT, Adaptation.BIGGER_TEXT ->
                    ChangeSettingsUndo("방금 키운 글씨를 한 단계 전으로 돌릴게요.", textBoostStepBack = true).takeIf { usage.textBoost > 1f }
                Adaptation.CALENDAR -> ChangeSettingsUndo("방금 켠 캘린더 일정 알림을 끌게요.", calendar = false)
                null -> null
            }
        }
        else -> null
    }
}

@Suppress("FunctionName")
private fun ChangeSettingsUndo(
    say: String,
    undoTextBoost: Boolean = false,
    textBoostStepBack: Boolean = false,
    calendar: Boolean? = null,
    adapt: Boolean? = null,
) = AssistantAction.ChangeSettings(say, undoTextBoost = undoTextBoost, textBoostStepBack = textBoostStepBack, calendar = calendar, adapt = adapt)

internal fun textTooSmall(s: String): Boolean {
    val aboutText = listOf("글씨", "글자", "화면", "글이").any { s.contains(it) }
    val cannotSee = listOf("안보여", "작아", "크게", "흐려", "안보이").any { s.contains(it) }
    // A bare "(잘) 안 보여(요)" with nothing else is about the screen too.
    val bare = s.matches(Regex("""(잘)?안보여(요)?[.!?]*"""))
    return (aboutText && cannotSee) || bare
}

/** Words that point back at the place just found: "그곳", "거기", "그 가게". */
private val PLACE_BACK_WORDS = listOf("그곳", "거기", "그가게", "그편의점", "그화장실", "그건물", "그데", "저기")

/** Questions about where exactly a place is, which the map data can partly answer. */
private val PLACE_DETAIL_WORDS = listOf("몇층", "층에", "몇 층", "주소", "어디에있", "어디있", "위치", "어디야", "어디에요", "건물")

/**
 * An answer about [place] (the one just found) when [said] asks where exactly it is, e.g. "그곳 몇층에 있어"
 * or "이마트24조대글로벌점 몇층이야". Says the address and, honestly, that the floor is not in the map data.
 * Null when [said] is about something else.
 */
fun placeDetailAnswer(said: String, place: Place): PlaceAnswer? {
    val s = said.replace(" ", "")
    val aboutIt = PLACE_BACK_WORDS.any { s.contains(it) } || s.contains(place.name.replace(" ", ""))
    val cannotFind = CANNOT_FIND_WORDS.any { s.contains(it) }
    val asksWhere = cannotFind || PLACE_DETAIL_WORDS.any { s.contains(it.replace(" ", "")) }
    if (!aboutIt || !asksWhere) return null
    val address = place.address.takeIf { it.isNotBlank() }?.let { " 주소는 $it 이에요." }.orEmpty()
    val floor = if (s.contains("층")) " 몇 층인지는 지도 자료에 없어서 알려 드릴 수 없어요." else ""
    // Cannot see the building: show where it is and walk them there.
    val then = if (cannotFind) " 지도에서 위치를 보여 드리고 길을 안내할게요." else if (floor.isNotEmpty()) " 가까이 가면 간판을 확인해 주세요." else ""
    return PlaceAnswer("${place.name}${address}$floor$then".trim(), showOnMap = cannotFind)
}

/** What AI 비서 says about the place just found, and whether to open its route on the map. */
data class PlaceAnswer(val text: String, val showOnMap: Boolean)

/** "안 보여", "못 찾겠어": the walker cannot see or find the place. Not text size (that needs 글씨·화면). */
private val CANNOT_FIND_WORDS = listOf("안보여", "안보이", "못찾", "모르겠", "어딨", "어디있", "어디야")

/** Other words for a saved place's name: "회사" for 직장, "우리 집" for 집. */
private val FAVORITE_SYNONYMS = mapOf("직장" to listOf("회사", "일터"), "학교" to listOf("학원"))

/**
 * The saved place (자주 가는 곳) [said] is about, e.g. 집 in "집에 가자" or 직장 in "회사 가는 길".
 * The name must stand as a word, so "국밥집" is not 집.
 */
fun favoriteIn(said: String, favorites: List<Favorite>): Favorite? = favorites.firstOrNull { f ->
    (listOf(f.name) + FAVORITE_SYNONYMS[f.name].orEmpty()).any { name ->
        Regex("""(^|\s|우리)${Regex.escape(name)}(\s|$|에|으로|로|까지|가|이|은|는|을|를|쪽|방향)""").containsMatchIn(said.trim())
    }
}

/** "집이", "학교가": the subject particle for [word]. */
internal fun withSubject(word: String): String = word + if (hasBatchim(word)) "이" else "가"

/** "집으로", "학교로": the direction particle for [word] (ㄹ takes 로). */
internal fun withDirection(word: String): String {
    val last = word.lastOrNull() ?: return word
    val code = last.code - 0xAC00
    return word + if (code in 0..11171 && code % 28 != 0 && code % 28 != 8) "으로" else "로"
}

private fun hasBatchim(word: String): Boolean {
    val code = (word.lastOrNull()?.code ?: return false) - 0xAC00
    return code in 0..11171 && code % 28 != 0
}

/**
 * Saved places first: "집에 가자" goes home even when the server is down (the phone's own assistant would
 * search the map for a place called 집), and a place not saved yet gets told so instead of a search.
 */
class FavoriteAppAssistant(
    private val inner: AppAssistant,
    private val favorites: suspend () -> List<Favorite>,
) : AppAssistant {
    override suspend fun reply(history: List<ChatLine>, context: AssistantContext): AssistantAction {
        val said = history.lastOrNull { it.fromUser }?.text.orEmpty()
        val rule = RuleAppAssistant.reply(history, context)
        if (rule is AssistantAction.Emergency) return rule
        val favorite = runCatching { favoriteIn(said, favorites()) }.getOrNull() ?: return inner.reply(history, context)
        val place = favorite.place ?: return AssistantAction.Answer(
            "${withSubject(favorite.name)} 아직 등록되지 않았어요. 설정의 '자주 가는 곳'에서 등록하면 바로 안내해 드릴게요.",
        )
        val answer = inner.reply(history, context)
        // The server's recommendation (walk or transit, with its reason) when it gave one; otherwise straight there.
        return answer as? AssistantAction.GoPlace
            ?: AssistantAction.GoPlace("${withDirection(favorite.name)} 길을 찾을게요.", place.copy(name = favorite.name))
    }
}

/** Actions the phone does itself; the server assistant is not asked about these. */
private fun AssistantAction.staysOnPhone(): Boolean = this is AssistantAction.Emergency ||
    this is AssistantAction.ChangeSettings || this is AssistantAction.ShowBuses ||
    this is AssistantAction.OpenReport || this is AssistantAction.GoNearby

/** Server tools that save settings on the server, which the app does not read; the phone does these. */
private val SERVER_SETTING_TOOLS = setOf("update_my_info", "set_map_layers")

/**
 * The team server's assistant first; the phone's own assistant ([local]) when the server fails or the
 * request is one the phone handles ([staysOnPhone]). Keeps the server's conversationId and the
 * buttons it offered, so tapping one sends its value back as `choice`.
 */
class ServerAppAssistant(
    private val local: AppAssistant,
    private val guestId: suspend () -> String?,
    private val favorites: suspend () -> List<Pair<String, LatLon>>,
) : AppAssistant {
    private var conversationId: String? = null
    private var offered: Map<String, String> = emptyMap()

    override suspend fun reply(history: List<ChatLine>, context: AssistantContext): AssistantAction {
        // The phone's word rules first: they need no network and catch anything urgent.
        val rule = RuleAppAssistant.reply(history, context)
        if (rule.staysOnPhone()) return rule
        if (history.count { it.fromUser } <= 1) {
            conversationId = null
            offered = emptyMap()
        }
        val said = history.lastOrNull { it.fromUser }?.text.orEmpty()
        val body = serverAssistantRequest(
            guestId = runCatching { guestId() }.getOrNull(),
            text = said,
            mode = context.mode,
            where = runCatching { context.where() }.getOrNull(),
            now = java.time.LocalDateTime.now(),
            conversationId = conversationId,
            choice = offered[said],
            favorites = runCatching { favorites() }.getOrDefault(emptyList()),
        )
        val json = try {
            ServerApi.post("/api/assistant", body)
        } catch (e: ApiException) {
            return local.reply(history, context)
        } catch (e: IOException) {
            return local.reply(history, context)
        }
        val reply = parseServerAssistantReply(json) ?: return local.reply(history, context)
        // The server keeps its own profile, which the app never fills in: it would ask a new phone how the
        // walker gets around although the app knows the mode, and would save settings the app does not read.
        if (reply.setupQuestion || reply.tools.any { it in SERVER_SETTING_TOOLS }) return local.reply(history, context)
        conversationId = reply.conversationId
        offered = reply.choices.associate { it.second to it.first }
        return reply.action
    }
}

/** The server's reply turned into an app action, with what is needed to carry the conversation on. */
internal data class ServerAssistantReply(
    val action: AssistantAction,
    val conversationId: String?,
    /** (value, label) of each button offered. */
    val choices: List<Pair<String, String>>,
    val tools: List<String>,
    /** The server asking a phone it does not know yet how the walker gets around (its first-time setup). */
    val setupQuestion: Boolean = false,
)

/** Button values of the server's first-time setup question ("걸어서, 지팡이, 휠체어, 유모차"). */
private val SETUP_CHOICES = setOf("walk", "cane", "wheelchair", "stroller")

/** The body for `POST /api/assistant`. */
internal fun serverAssistantRequest(
    guestId: String?,
    text: String,
    mode: UserMode,
    where: LatLon?,
    now: java.time.LocalDateTime,
    conversationId: String?,
    choice: String?,
    favorites: List<Pair<String, LatLon>>,
): JSONObject = JSONObject()
    .put("guestId", guestId ?: JSONObject.NULL)
    .put("text", text)
    // Not read by the server yet (it asks instead); sent so it can use the app's mode once it does.
    .put("mode", mode.key)
    .put("lat", where?.lat ?: JSONObject.NULL)
    .put("lon", where?.lon ?: JSONObject.NULL)
    .put("now", now.withSecond(0).withNano(0).toString().take(16))
    .put("conversationId", conversationId ?: JSONObject.NULL)
    .put("choice", choice ?: JSONObject.NULL)
    .put("favorites", JSONArray(favorites.map { (name, p) -> JSONObject().put("name", name).put("lat", p.lat).put("lon", p.lon) }))

/**
 * Reads `POST /api/assistant`'s answer: a recommended trip or a found place becomes [AssistantAction.GoPlace],
 * a question with buttons [AssistantAction.Ask], anything else [AssistantAction.Answer]. Null without words to say.
 */
internal fun parseServerAssistantReply(json: JSONObject): ServerAssistantReply? {
    fun JSONObject.text(key: String): String? = optString(key).takeIf { !isNull(key) && it.isNotBlank() }
    var say = json.text("say") ?: return null
    val ask = json.text("ask")
    val choicesJson = json.optJSONArray("choices")
    val choices = (0 until (choicesJson?.length() ?: 0)).mapNotNull { i ->
        val c = choicesJson!!.optJSONObject(i) ?: return@mapNotNull null
        val label = c.text("label") ?: return@mapNotNull null
        (c.text("value") ?: label) to label
    }
    val toolsJson = json.optJSONArray("tools")
    val tools = (0 until (toolsJson?.length() ?: 0)).map { toolsJson!!.optString(it) }

    val destination = json.optJSONObject("pick")?.optJSONObject("request")?.optJSONObject("destination")
    val spot = json.optJSONObject("place")
    val place = when {
        destination != null && destination.has("lat") && destination.has("lon") -> Place(
            id = null, name = destination.text("name") ?: "목적지", address = "",
            lat = destination.getDouble("lat"), lon = destination.getDouble("lon"), distanceM = null,
        )
        spot != null && spot.has("lat") && spot.has("lon") -> Place(
            id = null, name = spot.text("name") ?: "찾은 곳", address = "",
            lat = spot.getDouble("lat"), lon = spot.getDouble("lon"),
            distanceM = if (spot.isNull("distanceM") || !spot.has("distanceM")) null else spot.optInt("distanceM"),
        )
        else -> null
    }
    if (ask != null && !say.contains(ask)) say = "$say\n$ask"
    val action = when {
        // A question waits for the walker, even when a place came with it.
        ask != null || choices.isNotEmpty() -> AssistantAction.Ask(say, choices.map { it.second })
        place != null -> AssistantAction.GoPlace(say, place, transit = json.optJSONObject("pick")?.optString("type") == "transit")
        else -> AssistantAction.Answer(say)
    }
    val setup = "get_my_info" in tools && place == null && choices.any { it.first in SETUP_CHOICES }
    return ServerAssistantReply(action, json.text("conversationId"), choices, tools, setup)
}

/** Gemini picks the action in a fixed JSON shape; anything unusable falls back to the rules. */
class GeminiAppAssistant(private val fallback: AppAssistant) : AppAssistant {
    override suspend fun reply(history: List<ChatLine>, context: AssistantContext): AssistantAction {
        val action = runCatching { ask(history, context) }.getOrNull() ?: return fallback.reply(history, context)
        // Gemini says "report" without the kind; fill it in from the words.
        return if (action is AssistantAction.OpenReport) reportAction(history.lastOrNull { it.fromUser }?.text.orEmpty()) else action
    }

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
