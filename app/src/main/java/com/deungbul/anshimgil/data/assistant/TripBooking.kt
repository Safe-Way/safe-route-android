package com.deungbul.anshimgil.data.assistant

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import com.deungbul.anshimgil.data.route.night

/*
 * 길 안내 예약 (11/10): "10분 뒤에 문화전당 가자", "오늘 3시에 시청 길 안내 예약해 줘", "11월 12일 오전 9시 병원 가는 길".
 * AI 비서 asks once ("…에 문화전당 길 안내를 시작할까요?"), and at that time the guidance opens by itself.
 * The team's rule (no_tool_policy ③, RuleBrain NO_TOOL "예약") is about booking with others (병원 예약, 택시) — that
 * still goes to the server and is still answered "못 해요". Only a time *and* a way to go is read here, on the phone.
 */

/** A guidance booked for later: where, when, and by transit or not. */
data class TripBooking(val id: Long, val at: LocalDateTime, val name: String, val lat: Double, val lon: Double, val transit: Boolean = false)

/** What was said about a booked guidance. */
sealed interface BookingAsk {
    /** Go to [query] (null: the place just talked about) at [at]. */
    data class Book(val at: LocalDateTime, val query: String?) : BookingAsk
    /** A time was said but it is already past, or too far off. */
    data class BadTime(val say: String) : BookingAsk
    data object Cancel : BookingAsk
    data object List : BookingAsk
}

/** How far ahead a guidance can be booked. */
const val BOOK_MAX_DAYS = 31L

private val HOUR_WORDS = listOf("한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열", "열한", "열두")
private val NUM = "(\\d{1,2}|열한|열두|한|두|세|네|다섯|여섯|일곱|여덟|아홉|열)"
private fun num(s: String): Int = s.toIntOrNull() ?: (HOUR_WORDS.indexOf(s) + 1)

private val IN_HOURS = Regex("$NUM\\s*시간\\s*(반)?\\s*(?:(\\d{1,2})\\s*분)?\\s*(?:뒤|후|있다가|이따)")
private val IN_MINUTES = Regex("(\\d{1,3})\\s*분\\s*(?:뒤|후|있다가|이따)")
private val HALF_HOUR = Regex("(?<![\\d시간])반\\s*시간\\s*(?:뒤|후|있다가)")
private val CLOCK = Regex("$NUM\\s*시(?!간)\\s*(?:(\\d{1,2})\\s*분|(반))?")
private val CLOCK_COLON = Regex("(\\d{1,2}):(\\d{2})")
private val MONTH_DAY_AT = Regex("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일")
private val DAY_AT = Regex("(?<![\\d월])(\\d{1,2})\\s*일(?!요)")
private val WEEKDAY = Regex("(다음\\s*주\\s*)?([월화수목금토일])요일")

/** Words that say the time is a time to go: 가자, 길 안내, 예약, 알려 줘, 출발. */
private val BOOK_WORDS = listOf("예약", "안내", "알려", "출발", "데려", "길")
private val CANCEL_WORDS = listOf("취소", "지워", "지우", "없애", "안 갈", "안갈", "그만")

private fun goWord(said: String): Boolean =
    placeToGo(said) != null || BOOK_WORDS.any { said.replace(" ", "").contains(it) }

