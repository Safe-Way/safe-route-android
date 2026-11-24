package com.deungbul.anshimgil.data.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.deungbul.anshimgil.data.user.ChatLine

class ChatStoreTest {
    @Test fun keptAndReadBack() {
        val lines = listOf(ChatLine(false, "무엇을 도와드릴까요?"), ChatLine(true, "양림동에서 사직공원 갈래"), ChatLine(false, "길을 \"찾았어요\".\n지도에서 볼까요?"))
        assertEquals(lines, chatFromJson(chatToJson(lines)))
        assertEquals(emptyList<ChatLine>(), chatFromJson(null))
    }

    @Test fun onlyTheLastLinesAreKept() {
        val many = (1..CHAT_KEEP_LINES + 5).map { ChatLine(it % 2 == 0, "말 $it") }
        val back = chatFromJson(chatToJson(many))
        assertEquals(CHAT_KEEP_LINES, back.size)
        assertEquals("말 6", back.first().text)
    }

    @Test fun askingToClear() {
        listOf("대화 지워 줘", "채팅 기록 초기화해줘", "대화 내역 삭제", "처음부터 다시 하자").forEach { assertTrue(it, asksToClearChat(it)) }
        listOf("문화전당 가자", "기록 보여 줘", "지우개 파는 곳").forEach { assertFalse(it, asksToClearChat(it)) }
    }

    @Test fun answeringTheClearQuestion() {
        listOf("지우기", "네", "응 지워", "삭제해", "대화 지워 줘").forEach { assertEquals(it, true, clearAnswer(it)) }
        listOf("취소", "아니", "안 지울래", "괜찮아", "그냥 둬").forEach { assertEquals(it, false, clearAnswer(it)) }
        assertEquals(null, clearAnswer("문화전당 가자"))
    }
}
