package com.deungbul.anshimgil.data

/*
 * 뜸 들이는 말 (11/13, 채림): "어 그 머시기 뭐더라…" 하고 생각하는 동안 조용하면 그 부분만 받아써져 보내지고,
 * 나머지는 다시 눌러 말해야 했다. 받아쓴 말이 뜸 말뿐이거나 뜸 말로 끝나면 보내지 않고 바로 다시 들어 앞말과
 * 이어 붙인다. 뜸 말은 서버(safe-route Fillers.WORDS)와 같은 낱말에 채림 님이 적은 "머시기"를 더한 것.
 */

/** Words said while thinking: safe-route Fillers.WORDS, plus 머시기 (채림 11/13). */
val FILLER_WORDS = setOf(
    "어", "어어", "음", "음음", "으음", "흠", "아", "아아", "에", "에이", "저",
    "저기", "저기요", "그", "그게", "그러니까", "그니까", "있잖아", "있잖아요", "뭐더라", "어디더라", "뭐지", "어디지",
    "잠시만", "잠시만요", "잠깐", "잠깐만", "잠깐만요", "글쎄",
    "머시기", "뭐시기",
)

/** The words of [text] without the dots, marks and tildes at their ends ("어..", "잠시만~"), as the server reads them. */
private fun bareWords(text: String): List<String> =
    text.trim().split(Regex("\\s+"))
        .map { it.replace(Regex("[.。…!！?？~,]+$"), "").replace(Regex("^[.。…]+"), "") }
        .filter { it.isNotEmpty() }

/** Whether the walker is most likely still thinking: nothing but fillers, or ending with one ("남광주 그 뭐더라"). */
fun waitsForMore(text: String): Boolean {
    val words = bareWords(text)
    return words.isEmpty() || words.last() in FILLER_WORDS
}

/** Whether [text] says something besides fillers. */
fun saysSomething(text: String): Boolean = bareWords(text).any { it !in FILLER_WORDS }

/** At most this many times in a row the listening goes on after a pause on a filler. */
const val MAX_FILLER_WAITS = 3
