package com.deungbul.anshimgil.data

/*
 * 걷는 사람이 바뀌었다는 말 (사용성 점검표 9·10번, 11/9): "오늘은 휠체어 안 타", "다리를 다쳐서 휠체어 타".
 * The mode is changed only when asked for in so many words ("휠체어 모드로 바꿔 줘"); a remark about how the
 * walker gets about today is asked back first, and an injury is asked how long. Handled on the phone: the server
 * assistant does not know the app's modes.
 */

private val MODE_WORDS: List<Pair<List<String>, UserMode>> = listOf(
    listOf("휠체어", "장애인") to UserMode.DISABLED,
    listOf("어르신", "노인") to UserMode.SENIOR,
    listOf("어린이", "아이 모드", "아이모드") to UserMode.CHILD,
    listOf("일반") to UserMode.GENERAL,
)

/** A mode's name as AI 비서 says it. */
val UserMode.spokenName: String
    get() = when (this) {
        UserMode.CHILD -> "어린이 모드"
        UserMode.SENIOR -> "어르신 모드"
        UserMode.DISABLED -> "장애인(휠체어) 모드"
        UserMode.GENERAL -> "일반 모드"
    }

const val KEEP_AS_IS = "그대로 둘래"
private const val HOW_LONG = "휠체어는 언제까지 타세요?"
private val HOW_LONG_ANSWERS = listOf("오늘만", "며칠 동안", "한동안 계속")


private fun String.squeezed() = replace(" ", "")

/**
 * AI 비서's reply when [said] is about who walks or how (a mode), given the conversation so far and the [current]
 * mode; null when it is not. Answers here stay on the phone.
 */
fun walkerChange(said: String, history: List<ChatLine>, current: UserMode): AssistantAction? {
    val s = said.squeezed()
    fun has(vararg w: String) = w.any { s.contains(it.squeezed()) }
    val lastAsked = history.lastOrNull { !it.fromUser }?.text.orEmpty()

    if (has(KEEP_AS_IS, "그대로 둘게", "그대로 두", "바꾸지 마", "안 바꿔")) {
        return AssistantAction.Answer("네, ${current.spokenName} 그대로 둘게요.", onPhone = true)
    }
    // The answer to "언제까지 타세요?": the wheelchair was already said, and how long is known now.
    if (HOW_LONG in lastAsked && (HOW_LONG_ANSWERS.any { s.contains(it.squeezed()) } || has("오늘", "내일", "며칠", "일주일", "주", "달", "계속", "당분간"))) {
        return AssistantAction.ChangeSettings(
            "알겠어요. ${UserMode.DISABLED.spokenName}로 바꿔 계단 없는 길로 안내할게요. 다 나으면 \"휠체어 안 타\"라고 말해 주세요.",
            mode = UserMode.DISABLED,
        )
    }
    val mode = MODE_WORDS.firstOrNull { (words, _) -> words.any { s.contains(it.squeezed()) } }?.second
    // Asked for in so many words: change it (it can be taken back at once).
    if (mode != null && has("모드로", "모드 바꿔", "바꿔줘", "바꿔 줘", "바꿔주세요", "로 바꿔", "으로 바꿔", "모드 해", "모드로 해")) {
        return if (mode == current) AssistantAction.Answer("지금도 ${current.spokenName}예요.", onPhone = true)
        else AssistantAction.ChangeSettings("${mode.spokenName}로 바꿀게요.", mode = mode)
    }
    // Anything urgent goes to the emergency rule first (the server's urgent words, Assistant.kt).
    if (!has("휠체어") || isEmergency(said)) return null
    // "오늘은 휠체어 안 타": walking without it today.
    if (has("안타", "안 타", "안써", "안 써", "안탈", "없이", "내려", "그만")) {
        if (current != UserMode.DISABLED) return AssistantAction.Answer("지금은 휠체어 모드가 아니에요. ${current.spokenName} 그대로 둘게요.", onPhone = true)
        return AssistantAction.Ask(
            "오늘은 휠체어 없이 걸으시는군요. 어떤 모드로 바꿀까요?",
            listOf("어르신 모드로 바꿔 줘", "일반 모드로 바꿔 줘", KEEP_AS_IS),
            onPhone = true,
        )
    }
    // "다리를 다쳐서 휠체어 타": for a while, so ask how long.
    if (has("다쳐", "다쳤", "다친", "수술", "깁스", "부러", "삐었", "골절")) {
        return AssistantAction.Ask("다치셨군요. 얼른 나으시길 바라요. $HOW_LONG", HOW_LONG_ANSWERS, onPhone = true)
    }
    if (current == UserMode.DISABLED) return null
    return AssistantAction.Ask(
        "휠체어로 다니시면 ${UserMode.DISABLED.spokenName}로 바꿀까요? 계단 없는 길로 안내해요.",
        listOf("휠체어 모드로 바꿔 줘", KEEP_AS_IS),
        onPhone = true,
    )
}
