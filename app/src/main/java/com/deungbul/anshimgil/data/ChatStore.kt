package com.deungbul.anshimgil.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/*
 * AI 비서's conversation kept on the phone (11/12: "채팅 내역이 나갔어도 초기화가 안 됐으면, 원할 때 초기화"):
 * it stays after leaving AI 비서 and after the app is closed, until the walker clears it ([대화 지우기] or
 * "대화 지워 줘"). Only on this phone; it is not sent anywhere.
 */

/** At most this many lines are kept (the oldest go first). */
const val CHAT_KEEP_LINES = 200

object ChatStore {
    private const val PREFS = "assistant_chat"
    private const val KEY = "lines"

    fun load(context: Context): List<ChatLine> =
        runCatching { chatFromJson(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)) }
            .getOrDefault(emptyList())

    fun save(context: Context, lines: List<ChatLine>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, chatToJson(lines)).apply()
    }
}

/** The last [CHAT_KEEP_LINES] lines as [{u, t}]. */
fun chatToJson(lines: List<ChatLine>): String =
    JSONArray().apply { lines.takeLast(CHAT_KEEP_LINES).forEach { put(JSONObject().put("u", it.fromUser).put("t", it.text)) } }.toString()

fun chatFromJson(json: String?): List<ChatLine> {
    if (json.isNullOrBlank()) return emptyList()
    val a = JSONArray(json)
    return (0 until a.length()).mapNotNull { i ->
        val o = a.optJSONObject(i) ?: return@mapNotNull null
        o.optString("t").takeIf { it.isNotBlank() }?.let { ChatLine(o.optBoolean("u"), it) }
    }
}

private val CHAT_WORDS = listOf("대화", "채팅", "기록", "내역", "말한거", "얘기")
private val CLEAR_WORDS = listOf("지워", "지우", "삭제", "초기화", "비워", "없애")

/** Whether [said] asks to clear the conversation ("대화 지워 줘", "채팅 기록 초기화", "처음부터 다시 하자"). */
fun asksToClearChat(said: String): Boolean {
    val s = said.replace(" ", "")
    if (s.startsWith("처음부터다시")) return true
    return CHAT_WORDS.any { it in s } && CLEAR_WORDS.any { it in s }
}

/** Asked before clearing by voice, as the [대화 지우기] button does (11/13: "대화 삭제" cleared at once). */
const val CLEAR_QUESTION = "대화를 모두 지울까요? 지우면 되돌릴 수 없어요."
const val CLEAR_YES = "지우기"
const val CLEAR_NO = "취소"

/** The answer to [CLEAR_QUESTION]: true to clear, false not to, null for something else. */
fun clearAnswer(said: String): Boolean? {
    val s = said.replace(" ", "")
    if (s == CLEAR_NO || listOf("취소", "아니", "안지", "괜찮", "말고", "그만", "싫어", "놔둬", "두세요", "둬").any { it in s }) return false
    if (s == CLEAR_YES || (s.length <= 12 && listOf("지우", "지워", "삭제", "네", "응", "예", "그래", "좋아").any { it in s })) return true
    return null
}
