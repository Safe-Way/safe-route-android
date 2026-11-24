package com.deungbul.anshimgil.data.user

import com.deungbul.anshimgil.data.assistant.summary
import com.deungbul.anshimgil.data.server.ServerApi

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

/*
 * New walkers are first asked the team server's setup questions (safe-route RuleBrain.startSetup · setupAid ·
 * setupStairs · setupConfirm, dialogues.md 2번): how they get about, how many steps are fine, a check, then the
 * trip-record consent (consent.md 1번, "처음 쓸 때, 비서의 다섯째 질문"). The app's own text / voice / SOS questions,
 * which the team has no rule for, follow. The answers go to the server's user_profile (mobilityAid, stairsOk, setupDone).
 */

/** The server's ways of getting about (RuleBrain.AID_CHOICES), as said. */
val SETUP_AIDS = listOf("walk" to "걸어서", "cane" to "지팡이", "wheelchair" to "휠체어", "stroller" to "유모차")

/** The server's words for a way of getting about (RuleBrain.aidWord). */
fun aidWord(aid: String?): String = when (aid) {
    "wheelchair", "manual_wheelchair" -> "휠체어"
    "power_wheelchair" -> "전동 휠체어"
    "cane" -> "지팡이"
    "walker" -> "보행기"
    "stroller" -> "유모차"
    else -> "걸어서"
}

/** The server's user_profile value for a way of getting about (RuleBrain.aidFields: mobility_aid). */
fun aidProfileValue(aid: String): String? = when (aid) {
    "wheelchair" -> "manual_wheelchair"
    "cane", "walker", "stroller", "power_wheelchair" -> aid
    else -> null
}

/** The team's consent question for the trip record (consent.md 1번), asked in the setup. */
const val SETUP_CONSENT_QUESTION =
    "다닌 기록을 남겨도 될까요? 기록을 남기면 비서가 더 잘 도와드려요. " +
        "남기는 것: 간 곳의 이름, 나간 요일과 시간대(30분 단위), 걸어갔는지 버스를 탔는지, 그날 날씨, 다녀와서 남긴 한마디. " +
        "남기지 않는 것: 이름, 전화번호, 집 주소, 지나온 길, 정확한 시각. 언제든 [내 기록 지우기]를 누르면 바로 모두 지워져요."

/**
 * Read out instead of [SETUP_CONSENT_QUESTION] when answering by voice: the whole text took about 25 seconds before the
 * microphone opened (11/10 phone test). The screen still shows the team's full words (consent.md 1번).
 */
const val SETUP_CONSENT_SPOKEN = "다닌 기록을 남겨도 될까요? 남기는 것과 남기지 않는 것은 화면에 적어 두었어요."

/** One question, its answers in order (the first is the suggested one). */
enum class SetupStep(val question: String, val answers: List<String>) {
    /**
     * Asked first (team, 11/10): answer by voice or by tapping. By voice, each next question is read out and the
     * microphone opens by itself, so [말로 답하기] need not be pressed every time.
     */
    WAY("처음 몇 가지를 여쭤볼게요. 말로 답하실래요, 버튼을 누르실래요?", listOf("말로 할게요", "버튼으로 할게요")),
    /** The server's first setup question for a new walker. */
    AID("처음 오셨네요. 어떻게 다니세요? 걸어서, 지팡이, 휠체어, 유모차 중에 말씀해 주세요.", SETUP_AIDS.map { it.second }),
    /** The server's stairs question; the words depend on the answer before ([setupQuestion]). */
    STAIRS("", listOf("못 가요", "몇 칸은 괜찮아요", "괜찮아요")),
    /** The server's check before saving ([setupQuestion]). */
    CONFIRM("", listOf("네", "아니요")),
    CONSENT(SETUP_CONSENT_QUESTION, listOf(CONSENT_YES, CONSENT_NO)),
    START("세 가지만 더 여쭤보고 쓰기 편하게 맞춰 드릴까요?", listOf("좋아요", "다음에 할게요")),
    CHANGED("모드를 바꾸셨네요. 세 가지만 다시 여쭤보고 새 모드에 맞춰 드릴까요?", listOf("좋아요", "지금 그대로 둘게요")),
    TEXT("화면의 글씨가 잘 보이세요?", listOf("잘 보여요", "조금 작아요", "많이 작아요")),
    VOICE("걸을 때 소리로 길을 알려 드릴까요?", listOf("소리로 알려 줘", "화면만 볼게요")),
    SOS("위급할 때 바로 누를 SOS 버튼을 지도에 둘까요?", listOf("둘래요", "필요 없어요")),
    /** The summary; its second answer takes everything back (사용성 점검표 21번). */
    DONE("", listOf("알겠어요", "처음대로 되돌리기")),
}