/** The time said in [said], from [now]; null when no time is in it. Past times come back as they are. */
internal fun timeIn(said: String, now: LocalDateTime): LocalDateTime? {
    val s = said
    HALF_HOUR.find(s)?.let { return now.plusMinutes(30).withSecond(0).withNano(0) }
    IN_HOURS.find(s)?.let { m ->
        val min = num(m.groupValues[1]) * 60L + (if (m.groupValues[2].isNotEmpty()) 30 else 0) + (m.groupValues[3].toLongOrNull() ?: 0)
        return now.plusMinutes(min).withSecond(0).withNano(0)
    }
    IN_MINUTES.find(s)?.let { return now.plusMinutes(it.groupValues[1].toLong()).withSecond(0).withNano(0) }

    val t = s.replace(" ", "")
    val today = now.toLocalDate()
    val day = when {
        MONTH_DAY_AT.find(s) != null -> MONTH_DAY_AT.find(s)!!.let { m ->
            runCatching { today.withMonth(m.groupValues[1].toInt()).withDayOfMonth(m.groupValues[2].toInt()) }.getOrNull()
                ?.let { if (it.isBefore(today)) it.plusYears(1) else it }
        }
        t.contains("모레") -> today.plusDays(2)
        t.contains("내일") -> today.plusDays(1)
        WEEKDAY.find(s) != null -> WEEKDAY.find(s)!!.let { m ->
            val dow = DayOfWeek.of("월화수목금토일".indexOf(m.groupValues[2]) + 1)
            val d = today.with(TemporalAdjusters.next(dow))
            if (m.groupValues[1].isNotEmpty() && d.isBefore(today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)))) d.plusWeeks(1) else d
        }
        DAY_AT.find(s) != null -> DAY_AT.find(s)!!.let { m ->
            val n = m.groupValues[1].toInt()
            runCatching { today.withDayOfMonth(n) }.getOrNull()?.let { if (it.isBefore(today)) it.plusMonths(1) else it }
        }
        else -> null
    }

    var hour: Int
    var minute: Int
    val colon = CLOCK_COLON.find(s)
    val clock = CLOCK.find(s)
    when {
        colon != null -> { hour = colon.groupValues[1].toInt(); minute = colon.groupValues[2].toInt() }
        clock != null -> {
            hour = num(clock.groupValues[1])
            minute = clock.groupValues[2].toIntOrNull() ?: if (clock.groupValues[3].isNotEmpty()) 30 else 0
        }
        else -> return null // a day alone ("내일 문화전당 가자") is not a booking: it needs a time
    }
    if (hour !in 0..24 || minute !in 0..59) return null
    if (hour == 24) hour = 0
    val am = listOf("오전", "아침", "새벽").any { t.contains(it) }
    val pm = listOf("오후", "저녁", "밤", "점심", "낮").any { t.contains(it) }
    // Said without 오전/오후: 1–6시 is the afternoon (nobody sets out at 3 in the night), 7–11시 the morning.
    val noon = when {
        hour >= 12 -> hour
        pm -> if (t.contains("밤") && hour == 12) 0 else hour + 12
        am -> hour
        hour in 1..6 -> hour + 12
        else -> hour
    }
    if (day != null) return day.atTime(noon % 24, minute)
    val at = today.atTime(noon % 24, minute)
    // "오늘 9시" said at 10 in the morning: 9 in the evening, when 오전 was not said.
    if (!at.isAfter(now) && !am && !pm && noon < 12 && at.plusHours(12).isAfter(now)) return at.plusHours(12)
    if (!at.isAfter(now) && !t.contains("오늘")) return at.plusDays(1)
    return at
}

/**
 * What [said] asks of a booked guidance, or null when it is not about one (then AI 비서 goes on as before).
 * A booking needs both a time and a way to go: "3시에 문 닫아?" is not one.
 */
fun bookingAsk(said: String, now: LocalDateTime): BookingAsk? {
    val t = said.replace(" ", "")
    if (t.contains("예약")) {
        if (CANCEL_WORDS.any { t.contains(it.replace(" ", "")) }) return BookingAsk.Cancel
        if (listOf("뭐", "무슨", "확인", "있어", "있나", "보여", "알려줘").any { t.contains(it) } && timeIn(said, now) == null) return BookingAsk.List
        // 병원 예약, 식당 예약: booking with others stays the server's (못 해요).
        if (listOf("병원", "식당", "택시", "진료", "미용실", "기차", "버스표", "표").any { t.contains(it) } && !t.contains("길")) return null
    }
    val at = timeIn(said, now) ?: return null
    if (!goWord(said)) return null
    if (!at.isAfter(now)) return BookingAsk.BadTime("${whenText(at, now)}는 벌써 지났어요. 다른 시간을 말씀해 주세요.")
    if (at.isAfter(now.plusDays(BOOK_MAX_DAYS))) return BookingAsk.BadTime("길 안내는 한 달 안으로만 예약할 수 있어요.")
    return BookingAsk.Book(at, bookingPlace(said))
}

