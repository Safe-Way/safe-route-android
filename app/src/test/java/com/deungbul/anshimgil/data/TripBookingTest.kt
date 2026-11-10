package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TripBookingTest {
    // Tuesday 2026-11-10, 10:05 in the morning.
    private val now = LocalDateTime.of(2026, 11, 10, 10, 5)
    private fun at(s: String) = LocalDateTime.parse(s)
    private fun book(said: String) = bookingAsk(said, now) as? BookingAsk.Book

    @Test
    fun relativeTimes() {
        assertEquals(BookingAsk.Book(at("2026-11-10T10:15"), "문화전당"), bookingAsk("10분 뒤에 문화전당 가자", now))
        assertEquals(at("2026-11-10T11:35"), book("1시간 반 뒤에 시청 가자")?.at)
        assertEquals(at("2026-11-10T12:05"), book("두 시간 후에 병원 가는 길 알려 줘")?.at)
        assertEquals(at("2026-11-10T10:35"), book("30분 있다가 집에 갈래")?.at)
        assertEquals("집", book("30분 있다가 집에 갈래")?.query)
    }

    @Test
    fun clockTimes() {
        assertEquals(BookingAsk.Book(at("2026-11-10T15:00"), "시청"), bookingAsk("오늘 3시에 시청 길 안내 예약해 줘", now))
        assertEquals(at("2026-11-10T15:30"), book("3시 반에 문화전당 가자")?.at)
        assertEquals(at("2026-11-11T09:00"), book("내일 아침 9시에 문화전당 가자")?.at)
        assertEquals(at("2026-11-11T09:00"), book("9시에 문화전당 가자")?.at.let { it }.let { book("내일 9시에 문화전당 가자")?.at })
        assertEquals(at("2026-11-10T21:00"), book("오늘 9시에 문화전당 가자")?.at) // 9 a.m. is past: the evening
        assertEquals(at("2026-11-12T09:00"), book("11월 12일 오전 9시 국립아시아문화전당 길 안내 예약")?.at)
        assertEquals("국립아시아문화전당", book("11월 12일 오전 9시 국립아시아문화전당 길 안내 예약")?.query)
        assertEquals(at("2026-11-10T18:20"), book("저녁 6시 20분에 충장로 가자")?.at)
        assertEquals(at("2026-11-13T14:00"), book("금요일 2시에 병원 가는 길 알려줘")?.at)
        assertEquals(at("2026-11-10T14:00"), book("오후 2시에 데려다 줘")?.at)
        assertNull(book("오후 2시에 데려다 줘")?.query)
    }

    @Test
    fun theSamePlaceAsBefore() {
        assertNull(book("10분 뒤에 거기로 가자")?.query)
        assertNull(book("그곳으로 3시에 안내해 줘")?.query)
    }

    @Test
    fun notBookings() {
        assertNull(bookingAsk("3시에 문 닫아?", now))
        assertNull(bookingAsk("문화전당 가자", now))
        assertNull(bookingAsk("2시간 걸려?", now))
        assertNull(bookingAsk("내일 문화전당 가자", now)) // no time yet
        assertNull(bookingAsk("병원 예약해 줘", now)) // the team's 못 해요
        assertNull(bookingAsk("내일 3시에 병원 예약해 줘", now))
    }

    @Test
    fun cancelListAndPast() {
        assertEquals(BookingAsk.Cancel, bookingAsk("예약 취소해 줘", now))
        assertEquals(BookingAsk.Cancel, bookingAsk("길 안내 예약 지워 줘", now))
        assertEquals(BookingAsk.List, bookingAsk("예약 뭐 있어?", now))
        assertTrue(bookingAsk("오늘 오전 8시에 시청 가자", now) is BookingAsk.BadTime)
    }

    @Test
    fun words() {
        assertEquals("오늘 오후 3시", whenText(at("2026-11-10T15:00"), now))
        assertEquals("내일 오전 9시 반", whenText(at("2026-11-11T09:30"), now))
        assertEquals("11월 13일 오후 12시 5분", whenText(at("2026-11-13T12:05"), now))
        assertEquals("10분 뒤(오전 10시 15분)에 문화전당 길 안내를 시작할까요?", bookingQuestion(at("2026-11-10T10:15"), "문화전당", now))
        assertEquals("오늘 오후 3시에 시청 길 안내를 시작할까요?", bookingQuestion(at("2026-11-10T15:00"), "시청", now))
    }

    @Test
    fun listAndCancelWords() {
        val b = TripBooking(1, at("2026-11-10T15:00"), "시청", 35.0, 126.0)
        assertEquals("오늘 오후 3시에 시청 길 안내가 예약되어 있어요.", bookingsText(listOf(b), now))
        assertEquals("오늘 오후 3시 시청 길 안내 예약을 취소했어요.", cancelledText(listOf(b), now))
        assertEquals("예약된 길 안내가 없어요.", cancelledText(emptyList(), now))
    }
}
