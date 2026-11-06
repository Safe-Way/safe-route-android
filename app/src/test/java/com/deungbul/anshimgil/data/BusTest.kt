package com.deungbul.anshimgil.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class BusTest {
    private val json = JSONObject(
        """{"stops":[{"name":"조선대해오름관","nextStop":"종점","lat":35.1401,"lon":126.9319,"distanceM":31,"arrivals":[
            {"lineName":"27","remainMin":5,"remainStop":2,"lowFloor":false},
            {"lineName":"45","remainMin":3,"remainStop":2,"lowFloor":true},
            {"lineName":"03","remainMin":4,"remainStop":3,"lowFloor":true}]}]}""",
    )

    @Test
    fun readsStopsAndBuses() {
        val stop = parseBusNearby(json).single()
        assertEquals("조선대해오름관", stop.name)
        assertEquals(31, stop.distanceM)
        assertEquals(3, stop.buses.size)
    }

    @Test
    fun lowFloorBusesComeFirstForWheelchairAndSenior() {
        val buses = parseBusNearby(json).single().buses
        assertEquals(listOf("45", "03", "27"), busesToShow(buses, UserMode.DISABLED).map { it.line })
        assertEquals(listOf("45", "03", "27"), busesToShow(buses, UserMode.GENERAL).map { it.line })
        val late = buses.map { if (it.line == "27") it.copy(inMin = 1) else it }
        assertEquals("27", busesToShow(late, UserMode.GENERAL).first().line)
        assertEquals("45", busesToShow(late, UserMode.SENIOR).first().line)
    }
}
