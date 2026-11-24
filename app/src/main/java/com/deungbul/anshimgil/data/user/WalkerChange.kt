package com.deungbul.anshimgil.data.user

import com.deungbul.anshimgil.data.assistant.AssistantAction
import com.deungbul.anshimgil.data.assistant.isEmergency
import com.deungbul.anshimgil.data.assistant.serverModeWord

/*
 * 걷는 사람이 바뀌었다는 말 (사용성 점검표 9·10번, 11/9): "오늘은 휠체어 안 타", "다리를 다쳐서 휠체어 타".
 * The mode is changed only when asked for in so many words ("휠체어 모드로 바꿔 줘"); a remark about how the
 * walker gets about today is asked back first, and an injury is asked how long. Handled on the phone: the server
 * assistant does not know the app's modes.
 */


/** A mode's name as AI 비서 says it. */
val UserMode.spokenName: String
    get() = when (this) {
        UserMode.CHILD -> "어린이 모드"
        UserMode.SENIOR -> "어르신 모드"
        UserMode.DISABLED -> "장애인(휠체어) 모드"
        UserMode.GENERAL -> "일반 모드"
    }

const val KEEP_AS_IS = "그대로 둘래"

/** The server's yes / no buttons (RuleBrain.YES_NO). */
val YES_NO = listOf("네", "아니요")

private fun String.squeezed() = replace(" ", "")

private fun isYes(s: String) = s.startsWith("네") || s.startsWith("응") || s.startsWith("예") || s.contains("맞아") || s.contains("좋아")
private fun isNo(s: String) = s.startsWith("아니") || s.contains("싫어") || s.contains("괜찮아요")

private val ASKED_DAY = Regex("(\\d{1,2})월 (\\d{1,2})일까지")

/**
 * AI 비서's reply when [said] is about who walks or how, given the conversation so far, the [current] mode and the
 * walker's [usual] mode (differs from [current] during a temporary change); null when it is not. The steps are the
 * team server's (RuleBrain.tempBody · tempUntil · 다 나음 · 오늘만, TempMode.kt); answers stay on the phone.
 */
