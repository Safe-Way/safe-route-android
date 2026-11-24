package com.deungbul.anshimgil.data.safety

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripFeedbackTest {
    @Test
    fun answersBecomeWhatTheServerLearnsFrom() {
        assertEquals("good" to null, feedbackFor(FeedbackStep.HOW, 0))
        assertNull(feedbackFor(FeedbackStep.HOW, 1)) // asked why next
        assertEquals(FeedbackStep.WHY, nextFeedbackStep(FeedbackStep.HOW, 1))
        assertNull(feedbackFor(FeedbackStep.HOW, 2)) // 말 안 할래요: nothing sent
        // The server counts "계단" in the reason (PreferenceLearner).
        assertEquals("bad" to "계단", feedbackFor(FeedbackStep.WHY, 0))
        assertNull(nextFeedbackStep(FeedbackStep.WHY, 0))
    }

    @Test
    fun whatTheServerLearnedIsSaid() {
        val json = JSONObject("""{"learned":[{"what":"stairsOk","from":2,"to":1,"say":"계단이 힘드셨다는 말이 쌓여서, 앞으로 계단이 적은 길로 안내할게요."}]}""")
        assertEquals(listOf("계단이 힘드셨다는 말이 쌓여서, 앞으로 계단이 적은 길로 안내할게요."), learnedSays(json))
        assertEquals(emptyList<String>(), learnedSays(JSONObject("""{"learned":[]}""")))
    }


    /** 역할: 도착 뒤 "말 안 할래요"를 고르면 서버에 아무것도 보내지 않고 더 묻지도 않는지 확인한다. */
    @Test
    fun notSayingSendsNothingAndAsksNothing() {
        assertNull(feedbackFor(FeedbackStep.HOW, 2))       // 보낼 것 없음
        assertNull(nextFeedbackStep(FeedbackStep.HOW, 2))  // 다음 질문 없음
    }

    /** 역할: 불편한 이유 목록에 없는 번호가 와도 앱이 멈추지 않고, "불편했다"만 이유 없이(null) 보내는지 확인한다. */
    @Test
    fun anUnknownReasonStillSendsBad() {
        assertEquals("bad" to null, feedbackFor(FeedbackStep.WHY, 99))
    }
}
