package com.deungbul.anshimgil.data

/*
 * 출발지·경유지를 말로 (11/12 폰 시험): "문화전당역에서 서석동3으로 이동 후에 한국예술종합교육원으로 갈 거야".
 * The server's assistant always starts from 지금 위치 and reads stops only with 들르다 (StopReader), so it took
 * 문화전당역 as the destination. The phone reads these itself:
 *  - a start: "○○에서 (출발해서) …"
 *  - places on the way: "○○(으)로 이동 후에", "○○ 거쳐(서)", "○○ 경유해서", "○○ 갔다가", "○○ 간 다음에"
 * "다이소 들렀다가 집 갈래" with no start stays with the server (its 들를 곳 묶기, which finds a 다이소 on the way).
 */

/** One place named in a trip: what was said, and the saved place when it is one of 자주 가는 곳. */
data class TripPart(val query: String, val place: Place? = null)

/** A trip read from what was said: [start] null means here. */
data class TripAsk(val start: TripPart?, val stops: List<TripPart>, val destination: TripPart)

/** "에서" with an optional 출발: where the trip starts. */
private val START = Regex("^\\s*(.+?)\\s*에서\\s*(?:출발\\s*(?:해서|하고|하면|해)?\\s*)?")

/** Words meaning the walker's own position, so no separate start. */
private val HERE_WORDS = setOf("여기", "지금 위치", "현재 위치", "현위치", "지금 있는 곳", "내 위치", "이곳")

/** Words that go via a place on the way (the server's 들르다 words are added when a start is given). */
private const val VIA_WORDS = "이동\\s*(?:을\\s*)?(?:한|하고|해서|하고서)?\\s*(?:후에?|뒤에?|다음에?)?" +
    "|거쳐서?|거치고|경유\\s*(?:해서|하고|한\\s*(?:후에?|뒤에?|다음에?))?" +
    "|갔다가|갔다\\s*가서|(?:^|(?<=\\s)|(?<=에)|(?<=로)|(?<=을)|(?<=를))간\\s*(?:후에?|뒤에?|다음에?)"
private const val STOP_VIA_WORDS = "|들렀다가?|들렀다\\s*가서|들러서|들러|들르고서?|들려서|들리고"

private fun viaSplitter(withStopWords: Boolean) =
    Regex("\\s*(?:$VIA_WORDS${if (withStopWords) STOP_VIA_WORDS else ""})\\s*")

/** Going words; the destination is what comes before the first of them (as in [placeToGo]). */
private val TRIP_GO_WORDS = listOf("가고", "가는", "갈래", "갈게", "갈거", "갈 거", "갈꺼", "가려", "가자", "가야", "데려다", "가요", "갈까", "가줘", "가 줘", "안내", "길")

/** A going word stuck to the place ("조선대로갈거야", "광주역가자"); the place is what comes before it. */
private val GO_GLUED = Regex("^(.+?)(?:갈래|갈게|갈거|갈꺼|가자|가고싶|가려|가야|데려다|가줘|안내|갈까|가요)")

/** Small words dropped around a place's name. */
private val TRIP_FILLERS = setOf("먼저", "잠깐", "잠시", "그다음", "그다음에", "그리고", "다시", "지금", "오늘", "나", "저", "우리", "좀")

/**
 * The trip in [said] when it names a start or a place to go via, e.g. start 문화전당역, via 서석동3, to
 * 한국예술종합교육원 in "문화전당역에서 서석동3으로 이동후에 한국예술종합교육원으로 갈거야"; null otherwise.
 */
fun tripIn(said: String): TripAsk? {
    val text = withStopWordsFixed(said.trim())
    val startMatch = START.find(text)
    val startName = startMatch?.groupValues?.get(1)?.let(::placeName)
    val hasStart = startName != null
    val rest = if (startMatch != null && hasStart) text.substring(startMatch.range.last + 1) else text
    // Without a start, 들르다 is the server's (its stop rule finds the shop on the way).
    if (!hasStart && mentionsStops(text)) return null
    val pieces = rest.split(viaSplitter(withStopWords = hasStart)).map { it.trim() }.filter { it.isNotEmpty() }
    if (pieces.isEmpty()) return null
    if (!hasStart && pieces.size < 2) return null
    val dest = destinationName(pieces.last(), needGoWord = pieces.size < 2) ?: return null
    val stops = pieces.dropLast(1).map { placeName(it) ?: return null }
    val start = startName?.takeUnless { it in HERE_WORDS }?.let { TripPart(it) }
    // "여기에서 남광주시장 가자" is just a destination: the usual rule reads it.
    if (start == null && stops.isEmpty()) return null
    return TripAsk(start, stops.map { TripPart(it) }, TripPart(dest))
}

