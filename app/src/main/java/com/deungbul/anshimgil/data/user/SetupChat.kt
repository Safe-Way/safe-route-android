package com.deungbul.anshimgil.data.user

import org.json.JSONArray
import org.json.JSONObject
import com.deungbul.anshimgil.data.assistant.summary
import com.deungbul.anshimgil.data.place.CHILD_ZONE_KEY
import com.deungbul.anshimgil.data.place.FacilityKind
import com.deungbul.anshimgil.data.route.night
import com.deungbul.anshimgil.data.server.ServerApi

/*
 * 말로 맞추기: the walker (or a family member) says in their own words who walks and how, e.g.
 * "우리 엄마가 70대인데 다리가 좀 불편해요", and the app picks the settings. Gemini (free tier) does the
 * understanding when a key is set; otherwise, or when it fails, the app's own word rules do.
 * Only the typed or spoken words are sent: no name, phone number or position.
 */

/** Settings picked from the conversation; only the app's own choices can appear here. */
data class SetupPick(
    val mode: UserMode,
    val textSize: TextSize,
    val voiceGuide: Boolean,
    val sosButton: Boolean,
    /** Map items added to the mode's own (FacilityKind names or [CHILD_ZONE_KEY]). */
    val extraMapItems: Set<String>,
)

/** One line of the conversation. */
data class ChatLine(val fromUser: Boolean, val text: String)

sealed interface SetupReply {
    /** Ask once more, with a few ready answers to tap. */
    data class Ask(val question: String, val choices: List<String>) : SetupReply
    /** Done: the settings, and a sentence on why. */
    data class Pick(val pick: SetupPick, val reason: String) : SetupReply
}

interface SetupAssistant {
    /** [current] is what was picked so far, so a follow-up ("다른 것도 추가돼?") changes it instead of starting over. */
    suspend fun reply(history: List<ChatLine>, current: SetupPick? = null): SetupReply

    companion object {
        fun create(): SetupAssistant =
            if (ServerApi.isConfigured) ServerSetupAssistant(RuleSetupAssistant) else RuleSetupAssistant
    }
}

/** Map items the assistant may add, with the words it knows them by. */
internal val SETUP_MAP_ITEMS = mapOf(
    "HEAT_SHELTER" to "무더위쉼터",
    "COLD_SHELTER" to "한파쉼터",
    "TOILET" to "공중화장실",
    "ACCESSIBLE_TOILET" to "장애인 화장실",
    "STAIRS" to "계단",
    "CURB_RAMP" to "턱 낮춘 횡단보도",
    "SUBWAY" to "지하철역",
    "EMERGENCY_BELL" to "비상벨",
    CHILD_ZONE_KEY to "어린이 보호구역",
)

/** The settings in plain words, e.g. for the "이렇게 맞췄어요" summary. */
fun SetupPick.summaryLines(): List<String> = buildList {
    add("${modeLabel(mode)} 모드")
    add("글자 ${textSize.label}")
    add(if (voiceGuide) "음성 안내 켬" else "음성 안내 끔")
    if (sosButton) add("SOS 버튼 켬")
    extraMapItems.mapNotNull { SETUP_MAP_ITEMS[it] }.takeIf { it.isNotEmpty() }?.let { add("지도에 " + it.joinToString("·") + " 더 보기") }
}

private fun modeLabel(mode: UserMode) = when (mode) {
    UserMode.CHILD -> "어린이"
    UserMode.SENIOR -> "어르신"
    UserMode.DISABLED -> "장애인"
    UserMode.GENERAL -> "일반인"
}

/**
 * Word rules: good enough to work without a key or connection, and the fallback when Gemini fails.
 * Asks once if it cannot tell who walks; otherwise picks.
 */
object RuleSetupAssistant : SetupAssistant {
    override suspend fun reply(history: List<ChatLine>, current: SetupPick?): SetupReply {
        val said = history.filter { it.fromUser }.joinToString(" ") { it.text }
        val mode = modeFrom(said)
            ?: return if (history.count { it.fromUser } >= 2) {
                pick(UserMode.GENERAL, said, "누가 걷는지 확실하지 않아서 일반인 모드로 맞췄어요.")
            } else {
                SetupReply.Ask("누가 걸어가시나요?", listOf("제가 걸어요", "아이가 걸어요", "부모님이 걸어요", "휠체어를 타요"))
            }
        return pick(mode, said, null)
    }

    private fun pick(mode: UserMode, said: String, reason: String?): SetupReply.Pick {
        val hardToSee = said.has("눈이", "잘 안 보", "글씨", "노안", "시력")
        val night = said.has("밤", "늦게", "야간", "퇴근", "학원")
        val legs = said.has("다리", "무릎", "지팡이", "천천히", "숨이", "힘들")
        val textSize = when {
            hardToSee || mode == UserMode.SENIOR || mode == UserMode.DISABLED -> TextSize.LARGE
            else -> TextSize.NORMAL
        }
        val extras = buildSet {
            if (legs && mode != UserMode.DISABLED) { add("TOILET"); add("STAIRS") }
            if (mode == UserMode.DISABLED) add("SUBWAY")
            if (night) add("EMERGENCY_BELL")
        }
        val why = reason ?: buildList {
            add("${modeLabel(mode)} 모드가 맞아 보여요.")
            if (legs) add("다리가 불편하시다니 계단은 피하고 쉴 곳을 함께 보여 드릴게요.")
            if (night) add("밤에 다니신다니 비상벨을 지도에 더하고 SOS 버튼을 켰어요.")
            if (textSize == TextSize.LARGE) add("글자는 크게 했어요.")
        }.joinToString(" ")
        return SetupReply.Pick(
            SetupPick(
                mode = mode,
                textSize = textSize,
                voiceGuide = true,
                sosButton = night || mode == UserMode.CHILD || mode == UserMode.SENIOR,
                extraMapItems = extras,
            ),
            why,
        )
    }

