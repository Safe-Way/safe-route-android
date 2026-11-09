package com.deungbul.anshimgil.data

/*
 * "문화전당 가자" (사용성 점검표 5번, 11/9): a place named with "가자 / 가 줘 / 가고 싶어" goes straight to the
 * route, one word said and nothing more to tap. The server assistant asked which of three places was meant; the
 * phone picks the landmark among the search results itself (the walker can still change the destination).
 */

private val GO_ENDINGS = listOf(
    "가자", "가 자", "가줘", "가 줘", "가주세요", "가 주세요", "가고 싶어", "가고싶어", "가고 싶어요", "가고싶어요",
    "갈래", "갈래요", "가려고", "데려다 줘", "데려다줘", "안내해 줘", "안내해줘", "안내해 주세요", "길 알려 줘", "길 알려줘",
)

/** Particles and words left after the place's name ("문화전당으로", "문화전당까지"). */
private val TRAILING = listOf("으로", "로", "까지", "에", "에게", "쪽으로", "쪽")

/** The place named in [said] when it asks to go there, e.g. "문화전당" in "문화전당 가자"; null otherwise. */
fun placeToGo(said: String): String? {
    // Stopping somewhere on the way ("다이소 들렀다가 집에 갈래") is for AI 비서 on the server, which plans the stops.
    if (mentionsStops(said)) return null
    var s = said.trim().trimEnd('.', '!', '?', '~').trim()
    val ending = GO_ENDINGS.sortedByDescending { it.length }.firstOrNull { s.endsWith(it) } ?: return null
    s = s.removeSuffix(ending).trim()
    for (w in listOf("길", "빨리", "지금", "얼른", "어서")) s = s.removeSuffix(w).trim()
    TRAILING.sortedByDescending { it.length }.firstOrNull { s.endsWith(it) && s.length > it.length && isParticle(s, it) }
        ?.let { s = s.removeSuffix(it).trim() }
    s = s.removePrefix("나 ").removePrefix("우리 ").trim()
    // Not a place: "집에 가자" is a saved place (asked before this), "같이 가자" and the like are not names.
    if (s.length < 2 || s in setOf("같이", "어디", "어디로", "거기", "저기", "여기", "집", "다른 데", "다른데")) return null
    return s
}

/** "로" is a particle only after a vowel or ㄹ ("학교로", "서울로"); "충장로", "금남로" are names. */
private fun isParticle(s: String, ending: String): Boolean {
    if (ending != "로") return true
    val before = s.dropLast(1).lastOrNull() ?: return false
    val code = before.code - 0xAC00
    return code in 0..11171 && (code % 28 == 0 || code % 28 == 8)
}

/**
 * The place meant among search results (nearest first): a landmark has several entries sharing its name
 * ("국립아시아문화전당 GATE3", "국립아시아문화전당 부설주차장", …), so the name most results begin with wins, and
 * its nearest entry is taken; with no such name, the nearest result.
 */
fun pickLandmark(results: List<Place>): Place? {
    if (results.isEmpty()) return null
    val base = { p: Place -> p.name.trim().substringBefore(' ') }
    val counts = results.groupingBy(base).eachCount()
    val top = counts.maxByOrNull { it.value } ?: return results.first()
    if (top.value < 2) return results.first()
    return results.first { base(it) == top.key }
}

/**
 * Words for stopping somewhere on the way: 들르다 and how speech recognition often hears it (11/9 on the phone,
 * "다이소 들렀다" came out as "다이소 드렸다").
 */
private val STOP_WORDS = Regex("들르|들렀|들러|들리고|들려서|드렸다|드르고|드르고서|들렸다")

/** Whether [said] asks to stop somewhere on the way (들를 곳). */
fun mentionsStops(said: String): Boolean = STOP_WORDS.containsMatchIn(said)

/** [said] with the usual mishearings of 들르다 put right, so the server reads the stops. */
fun withStopWordsFixed(said: String): String =
    said.replace("드렸다가", "들렀다가").replace("드렸다", "들렀다").replace("드르고", "들르고").replace("들렸다", "들렀다")