/** The place named in a piece of a trip, without its particle and small words; null when nothing is left. */
private fun placeName(piece: String): String? {
    val words = piece.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it !in TRIP_FILLERS }
    if (words.isEmpty()) return null
    val name = (words.dropLast(1) + tripParticleOff(words.last())).joinToString(" ").trim()
    return name.takeIf { it.isNotEmpty() && it !in TRIP_FILLERS }
}

/** [stripParticle], also from a short word ("집에" → 집) and 을/를 ("서석동을 거쳐"). */
private fun tripParticleOff(w: String): String {
    if (w.endsWith("마을")) return w // 양림마을, not 을
    for (p in listOf("까지", "으로", "에", "을", "를")) if (w.endsWith(p) && w.length > p.length) return w.dropLast(p.length)
    return stripParticle(w)
}

/** The destination in the last piece: the words before the going word ("한국예술종합교육원으로 갈거야"). */
private fun destinationName(piece: String, needGoWord: Boolean): String? {
    val words = piece.trim().split(Regex("\\s+"))
    val at = (1 until words.size).firstOrNull { i -> TRIP_GO_WORDS.any { g -> words[i].startsWith(g.substringBefore(' ')) && (g.length > 1 || words[i].length <= 2) } }
        ?: words.indices.firstOrNull { i -> i > 0 && words[i] == "가" }
    if (at == null) {
        // Said without spaces, as speech recognition often gives it: "조선대로갈거야".
        // Only on the last word, and not a way of going ("걸어갈래", "뛰어가자").
        GO_GLUED.find(words.last())?.groupValues?.get(1)?.takeUnless { Regex("(어|아|여|서|러)$").containsMatchIn(it) }
            ?.let { return placeName((words.dropLast(1) + it).joinToString(" ")) }
        if (needGoWord) return null
    }
    return placeName(words.subList(0, at ?: words.size).joinToString(" "))
}

/** Saved places ([Favorite]) put on the parts named just like them ("집에서 …", "… 회사로 갈래"). */
fun TripAsk.withSaved(favorites: List<Favorite>): TripAsk {
    fun fill(p: TripPart) = p.place?.let { p } ?: favorites.firstOrNull { it.name == p.query && it.place != null }
        ?.let { p.copy(place = it.place!!.copy(name = it.name)) } ?: p
    return copy(start = start?.let(::fill), stops = stops.map(::fill), destination = fill(destination))
}

/** What AI 비서 says when the trip was found, e.g. "문화전당역에서 출발해 서석동3을 거쳐 …으로 가는 길을 찾았어요." */
fun tripFoundSay(start: String?, stops: List<String>, destination: String): String {
    val from = start?.let { "${it}에서 출발해 " }.orEmpty()
    val via = if (stops.isEmpty()) "" else "${withObject(stops.joinToString(", "))} 거쳐 "
    return "$from$via${withDirection(destination)} 가는 길을 찾았어요."
}

/** 을/를 after [word] by its last sound. */
internal fun withObject(word: String): String {
    val code = word.lastOrNull()?.code?.minus(0xAC00) ?: return word
    return if (code in 0..11171 && code % 28 != 0) "${word}을" else "${word}를"
}

/**
 * The place meant by [query] in a trip. The search near a point is by distance only ("조선대" gave 도야짬뽕
 * 광주조선대점, "광주극장" a theater at 문화전당, 11/12 phone tests); the search without a point is by how well the
 * name fits, and its first result was the place meant every time (조선대학교, 광주극장, and 전남광주통합특별시청 광주청사
 * for 광주광역시청, a name no result holds, 11/13). Car parks and toilets only when nothing else is there.
 * 1) near: named exactly so, or ending with it ("광주충장로우체국" for 충장로우체국)
 * 2) near: a branch or part whose first word is it (다이소 충장점, 양동시장역 광주1호선), the landmark of those
 * 3) [anywhere] (the search without a point): its first result
 * 4) near: the landmark among those holding it, else the nearest
 */
suspend fun pickTripPlace(query: String, nearAll: List<Place>, anywhereAll: suspend () -> List<Place>): Place? {
    fun List<Place>.notParts() = filter { p -> PARTS.none { it in p.name } }.ifEmpty { this }
    val near = nearAll.notParts()
    val q = query.replace(" ", "")
    fun name(p: Place) = p.name.replace(" ", "")
    near.firstOrNull { name(it) == q }?.let { return it }
    near.firstOrNull { name(it).endsWith(q) }?.let { return it }
    near.filter { it.name.trim().substringBefore(' ') == query.trim() }.takeIf { it.isNotEmpty() }?.let { return pickLandmark(it) }
    anywhereAll().notParts().firstOrNull()?.let { return it }
    return pickLandmark(near.filter { name(it).contains(q) }) ?: pickLandmark(near)
}
