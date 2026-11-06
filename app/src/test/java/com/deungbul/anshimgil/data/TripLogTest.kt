package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class TripLogTest {
    private val asiaCulture = Place("1", "국립아시아문화전당", "광주 동구 문화전당로 38", 35.146734, 126.920318, null)

    @Test
    fun destinationIsRoundedToAbout100m() {
        val trip = tripRecord(asiaCulture, LocalDateTime.of(2026, 11, 9, 8, 25, 41), transit = false, mode = UserMode.DISABLED, weather = "rain")
        assertEquals(35.147, trip.destLat, 0.0)
        assertEquals(126.920, trip.destLon, 0.0)
        assertEquals("2026-11-09T08:25", trip.startedAt)
        assertEquals("walk", trip.by)
        // Rounding moves the point by at most about 60 m.
        assert(metersBetween(LatLon(asiaCulture.lat, asiaCulture.lon), LatLon(trip.destLat, trip.destLon)) < 80)
    }

    @Test
    fun recordGoesAndComesBackTheSame() {
        val trip = tripRecord(asiaCulture, LocalDateTime.of(2026, 11, 9, 8, 25), transit = true, mode = UserMode.SENIOR, weather = "clear")
        assertEquals(trip, TripRecord.decode(trip.encode()))
        assertNull(TripRecord.decode("not json"))
        val json = tripJson("guest-1", trip)
        assertEquals("guest-1", json.getString("guestId"))
        assertEquals("transit", json.getString("by"))
        assertEquals("senior", json.getString("mode"))
        // Never the start or the path.
        assertFalse(json.has("origin") || json.has("path") || json.has("geometry"))
    }

    @Test
    fun weatherWord() {
        val base = RouteResult(emptyList(), emptyList(), Explanation("", emptyList()), emptyList(), DataQuality(true, null))
        assertEquals("rain", tripWeather(base.copy(raining = true)))
        assertEquals("heat", tripWeather(base.copy(weather = listOf("폭염", "33°C"))))
        assertEquals("clear", tripWeather(base))
        assertEquals("clear", tripWeather(null))
    }
}
