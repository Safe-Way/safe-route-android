package com.deungbul.anshimgil.data

/*
 * 처음 맞추기 (11/8): right after the mode is picked on a new install, AI 비서 asks a few short questions in its
 * bubble on the map, each answered with a tap, and sets the first settings from the answers — the text size
 * (and, when the text is still hard to read, its own enlargement on top, [TEXT_BOOST_STEPS]), voice guidance and
 * the SOS button. "다음에 할게요" at the start leaves everything as it is. Each answer is applied at once, so the
 * walker sees the change; everything can be changed later in settings or by asking AI 비서.
 * Also asked when the mode is changed to another one later ([SetupStep.CHANGED]), since what suits the new
 * walker may differ. Not asked when the mode was set by 말로 맞추기 (that already set these) or picked again.
 * The questions start from the current settings: an answer only changes what it is about.
 */

/** One question, its answers in order (the first is the suggested one). */
enum class SetupStep(val question: String, val answers: List<String>) {
    START("처음 오셨네요. 세 가지만 여쭤보고 쓰기 편하게 맞춰 드릴까요?", listOf("좋아요", "다음에 할게요")),
    CHANGED("모드를 바꾸셨네요. 세 가지만 다시 여쭤보고 새 모드에 맞춰 드릴까요?", listOf("좋아요", "지금 그대로 둘게요")),
    TEXT("화면의 글씨가 잘 보이세요?", listOf("잘 보여요", "조금 작아요", "많이 작아요")),
    VOICE("걸을 때 소리로 길을 알려 드릴까요?", listOf("소리로 알려 줘", "화면만 볼게요")),
    SOS("위급할 때 바로 누를 SOS 버튼을 지도에 둘까요?", listOf("둘래요", "필요 없어요")),
    DONE("", listOf("알겠어요")),
}

/** What the answers set; null is left as it is. */
data class SetupAnswers(
    val textSize: TextSize? = null,
    val textBoost: Float? = null,
    val voiceGuide: Boolean? = null,
    val sosButton: Boolean? = null,
)

/** The next step after answer [index] to [step], with the settings so far; null step means finished or skipped. */
fun answerSetup(step: SetupStep, index: Int, so: SetupAnswers): Pair<SetupStep?, SetupAnswers> = when (step) {
    SetupStep.START, SetupStep.CHANGED -> (if (index == 0) SetupStep.TEXT else null) to so
    SetupStep.TEXT -> SetupStep.VOICE to when (index) {
        // Readable as it is: left as it is (a walker on 크게 who changes the mode keeps it).
        0 -> so
        1 -> so.copy(textSize = TextSize.LARGE)
        // Still hard to read at the biggest size: AI 비서's own enlargement on top, one step.
        else -> so.copy(textSize = TextSize.LARGE, textBoost = TEXT_BOOST_STEPS[1])
    }
    SetupStep.VOICE -> SetupStep.SOS to so.copy(voiceGuide = index == 0)
    SetupStep.SOS -> SetupStep.DONE to so.copy(sosButton = index == 0)
    SetupStep.DONE -> null to so
}

/** The closing line: what was set, and where to change it. */
fun setupSummary(a: SetupAnswers): String {
    val parts = listOfNotNull(
        a.textSize?.let { size -> "글씨 ${size.label}" + if ((a.textBoost ?: 1f) > 1f) "(한 단계 더)" else "" } ?: "글씨 그대로",
        a.voiceGuide?.let { if (it) "음성 안내 켬" else "음성 안내 끔" },
        a.sosButton?.let { if (it) "SOS 버튼 켬" else "SOS 버튼 없음" },
    )
    return "이렇게 맞췄어요: ${parts.joinToString(", ")}. 설정에서나 저에게 말해서 언제든 바꿀 수 있어요."
}

/** What AI 비서 says for [step] ([SetupStep.DONE] says what was set). */
fun setupQuestion(step: SetupStep, so: SetupAnswers): String = if (step == SetupStep.DONE) setupSummary(so) else step.question
