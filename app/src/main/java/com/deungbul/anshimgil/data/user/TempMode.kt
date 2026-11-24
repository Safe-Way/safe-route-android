package com.deungbul.anshimgil.data.user

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/*
 * 몸 상태가 잠깐 바뀜 ("다리를 다쳐서 휠체어 타고 다녀"): the team server's rule (safe-route RuleBrain.tempBody,
 * tempUntil, the 다 나음 and 오늘만 steps, MeService.TEMP_DEFAULT_DAYS, me.txt). The way of walking changes only until
 * an end day; with no day given it is 7 days from today; on the end day the walker is asked
 * "다리는 좀 괜찮아지셨어요?"; after it the usual mode comes back. The phone keeps it ([TempModeStore]) and tells
 * the server (`PATCH /api/me` tempMode·tempUntil·tempReason, clearTemp), so both know the same.
 */

/** No end day said: this many days from today (MeService.TEMP_DEFAULT_DAYS). */
const val TEMP_DEFAULT_DAYS = 7L

/** A way of walking for a while: [temp] until [until] (that day included), then [usual] again. */
data class TempState(val usual: UserMode, val temp: UserMode, val until: LocalDate, val reason: String)

/** How the server says a way of walking (RuleBrain.modeWord): "휠체어로", "어르신 걸음으로", "어린이와 함께", "걸어서". */
fun modeWord(mode: UserMode?): String = when (mode) {
    UserMode.DISABLED -> "휠체어로"
    UserMode.SENIOR -> "어르신 걸음으로"
    UserMode.CHILD -> "어린이와 함께"
    else -> "걸어서"
}

/** 2026-11-30 → "11월 30일" (KoreanTime.day). */
fun dayText(d: LocalDate): String = "${d.monthValue}월 ${d.dayOfMonth}일"

private val MONTH_DAY = Regex("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일")
private val DAY_ONLY = Regex("(\\d{1,2})\\s*일\\s*까지")
private val N_DAYS = Regex("(\\d{1,2})\\s*일\\s*(동안|간)")
private val N_WEEKS = Regex("(\\d{1,2}|한|두|세)\\s*(주|주일)")
private val N_MONTHS = Regex("(\\d{1,2}|한|두|세)\\s*달")

private fun count(s: String) = when (s) { "한" -> 1L; "두" -> 2L; "세" -> 3L; else -> s.toLong() }

/** "언제까지" in [text], as the server reads it (KoreanTime.until); null when not said ("모르겠어" too). */
fun untilIn(text: String, today: LocalDate): LocalDate? {
    val t = text.replace(" ", "")
    if (t.contains("모르")) return null
    if (t.contains("오늘만") || t.contains("오늘까지") || t.contains("오늘은")) return today
    if (t.contains("내일까지")) return today.plusDays(1)
    if (t.contains("이번주")) return today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    if (t.contains("이번달") || t.contains("이달")) return today.with(TemporalAdjusters.lastDayOfMonth())
    if (t.contains("다음달말") || t.contains("다음달까지")) return today.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth())
    MONTH_DAY.find(text)?.let { m ->
        val d = LocalDate.of(today.year, m.groupValues[1].toInt(), m.groupValues[2].toInt())
        return if (d.isBefore(today)) d.plusYears(1) else d
    }
    DAY_ONLY.find(text)?.let { m ->
        val d = today.withDayOfMonth(minOf(m.groupValues[1].toInt(), today.lengthOfMonth()))
        return if (d.isBefore(today)) d.plusMonths(1) else d
    }
    N_DAYS.find(text)?.let { return today.plusDays(it.groupValues[1].toLong()) }
    if (t.contains("일주일")) return today.plusWeeks(1)
    N_WEEKS.find(text)?.let { return today.plusWeeks(count(it.groupValues[1])) }
    N_MONTHS.find(text)?.let { return today.plusMonths(count(it.groupValues[1])) }
    return null
}

/** The server's question when the end day is not said yet: "많이 불편하시겠어요. 언제까지 휠체어를 타세요? …" */
fun howLongQuestion(mode: UserMode): String =
    "많이 불편하시겠어요. 언제까지 " + modeWord(mode).replace("로", "를").replace("으를", "를") +
        " 타세요? 날짜나 '이번 달까지'처럼 말씀해 주세요."

/** The server's check before saving (RuleBrain.tempUntil): until when, and the wheelchair map pictures. */
fun tempConfirmQuestion(mode: UserMode, until: LocalDate, said: Boolean): String {
    val head = if (!said) "그럼 일주일 동안(${dayText(until)}까지) ${modeWord(mode)} 안내하고, 그때 다시 여쭤볼게요."
    else "${dayText(until)}까지 ${modeWord(mode)} 안내하고, 그때 다시 여쭤볼게요."
    val extra = if (mode == UserMode.DISABLED) " 그동안 장애인 화장실과 보도턱 낮춘 횡단보도도 지도에 보여 드릴게요." else ""
    return "$head$extra 맞으세요?"
}

/** The server's question on the end day (RuleBrain.suggest): "다리는 좀 괜찮아지셨어요? …" */
fun tempEndQuestion(state: TempState): String =
    (if (state.reason.contains("다리")) "다리는" else "몸은") +
        " 좀 괜찮아지셨어요? 괜찮아지셨으면 내일부터 평소처럼 ${modeWord(state.usual)} 안내할까요?"

/** The phone's copy of the temporary way of walking; the server keeps the same in user_profile. */
object TempModeStore {
    private const val PREFS = "temp_mode"

    fun get(context: Context): TempState? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val usual = p.getString("usual", null)?.let { UserMode.fromKey(it) } ?: return null
        val temp = p.getString("temp", null)?.let { UserMode.fromKey(it) } ?: return null
        val until = p.getString("until", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return TempState(usual, temp, until, p.getString("reason", "").orEmpty())
    }

    fun save(context: Context, state: TempState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("usual", state.usual.key).putString("temp", state.temp.key)
            .putString("until", state.until.toString()).putString("reason", state.reason).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