/** The place in a booking ("문화전당" in "10분 뒤에 문화전당 가자"); null for "거기"/"그곳" or none. */
internal fun bookingPlace(said: String): String? {
    var rest = said
    for (r in listOf(HALF_HOUR, IN_HOURS, IN_MINUTES, MONTH_DAY_AT, WEEKDAY, CLOCK_COLON, CLOCK, DAY_AT)) rest = r.replace(rest, " ")
    val drop = setOf(
        "오늘", "내일", "모레", "오전", "오후", "아침", "저녁", "밤", "낮", "점심", "새벽", "에", "쯤", "정도", "되면", "이", "가",
        "예약", "예약해", "예약해줘", "예약해", "줘", "주세요", "해줘", "해", "길안내", "안내", "안내해줘", "안내해", "시작", "시작해줘",
        "알려줘", "알려", "출발", "출발할게", "할래", "하자", "해주라", "부탁해", "가는", "길", "길을", "데려다", "데려가", "데려다줘", "가자", "갈래", "가요",
    )
    val words = rest.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && stripParticle(it).removeSuffix("에") !in drop && it !in drop }
    val joined = words.joinToString(" ")
    val name = placeToGo(joined) ?: words.map { stripParticle(it) }.filter { it !in drop && it.length > 0 }.joinToString(" ").ifBlank { null }
    return name?.takeUnless { it in setOf("거기", "그곳", "그기", "저기", "그데", "그리") || it.startsWith("거기") || it.startsWith("그곳") }
}

/** "오늘 오후 3시", "내일 오전 9시 30분", "11월 12일 오후 2시 반" — how AI 비서 says a booked time. */
fun whenText(at: LocalDateTime, now: LocalDateTime): String {
    val d = at.toLocalDate()
    val today = now.toLocalDate()
    val day = when (d) {
        today -> "오늘"
        today.plusDays(1) -> "내일"
        today.plusDays(2) -> "모레"
        else -> "${d.monthValue}월 ${d.dayOfMonth}일"
    }
    val h = at.hour
    val part = when {
        h < 12 -> "오전"
        else -> "오후"
    }
    val h12 = if (h % 12 == 0) 12 else h % 12
    val min = when (at.minute) { 0 -> ""; 30 -> " 반"; else -> " ${at.minute}분" }
    return "$day $part ${h12}시$min"
}

/** The question before booking: "오늘 오후 3시에 국립아시아문화전당 길 안내를 시작할까요?" (10분 뒤: "10분 뒤(오후 3시 10분)에"). */
fun bookingQuestion(at: LocalDateTime, name: String, now: LocalDateTime): String {
    val mins = (java.time.Duration.between(now, at).seconds + 30) / 60
    val time = if (mins in 1..119 && at.toLocalDate() == now.toLocalDate()) "${if (mins >= 60) "${mins / 60}시간 ${if (mins % 60 > 0) "${mins % 60}분 " else ""}" else "${mins}분 "}뒤(${whenText(at, now).removePrefix("오늘 ")})"
    else whenText(at, now)
    return "${time}에 $name 길 안내를 시작할까요?"
}

/** Said once it is booked. */
fun bookedText(at: LocalDateTime, name: String, now: LocalDateTime): String =
    "${whenText(at, now)}에 $name 길 안내를 예약했어요. 그때 알림이 오고, 세잎지도를 보고 계시면 바로 안내를 시작해요. " +
        "취소하려면 '예약 취소'라고 말씀해 주세요."

/** The booked guidances said as a list. */
fun bookingsText(list: kotlin.collections.List<TripBooking>, now: LocalDateTime): String = when (list.size) {
    0 -> "예약된 길 안내가 없어요."
    1 -> "${whenText(list[0].at, now)}에 ${list[0].name} 길 안내가 예약되어 있어요."
    else -> "예약된 길 안내가 ${list.size}개 있어요. " + list.sortedBy { it.at }.joinToString(" ") { "${whenText(it.at, now)} ${it.name}." }
}

/** Said after "예약 취소": what was cancelled. */
fun cancelledText(gone: kotlin.collections.List<TripBooking>, now: LocalDateTime): String = when (gone.size) {
    0 -> "예약된 길 안내가 없어요."
    1 -> "${whenText(gone[0].at, now)} ${gone[0].name} 길 안내 예약을 취소했어요."
    else -> "길 안내 예약 ${gone.size}개를 모두 취소했어요."
}

/** The two answers to [bookingQuestion]. */
const val BOOK_YES = "예약할게요"
const val BOOK_NO = "괜찮아요"