    internal fun modeFrom(said: String): UserMode? = when {
        said.has("휠체어", "전동", "보행기", "장애") -> UserMode.DISABLED
        said.has("아이", "아들", "딸", "초등", "어린이", "유치원", "학교 가") -> UserMode.CHILD
        said.has("엄마", "아빠", "어머니", "아버지", "할머니", "할아버지", "부모", "어르신", "노인", "60대", "70대", "80대", "90대") -> UserMode.SENIOR
        said.has("제가", "저는", "내가", "혼자", "출퇴근", "직장") -> UserMode.GENERAL
        else -> null
    }

    private fun String.has(vararg words: String) = words.any { contains(it) }
}

/**
 * 말로 맞추기 through the server's window `POST /api/ai/setup-chat` (진행표 12, server #119): the server holds the
 * Gemini key, the instruction and the answer's shape, so the app carries no key. Only the words and the settings go;
 * no name, number, place or guest number. On 503 AI_UNAVAILABLE or any failure the app's own rules answer.
 */
class ServerSetupAssistant(private val fallback: SetupAssistant) : SetupAssistant {
    override suspend fun reply(history: List<ChatLine>, current: SetupPick?): SetupReply =
        runCatching { parseSetupJson(ServerApi.post("/api/ai/setup-chat", appChatRequest(history, SETUP_CHAT_LINES, current?.let { settingsJson(it) }))) }
            .getOrNull() ?: fallback.reply(history, current)
}

/** The server takes this many lines of the talk at most, each up to [APP_CHAT_LINE_CHARS]. */
private const val SETUP_CHAT_LINES = 12
internal const val APP_CHAT_LINE_CHARS = 300

/** `{history: [{fromUser, text}], settings}` for the server's AI windows (/api/ai/setup-chat, /api/ai/map-chat). */
internal fun appChatRequest(history: List<ChatLine>, lines: Int, settings: JSONObject?): JSONObject = JSONObject()
    .put("history", JSONArray().apply {
        history.takeLast(lines).forEach { put(JSONObject().put("fromUser", it.fromUser).put("text", it.text.take(APP_CHAT_LINE_CHARS))) }
    })
    .put("settings", settings ?: JSONObject.NULL)

/** The app's settings in the app's own names, as the server's windows take them. */
internal fun settingsJson(mode: UserMode, textSize: TextSize, voiceGuide: Boolean, sosButton: Boolean, extraMapItems: Set<String>): JSONObject =
    JSONObject().put("mode", mode.name).put("textSize", textSize.name).put("voiceGuide", voiceGuide)
        .put("sosButton", sosButton).put("extraMapItems", JSONArray(extraMapItems.toList()))

private fun settingsJson(p: SetupPick) = settingsJson(p.mode, p.textSize, p.voiceGuide, p.sosButton, p.extraMapItems)

/** The server's answer (the shape Gemini used to give the app) as a reply, or null when the app cannot use it. */
internal fun parseSetupJson(j: JSONObject): SetupReply? {
    return when (j.optString("type")) {
        "ask" -> {
            val question = j.optString("question").takeIf { looksLikeKorean(it) } ?: return null
            val choices = j.optJSONArray("choices")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            SetupReply.Ask(question, choices.filter { it.isNotBlank() && looksLikeKorean(it) }.take(4))
        }
        "pick" -> {
            val mode = runCatching { UserMode.valueOf(j.optString("mode")) }.getOrNull() ?: return null
            val size = runCatching { TextSize.valueOf(j.optString("textSize")) }.getOrDefault(TextSize.NORMAL)
            val extras = j.optJSONArray("extraMapItems")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
                .filter { it in SETUP_MAP_ITEMS }.toSet()
            SetupReply.Pick(
                SetupPick(mode, size, j.optBoolean("voiceGuide", true), j.optBoolean("sosButton", false), extras),
                j.optString("reason").takeIf { looksLikeKorean(it) } ?: "말씀하신 내용에 맞춰 설정했어요.",
            )
        }
        else -> null
    }
}

/**
 * Whether a model's sentence is plain Korean the app can show: no Chinese characters or other scripts
 * slipped in, not one word repeated over and over (small models sometimes go off the rails like that).
 */
internal fun looksLikeKorean(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty() || t.length > 200) return false
    val letters = t.filter { it.isLetter() }
    if (letters.isEmpty()) return false
    val hangul = letters.count { it in '\uAC00'..'\uD7A3' || it in '\u3131'..'\u318E' }
    if (hangul * 10 < letters.length * 7) return false
    val words = t.split(Regex("\\s+")).filter { it.isNotBlank() }
    return words.size < 4 || words.groupingBy { it }.eachCount().values.max() * 2 <= words.size
}
