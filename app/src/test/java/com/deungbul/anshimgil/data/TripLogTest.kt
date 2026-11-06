package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TripLogTest {
    private val asiaCulture = Place("1", "국립아시아문화전당", "광주 동구 문화전당로 38", 35.146734, 126.920318, null)

    @Test
    fun startBodyRoundsTheDestinationAndHasNoStart() {
        val json = tripStartJson("guest-1", asiaCulture, UserMode.DISABLED, transit = false, routeKind = "safe", weather = "rain", night = false, plannedMin = 12)
        assertEquals("guest-1", json.getString("guestId"))
        assertEquals("국립아시아문화전당", json.getString("toName"))
        assertEquals(35.147, json.getDouble("toLat"), 0.0)
        assertEquals(126.920, json.getDouble("toLon"), 0.0)
        assertEquals("wheelchair", json.getString("mode"))
        assertEquals("walk", json.getString("travel"))
        assertEquals("safe", json.getString("routeKind"))
        assertEquals(12, json.getInt("plannedMin"))
        // Never the start or the path.
        assertFalse(json.has("origin") || json.has("fromLat") || json.has("path") || json.has("geometry"))
        // Rounding moves the point by at most about 60 m.
        assert(metersBetween(LatLon(asiaCulture.lat, asiaCulture.lon), LatLon(json.getDouble("toLat"), json.getDouble("toLon"))) < 80)
    }

    @Test
    fun transitHasNoRouteKind() {
        val json = tripStartJson("g", asiaCulture, UserMode.SENIOR, transit = true, routeKind = null, weather = "clear", night = true, plannedMin = null)
        assertEquals("transit", json.getString("travel"))
        assert(json.isNull("routeKind") && json.isNull("plannedMin"))
        assert(json.getBoolean("night"))
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
