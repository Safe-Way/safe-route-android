package com.deungbul.anshimgil.data.user

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import com.deungbul.anshimgil.data.place.Place
import com.deungbul.anshimgil.data.route.LatLon

class HabitsTest {
    private val school = LatLon(35.1400, 126.9342)
    private val acc = LatLon(35.1467, 126.9203)
    private val home = LatLon(35.1300, 126.9100)

    // Mondays: 2026-11-02, 11-09, 11-16, 11-23. Habits follow the team server (trip_habit, TodayService).
    private fun trip(day: Int, h: Int, m: Int, to: LatLon = school, name: String = "학교", transit: Boolean = false) =
        TripRecord(name, to.lat, to.lon, LocalDateTime.of(2026, 11, day, h, m), transit)

    private val mondays = listOf(trip(2, 8, 30), trip(9, 8, 35), trip(16, 8, 50))

    @Test
    fun threeTimesSameWeekdaySameSlotMakeAHabit() {
        val h = habitNow(mondays, LocalDateTime.of(2026, 11, 23, 7, 30), home)!!
        assertEquals("학교", h.place.name)
        assertEquals(LocalTime.of(8, 30), h.time)
        assertEquals(3, h.days)
        val hint = habitHint(h, LocalDate.of(2026, 11, 23))
        assertEquals("보통 8시 반쯤 학교에 가셨어요. 지금 길 안내할까요?", hint.text)
        // Taken down 30 minutes after its slot (TodayService: 30 minutes before now to 90 after).
        assertEquals(LocalDateTime.of(2026, 11, 23, 9, 0), hint.until)
    }

    @Test
    fun twoTimesAreNotAHabit() {
        assertNull(habitNow(mondays.take(2), LocalDateTime.of(2026, 11, 23, 8, 0), home))
    }

    @Test
    fun onlyFrom90MinutesBeforeTo30After() {
        assertNull(habitNow(mondays, LocalDateTime.of(2026, 11, 23, 6, 50), home))     // 100 minutes before
        assertNotNull(habitNow(mondays, LocalDateTime.of(2026, 11, 23, 7, 0), home))  // 90 minutes before
        assertNotNull(habitNow(mondays, LocalDateTime.of(2026, 11, 23, 9, 0), home))  // 30 minutes after
        assertNull(habitNow(mondays, LocalDateTime.of(2026, 11, 23, 9, 10), home))    // later
    }

    @Test
    fun otherWeekdaysAreOtherHabits() {
        // Monday, Tuesday and Wednesday are three weekdays, not three Mondays.
        val week = listOf(trip(9, 8, 30), trip(10, 8, 30), trip(11, 8, 30))
        assertNull(habitNow(week, LocalDateTime.of(2026, 11, 12, 8, 0), home))
        assertNull(habitNow(mondays, LocalDateTime.of(2026, 11, 24, 8, 0), home)) // a Tuesday
    }

    @Test
    fun notWhenAlreadyGoneOrAlreadyThere() {
        assertNull(habitNow(mondays + trip(23, 7, 50), LocalDateTime.of(2026, 11, 23, 8, 10), home))
        assertNull(habitNow(mondays, LocalDateTime.of(2026, 11, 23, 8, 10), school))
    }

    @Test
    fun anotherSlotOrPlaceIsAnotherHabit() {
        // 8:20 is the 8:00 slot, 8:30 the 8:30 one.
        assertNull(habitNow(listOf(trip(2, 8, 20), trip(9, 8, 30), trip(16, 8, 40)), LocalDateTime.of(2026, 11, 23, 8, 0), home))
        // Three places in the same slot.
        val spread = listOf(trip(2, 8, 30), trip(9, 8, 30, acc, "문화전당"), trip(16, 8, 30, home, "집"))
        assertNull(habitNow(spread, LocalDateTime.of(2026, 11, 23, 8, 10), LatLon(35.16, 126.85)))
    }

    @Test
    fun oldTripsAreForgottenOnThePhone() {
        assertTrue(keepTrips(mondays, LocalDateTime.of(2026, 12, 20, 8, 0)).isEmpty())
    }

    @Test
    fun busWhenMostTimesWentByBus() {
        val trips = listOf(trip(2, 17, 0, acc, "문화전당", true), trip(9, 17, 10, acc, "문화전당", true), trip(16, 17, 20, acc, "문화전당"))
        val h = habitNow(trips, LocalDateTime.of(2026, 11, 23, 16, 40), home)!!
        assertTrue(h.transit)
        assertEquals(LocalTime.of(17, 0), h.time)
    }

    @Test
    fun timesSaidTheKoreanWay() {
        assertEquals("8시 반", sayTime(LocalTime.of(8, 30)))
        assertEquals("5시", sayTime(LocalTime.of(17, 0)))
        assertEquals("2시 10분", sayTime(LocalTime.of(14, 10)))
        assertEquals("12시", sayTime(LocalTime.of(0, 0)))
    }

    @Test
    fun calendarLineNamesThePlaceOnce() {
        val e = CalendarEvent(1, "치과 예약", "광주 동구 밝은치과 2층", LocalDateTime.of(2026, 11, 12, 15, 0))
        assertEquals("3시에 '치과 예약' 일정이 있어요 (밝은치과). 길 찾아 드릴까요?", calendarText(e, Place(null, "밝은치과", "", 0.0, 0.0, null)))
        val e2 = e.copy(title = "밝은치과")
        assertEquals("3시에 '밝은치과' 일정이 있어요. 길 찾아 드릴까요?", calendarText(e2, Place(null, "밝은치과", "", 0.0, 0.0, null)))
    }
}