/** The index of "처음대로 되돌리기" among [SetupStep.DONE]'s answers. */
const val SETUP_UNDO_INDEX = 1

/** What the answers set; null is left as it is. */
data class SetupAnswers(
    /** Answering by voice ([SetupStep.WAY]). */
    val byVoice: Boolean = false,
    /** The server's setup: the way of getting about ([SETUP_AIDS] key) and steps (0 못 가요, 1 몇 칸, 2 괜찮아요). */
    val aid: String? = null,
    val stairs: Int? = null,
    /** "맞으세요?" answered yes: send aid and stairs to the server. */
    val confirmed: Boolean = false,
    /** The trip-record answer, when given here. */
    val consent: Boolean? = null,
    val textSize: TextSize? = null,
    val textBoost: Float? = null,
    val voiceGuide: Boolean? = null,
    val sosButton: Boolean? = null,
)

/** The next step after answer [index] to [step], with the settings so far; null step means finished or skipped. */
fun answerSetup(step: SetupStep, index: Int, so: SetupAnswers, current: TextSize = TextSize.NORMAL): Pair<SetupStep?, SetupAnswers> = when (step) {
    SetupStep.WAY -> SetupStep.AID to so.copy(byVoice = index == 0)
    // A wheelchair: no steps, straight to the check (RuleBrain.setupAid).
    SetupStep.AID -> SETUP_AIDS[index].first.let { aid ->
        if (aid == "wheelchair") SetupStep.CONFIRM to so.copy(aid = aid, stairs = 0) else SetupStep.STAIRS to so.copy(aid = aid)
    }
    SetupStep.STAIRS -> SetupStep.CONFIRM to so.copy(stairs = index)
    SetupStep.CONFIRM -> SetupStep.CONSENT to so.copy(confirmed = index == 0)
    SetupStep.CONSENT -> SetupStep.START to so.copy(consent = index == 0)
    SetupStep.START, SetupStep.CHANGED -> (if (index == 0) SetupStep.TEXT else null) to so
    SetupStep.TEXT -> SetupStep.VOICE to when (index) {
        // Readable as it is: left as it is (a walker on 크게 who changes the mode keeps it).
        0 -> so
        // Already on 크게 (어르신 starts there): AI 비서's own enlargement on top instead, one or two steps.
        1 -> if (current == TextSize.LARGE) so.copy(textSize = TextSize.LARGE, textBoost = TEXT_BOOST_STEPS[1]) else so.copy(textSize = TextSize.LARGE)
        // Still hard to read at the biggest size: AI 비서's own enlargement on top, one step (two from 크게).
        else -> so.copy(textSize = TextSize.LARGE, textBoost = TEXT_BOOST_STEPS[if (current == TextSize.LARGE) 2 else 1])
    }
    SetupStep.VOICE -> SetupStep.SOS to so.copy(voiceGuide = index == 0)
    SetupStep.SOS -> SetupStep.DONE to so.copy(sosButton = index == 0)
    SetupStep.DONE -> null to so
}

/** The closing line: what was set, and where to change it. */
fun setupSummary(a: SetupAnswers): String {
    val parts = listOfNotNull(
        a.textSize?.let { size -> "글씨 ${size.label}" + when {
            (a.textBoost ?: 1f) >= TEXT_BOOST_STEPS[2] -> "(두 단계 더)"
            (a.textBoost ?: 1f) > 1f -> "(한 단계 더)"
            else -> ""
        } } ?: "글씨 그대로",
        a.voiceGuide?.let { if (it) "음성 안내 켬" else "음성 안내 끔" },
        a.sosButton?.let { if (it) "SOS 버튼 켬" else "SOS 버튼 없음" },
    )
    return "이렇게 맞췄어요: ${parts.joinToString(", ")}. 설정에서나 저에게 말해서 언제든 바꿀 수 있어요."
}

/** What AI 비서 says for [step] ([SetupStep.DONE] says what was set). */
fun setupQuestion(step: SetupStep, so: SetupAnswers): String = when (step) {
    SetupStep.DONE -> setupSummary(so)
    // RuleBrain.setupAid / setupConfirm, word for word.
    SetupStep.STAIRS -> "${aidWord(so.aid)}(으)로 다니시는군요. 계단은 몇 칸 괜찮으세요?"
    SetupStep.CONFIRM -> "${aidWord(so.aid)}(으)로 다니고, " + when (so.stairs) {
        0 -> "계단은 못 가요"
        1 -> "계단은 몇 칸 괜찮아요"
        else -> "계단은 괜찮아요"
    } + ". 이렇게 기억할까요? 맞으세요?"
    else -> step.question
}

