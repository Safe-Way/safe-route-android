package com.deungbul.anshimgil.data

/*
 * "문화전당 가자": the team server's rule. The server reads such a request with Gemini first and only when that fails
 * with its words (safe-route AssistantService.think ⑥, RuleBrain.destination, RuleReader.destination). So the phone
 * sends it to the server, and [placeToGo] is that same word rule, for when the server cannot be reached.
 * The app's own rule of 11/9 (its "가자" endings, the words that are not a name, handled on the phone first) was
 * dropped for it.
 */

/** The server's words for wanting to go (RuleReader.GO_WORDS); the words before the first of them name the place. */
private val GO_WORDS = listOf("가고", "가는", "갈래", "갈게", "가려", "가자", "가야", "데려다", "데려가", "가요", "갈까", "가", "갈")

/** Way-of-walking words are not part of a place (RuleReader.MODE_WORDS). */
private val MODE_WORD_LIST = SERVER_MODE_WORDS.flatMap { it.second }

/** Words dropped from a place's name (RuleReader.isFiller). */
private val FILLERS = setOf("나", "저", "우리", "지금", "오늘", "같이", "좀", "빨리", "길", "알려줘", "알려", "주세요", "싶어", "싶어요", "쪽", "쪽으로", "쪽에", "방향", "방면")

/** A first word naming who goes along ("엄마랑", "친구하고"), RuleReader.WITH + isPersonish. */
private val WITH = Regex("^.*(랑|이랑|하고|와|과|께서|가|이|는|은)$")

/** The place named in [said] when it asks to go there, e.g. "문화전당" in "문화전당 가자" (RuleBrain.destination); null otherwise. */
fun placeToGo(said: String): String? {
    // Stopping somewhere on the way is read by the server's stop rule first (AssistantService ⑤).
    if (mentionsStops(said)) return null
    val f = said.replace(" ", "")
    if (f.startsWith("집에") || f.startsWith("집으로") || f.contains("집에갈") || f.contains("집에가")) return "집"
    val words = said.trim().split(Regex("\\s+"))
    var raw: String? = null
    for (i in 1 until words.size) {
        val w = words[i]
        if (GO_WORDS.any { g -> w.startsWith(g) && (g.length > 1 || w.length <= 2) }) {
            raw = words.subList(0, i).joinToString(" ")
            break
        }
    }
    val kept = mutableListOf<String>()
    for (word in (raw ?: return null).trim().split(Regex("\\s+"))) {
        if (word.isEmpty() || MODE_WORD_LIST.any { word.startsWith(it) } || word in FILLERS || stripParticle(word) in FILLERS) continue
        if (kept.isEmpty() && WITH.matches(word) && word.length > 1 && (word.endsWith("랑") || word.endsWith("하고"))) continue
        kept += stripParticle(word)
    }
    return kept.joinToString(" ").trim().ifEmpty { null }
}

/** "까지", "으로", "에" and the particle "로" taken off the end (RuleReader.stripParticle; "충장로" stays). */
internal fun stripParticle(w: String): String {
    for (p in listOf("까지", "으로", "에")) if (w.endsWith(p) && w.length > p.length + 1) return w.dropLast(p.length)
    if (w.endsWith("로") && w.length > 2) {
        val code = w[w.length - 2].code - 0xAC00
        if (code in 0..11171 && (code % 28 == 0 || code % 28 == 8)) return w.dropLast(1)
    }
    return w
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
    val group = results.filter { base(it) == top.key }
    // The place itself over its parts (11/12: "남광주시장 가자" took 남광주시장 공영주차장, then 공중화장실): the entry
    // named just that, then the first that is not a car park or a toilet.
    return group.firstOrNull { it.name.trim() == top.key }
        ?: group.firstOrNull { PARTS.none { w -> w in it.name } }
        ?: group.first()
}

/** Entries of a landmark that are its car parks or toilets, not the place to walk to. */
internal val PARTS = listOf("주차장", "주차", "화장실")

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
