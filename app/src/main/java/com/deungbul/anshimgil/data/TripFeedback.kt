package com.deungbul.anshimgil.data

/*
 * 도착 뒤 한마디 (팀 할 일 m07, 11/9): after arriving by guidance (with the walker's yes to keeping trips), AI 비서
 * asks how the way was and, when it was hard, why. The answer goes to the server with the trip
 * (PATCH /api/me/trips/{id} feedback, feedbackReason), which learns from it: stairs said to be hard twice make the
 * routes avoid more stairs, and it says so ("앞으로 계단이 적은 길로 안내할게요"), shown in the bubble.
 */

/** One question after arriving, with its answers in order. */
enum class FeedbackStep(val question: String, val answers: List<String>) {
    HOW("도착했어요. 길은 편하셨어요?", listOf("편했어요", "불편했어요", "말 안 할래요")),
    WHY("무엇이 불편하셨어요?", listOf("계단", "오르막", "길이 멀어요", "사람·차가 많아요", "다른 이유")),
}

/** What to send for answer [index] to [step]: (feedback, reason), or null to send nothing more. */
fun feedbackFor(step: FeedbackStep, index: Int): Pair<String, String?>? = when (step) {
    FeedbackStep.HOW -> when (index) {
        0 -> "good" to null
        else -> null
    }
    FeedbackStep.WHY -> "bad" to step.answers.getOrNull(index)
}

/** The step after answer [index] to [step]; null when done. */
fun nextFeedbackStep(step: FeedbackStep, index: Int): FeedbackStep? =
    if (step == FeedbackStep.HOW && index == 1) FeedbackStep.WHY else null
