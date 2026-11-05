package com.deungbul.anshimgil.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubwayTest {
    private val json = JSONObject(
        """{"basis":"timetable","stations":[{"name":"남광주","lat":35.139,"lon":126.922,"trains":[
            {"direction":"평동행","inMin":0,"stopsAway":1},
            {"direction":"녹동행","inMin":6,"stopsAway":4},
            {"direction":"평동행","inMin":10,"stopsAway":null},
            {"direction":"녹동행","inMin":16,"stopsAway":10}]}]}""",
    )

    @Test
    fun nextTrainEachWaySoonestFirst() {
        val station = matchStation("남광주역", parseSubwayNearby(json))!!
        assertEquals(listOf("평동행 · 곧 도착 (1정거장 전)", "녹동행 · 6분 뒤 (4정거장 전)"), nextTrainLines(station.trains))
    }

    @Test
    fun stationNamesMatchWithOrWithoutStationSuffix() {
        val stations = parseSubwayNearby(json)
        assertEquals("남광주", matchStation("남광주", stations)?.name)
        assertNull(matchStation("학동증심사입구역", stations))
    }
}