fun walkerChange(
    said: String,
    history: List<ChatLine>,
    current: UserMode,
    usual: UserMode = current,
    today: java.time.LocalDate = java.time.LocalDate.now(),
): AssistantAction? {
    val s = said.squeezed()
    fun has(vararg w: String) = w.any { s.contains(it.squeezed()) }
    val lastAsked = history.lastOrNull { !it.fromUser }?.text.orEmpty()

    // Answers to the server's yes/no checks.
    if (lastAsked.endsWith("맞으세요?") && (isYes(s) || isNo(s))) {
        if (isNo(s)) return AssistantAction.Answer("알겠어요. 바꾸지 않을게요.", onPhone = true)
        if (lastAsked.contains("그때 다시 여쭤볼게요")) {
            val (m, d) = ASKED_DAY.find(lastAsked)?.destructured ?: return null
            var until = java.time.LocalDate.of(today.year, m.toInt(), d.toInt())
            if (until.isBefore(today)) until = until.plusYears(1)
            val mode = serverModeWord(lastAsked) ?: modeFromWord(lastAsked) ?: UserMode.DISABLED
            val reason = if (history.any { it.fromUser && it.text.contains("다리") }) "다리 다침" else "몸이 불편함"
            return AssistantAction.ChangeSettings(
                "알겠어요. ${dayText(until)}까지 ${modeWord(mode)} 안내할게요.",
                mode = mode,
                addMapItems = if (mode == UserMode.DISABLED) setOf("toilet:wheelchair", "crosswalk:curb_low") else emptySet(),
                tempUntil = until,
                tempReason = reason,
            )
        }
        if (lastAsked.contains("오늘은 걸어서 다니시는 걸로")) {
            return AssistantAction.ChangeSettings(
                "알겠어요. 오늘은 걸어서 다니시는 걸로 안내할게요. 내일은 다시 평소대로 할게요.",
                mode = UserMode.GENERAL, tempUntil = today, tempReason = "오늘만",
            )
        }
        if (lastAsked.contains("이제 평소처럼") || lastAsked.contains("내일부터 평소처럼")) {
            return AssistantAction.ChangeSettings("다행이에요. 평소처럼 ${modeWord(usual)} 안내할게요.", mode = usual, clearTemp = true)
        }
    }
    // The answer to "언제까지 … 타세요?": the end day, or 7 days when not known.
    if (lastAsked.contains("타세요? 날짜나")) {
        val mode = serverModeWord(lastAsked.replace("를", "로")) ?: modeFromWord(lastAsked) ?: UserMode.DISABLED
        val day = untilIn(said, today)
        return AssistantAction.Ask(tempConfirmQuestion(mode, day ?: today.plusDays(TEMP_DEFAULT_DAYS), day != null), YES_NO, onPhone = true)
    }
    // "다리를 다쳐서 휠체어 타고 다녀": for a while, so until when.
    val named = serverModeWord(said)
    if (has("다쳐", "다쳤", "깁스", "수술") && named != null) {
        val day = untilIn(said, today) ?: return AssistantAction.Ask(howLongQuestion(named), emptyList(), onPhone = true)
        return AssistantAction.Ask(tempConfirmQuestion(named, day, true), YES_NO, onPhone = true)
    }
    // 다 나음.
    if (has("다나았", "나았어", "이제괜찮", "다시걸")) {
        return AssistantAction.Ask("다행이에요! 이제 평소처럼 ${modeWord(usual)} 다니시는 걸로 바꿀까요? 맞으세요?", YES_NO, onPhone = true)
    }
    // 오늘만: "오늘은 휠체어 안 타".
    if ((s.startsWith("오늘은") || s.startsWith("오늘만")) && named != null && has("안타", "안써")) {
        return AssistantAction.Ask("오늘은 걸어서 다니시는 걸로 안내할까요? 내일은 다시 평소대로 할게요. 맞으세요?", YES_NO, onPhone = true)
    }

    if (has(KEEP_AS_IS, "그대로 둘게", "그대로 두", "바꾸지 마", "안 바꿔")) {
        return AssistantAction.Answer("네, ${current.spokenName} 그대로 둘게요.", onPhone = true)
    }
    // The way of walking by the team server's words (RuleReader.MODE_WORDS), plus the app's own mode names.
    val mode = named ?: when {
        has("장애인") -> UserMode.DISABLED
        has("일반") -> UserMode.GENERAL
        else -> null
    }
    // Asked for in so many words: change it (it can be taken back at once).
    if (mode != null && has("모드로", "모드 바꿔", "바꿔줘", "바꿔 줘", "바꿔주세요", "로 바꿔", "으로 바꿔", "모드 해", "모드로 해")) {
        return if (mode == current) AssistantAction.Answer("지금도 ${current.spokenName}예요.", onPhone = true)
        else AssistantAction.ChangeSettings("${mode.spokenName}로 바꿀게요.", mode = mode)
    }
    // Anything urgent goes to the emergency rule first (the server's urgent words, Assistant.kt).
    if (!has("휠체어") || isEmergency(said) || current == UserMode.DISABLED) return null
    return AssistantAction.Ask(
        "휠체어로 다니시면 ${UserMode.DISABLED.spokenName}로 바꿀까요? 계단 없는 길로 안내해요.",
        listOf("휠체어 모드로 바꿔 줘", KEEP_AS_IS),
        onPhone = true,
    )
}

/** The mode in one of the server's mode words ("어르신 걸음으로", "어린이와 함께", "걸어서"). */
private fun modeFromWord(text: String): UserMode? = when {
    text.contains("휠체어") -> UserMode.DISABLED
    text.contains("어르신 걸음") -> UserMode.SENIOR
    text.contains("어린이와 함께") -> UserMode.CHILD
    text.contains("걸어서") -> UserMode.GENERAL
    else -> null
}
