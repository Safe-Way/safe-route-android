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
    suspend fun reply(history: List<ChatLine>): SetupReply

    companion object {
        fun create(): SetupAssistant =
            if (BuildConfig.GEMINI_API_KEY.isNotBlank()) GeminiSetupAssistant(RuleSetupAssistant) else RuleSetupAssistant
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
    override suspend fun reply(history: List<ChatLine>): SetupReply {
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
 * Gemini, asked for JSON in a fixed shape (structured output), so it can only answer with a question
 * or with the app's own setting values. Falls back to [fallback] on any failure.
 */
class GeminiSetupAssistant(private val fallback: SetupAssistant) : SetupAssistant {
    override suspend fun reply(history: List<ChatLine>): SetupReply =
        runCatching { ask(history) }.getOrNull() ?: fallback.reply(history)

    private suspend fun ask(history: List<ChatLine>): SetupReply? = withContext(Dispatchers.IO) {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${BuildConfig.GEMINI_MODEL}:generateContent")
        val body = geminiRequest(history).toString().toByteArray()
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
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            parseGeminiReply(JSONObject(text))
        } catch (e: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }
}

private val SETUP_INSTRUCTION = """
너는 '세잎지도' 걷기 길안내 앱의 처음 설정 도우미야. 사용자가 누가 어떻게 걷는지 한국어로 말하면 앱 설정을 골라.
모드: CHILD(어린이), SENIOR(어르신·다리가 불편하거나 천천히 걷는 사람), DISABLED(휠체어·보행기), GENERAL(그 밖의 사람).
누가 걷는지 모르면 한 번만 짧게 되물어(type=ask, 고를 답 2~4개). 두 번째 답부터는 꼭 설정을 골라(type=pick).
reason은 존댓말 한두 문장으로, 왜 그렇게 골랐는지 사용자에게 말하듯 써. 이름·전화번호를 묻지 마.
extraMapItems에는 모드에 더하면 좋은 것만: ${SETUP_MAP_ITEMS.keys.joinToString(", ")}.
""".trimIndent()

internal fun geminiRequest(history: List<ChatLine>): JSONObject {
    val contents = JSONArray()
    history.forEach { line ->
        contents.put(
            JSONObject()
                .put("role", if (line.fromUser) "user" else "model")
                .put("parts", JSONArray().put(JSONObject().put("text", line.text))),
        )
    }
    fun str(vararg values: String) = JSONObject().put("type", "STRING").apply { if (values.isNotEmpty()) put("enum", JSONArray(values.toList())) }
    val schema = JSONObject()
        .put("type", "OBJECT")
        .put(
            "properties",
            JSONObject()
                .put("type", str("ask", "pick"))
                .put("question", str())
                .put("choices", JSONObject().put("type", "ARRAY").put("items", str()))
                .put("mode", str(*UserMode.entries.map { it.name }.toTypedArray()))
                .put("textSize", str(*TextSize.entries.map { it.name }.toTypedArray()))
                .put("voiceGuide", JSONObject().put("type", "BOOLEAN"))
                .put("sosButton", JSONObject().put("type", "BOOLEAN"))
                .put("extraMapItems", JSONObject().put("type", "ARRAY").put("items", str(*SETUP_MAP_ITEMS.keys.toTypedArray())))
                .put("reason", str()),
        )
        .put("required", JSONArray(listOf("type")))
    return JSONObject()
        .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SETUP_INSTRUCTION))))
        .put("contents", contents)
        .put(
            "generationConfig",
            JSONObject().put("responseMimeType", "application/json").put("responseSchema", schema).put("temperature", 0.2),
        )
}

/** Gemini's answer as a reply, or null when it is not one the app can use. */
internal fun parseGeminiReply(response: JSONObject): SetupReply? {
    val text = response.optJSONArray("candidates")?.optJSONObject(0)
        ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
        ?.takeIf { it.isNotBlank() } ?: return null
    val j = runCatching { JSONObject(text) }.getOrNull() ?: return null
    return when (j.optString("type")) {
        "ask" -> {
            val question = j.optString("question").takeIf { it.isNotBlank() } ?: return null
            val choices = j.optJSONArray("choices")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            SetupReply.Ask(question, choices.filter { it.isNotBlank() }.take(4))
        }
        "pick" -> {
            val mode = runCatching { UserMode.valueOf(j.optString("mode")) }.getOrNull() ?: return null
            val size = runCatching { TextSize.valueOf(j.optString("textSize")) }.getOrDefault(TextSize.NORMAL)
            val extras = j.optJSONArray("extraMapItems")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
                .filter { it in SETUP_MAP_ITEMS }.toSet()
            SetupReply.Pick(
                SetupPick(mode, size, j.optBoolean("voiceGuide", true), j.optBoolean("sosButton", false), extras),
                j.optString("reason").ifBlank { "말씀하신 내용에 맞춰 설정했어요." },
            )
        }
        else -> null
    }
}
