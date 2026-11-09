package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class HabitsTest {
    private val school = LatLon(35.1400, 126.9342)
    private val acc = LatLon(35.1467, 126.9203)
    private val home = LatLon(35.1300, 126.9100)

    // 2026-11-09 is a Monday.
    private fun trip(day: Int, h: Int, m: Int, to: LatLon = school, name: String = "학교", transit: Boolean = false) =
        TripRecord(name, to.lat, to.lon, LocalDateTime.of(2026, 11, day, h, m), transit)

    private val mon_wed = listOf(trip(9, 8, 20), trip(10, 8, 35), trip(11, 8, 30))

    @Test
    fun threeWeekdaysMakeAHabitForThursday() {
        val h = habitNow(mon_wed, LocalDateTime.of(2026, 11, 12, 8, 5), home)!!
        assertEquals("학교", h.place.name)
        assertEquals(LocalTime.of(8, 30), h.time)
        assertEquals(3, h.days)
        val hint = habitHint(h, LocalDate.of(2026, 11, 12))
        assertEquals("보통 8시 반쯤 학교에 가셨어요. 지금 길 안내할까요?", hint.text)
        // Taken down once it is too late for it.
        assertEquals(LocalDateTime.of(2026, 11, 12, 8, 40), hint.until)
    }

    @Test
    fun twoDaysAreNotAHabit() {
        assertNull(habitNow(mon_wed.take(2), LocalDateTime.of(2026, 11, 11, 8, 5), home))
    }

    @Test
    fun onlyAroundTheUsualTime() {
        assertNull(habitNow(mon_wed, LocalDateTime.of(2026, 11, 12, 7, 30), home))  // an hour before
        assertNotNull(habitNow(mon_wed, LocalDateTime.of(2026, 11, 12, 8, 38), home)) // just after
        assertNull(habitNow(mon_wed, LocalDateTime.of(2026, 11, 12, 9, 0), home))   // well past
    }

    @Test
    fun notOnTheWeekendForAWeekdayHabit() {
        assertNull(habitNow(mon_wed, LocalDateTime.of(2026, 11, 14, 8, 10), home)) // Saturday
    }

    @Test
    fun notWhenAlreadyGoneOrAlreadyThere() {
        assertNull(habitNow(mon_wed + trip(12, 7, 50), LocalDateTime.of(2026, 11, 12, 8, 10), home))
        assertNull(habitNow(mon_wed, LocalDateTime.of(2026, 11, 12, 8, 10), school))
    }

    @Test
    fun differentTimesOrPlacesAreNoHabit() {
        // The same place at very different hours.
        assertNull(habitNow(listOf(trip(9, 8, 0), trip(10, 12, 0), trip(11, 18, 0)), LocalDateTime.of(2026, 11, 12, 11, 50), home))
        // Three places at the same hour.
        val spread = listOf(trip(9, 8, 30), trip(10, 8, 30, acc, "문화전당"), trip(11, 8, 30, home, "집"))
        assertNull(habitNow(spread, LocalDateTime.of(2026, 11, 12, 8, 10), LatLon(35.16, 126.85)))
        // Three trips on one day are one day.
        assertNull(habitNow(listOf(trip(10, 8, 20), trip(10, 8, 30), trip(10, 8, 40)), LocalDateTime.of(2026, 11, 12, 8, 10), home))
    }

    @Test
    fun oldTripsAreForgotten() {
        val old = listOf(trip(9, 8, 30), trip(10, 8, 30), trip(11, 8, 30))
        assertNull(habitNow(old, LocalDateTime.of(2026, 11, 30, 8, 10), home))
        assertTrue(keepTrips(old, LocalDateTime.of(2026, 12, 20, 8, 0)).isEmpty())
    }

    @Test
    fun busWhenMostDaysWentByBus() {
        val trips = listOf(trip(9, 17, 0, acc, "문화전당", true), trip(10, 17, 10, acc, "문화전당", true), trip(11, 16, 50, acc, "문화전당"))
        val h = habitNow(trips, LocalDateTime.of(2026, 11, 12, 16, 40), home)!!
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