/**
 * The answer meant by words said to a 처음 맞추기 question (사용성 점검표 2번: answered by voice alone), as the
 * index into [SetupStep.answers]; null when it is not clear, and the question is asked again.
 */
fun setupAnswerFromSpeech(step: SetupStep, said: String): Int? {
    val s = said.replace(" ", "").trimEnd('.', '!', '?', '~')
    fun has(vararg w: String) = w.any { s.contains(it.replace(" ", "")) }
    // "잘 모르겠어": asked again rather than guessed.
    if (has("모르겠", "글쎄")) return null
    // A no said around a yes word comes first ("안 좋아", "남기지 마", "하지 마").
    val no = has(
        "아니", "아뇨", "싫", "별로", "안좋", "필요없", "필요 없", "안해", "안 할", "하지마", "하지 마", "말아", "말고",
        "됐어", "됐습니다", "다음에", "나중에", "그대로", "안남", "남기지", "안둘", "두지마", "빼",
    )
    val yes = !no && has(
        "좋아", "좋습", "응", "네", "예", "그래", "그렇게", "맞아", "맞습", "오케이", "해줘", "해 줘", "해주세요", "할게", "부탁",
        "맞춰", "남겨", "남길", "어어",
    )
    return when (step) {
        SetupStep.WAY -> when { has("버튼", "누를", "눌러", "손으로") -> 1; has("말로", "말할", "목소리", "음성") || yes -> 0; no -> 1; else -> null }
        // The server's readings (RuleBrain.aid, setupStairs), with more ways of saying the same.
        SetupStep.AID -> when {
            has("휠체어", "전동") -> 2
            has("유모차", "아기", "애기", "아이데리고") -> 3
            has("지팡이", "보행기", "워커", "짚고", "지팡") -> 1
            has("걸어", "그냥", "혼자", "두발", "도움없이", "걷") -> 0
            else -> null
        }
        SetupStep.STAIRS -> when {
            has("못", "안돼", "안 돼", "힘들", "버거", "벅차", "무서", "자신없", "어려", "무리", "싫") -> 0
            has("몇", "조금", "좀", "한두", "짧으면", "적으면", "천천히") -> 1
            has("괜찮", "다돼", "잘다녀", "문제없", "상관없", "많아도", "돼", "가능") -> 2
            else -> null
        }
        SetupStep.CONFIRM, SetupStep.CONSENT -> when { no -> 1; yes -> 0; else -> null }
        // "괜찮아요" to an offer is a polite no.
        SetupStep.START, SetupStep.CHANGED -> when { no || has("괜찮") -> 1; yes -> 0; else -> null }
        SetupStep.TEXT -> when {
            has("많이", "너무", "안보여", "안 보여", "하나도", "흐려", "흐릿", "가물") -> 2
            has("조금", "좀", "약간", "작아", "작은", "작네") -> 1
            has("잘보여", "보여", "괜찮", "충분", "문제없", "커", "잘 읽") -> 0
            else -> null
        }
        SetupStep.VOICE -> when {
            has("화면만", "화면으로", "소리없이", "조용", "끄", "꺼", "시끄") || no -> 1
            has("소리", "말로", "들려", "알려", "읽어") || yes -> 0
            else -> null
        }
        SetupStep.SOS -> when {
            no || has("괜찮") -> 1
            has("둘래", "둬", "놔", "있으면", "좋겠", "필요해", "필요하") || yes -> 0
            else -> null
        }
        SetupStep.DONE -> when { has("되돌", "처음대로", "취소", "다시") -> SETUP_UNDO_INDEX; has("알겠", "고마", "됐어") || yes -> 0; else -> null }
    }
}

/**
 * When the words above cannot tell (null), the team server's AI picks the button meant
 * (`POST /api/ai/pick-answer`, server #152): the index among [choices], or null when it cannot tell either, the AI
 * is down (503 AI_UNAVAILABLE) or the server has not got the window yet. Never throws.
 */
suspend fun pickAnswerOnServer(question: String, choices: List<String>, said: String): Int? {
    if (!ServerApi.isConfigured || choices.size !in 2..6) return null
    return runCatching {
        val j = ServerApi.post(
            "/api/ai/pick-answer",
            org.json.JSONObject().put("question", question).put("choices", org.json.JSONArray(choices)).put("said", said),
        )
        if (j.isNull("index")) null else j.optInt("index", -1).takeIf { it in choices.indices }
    }.getOrNull()
}
