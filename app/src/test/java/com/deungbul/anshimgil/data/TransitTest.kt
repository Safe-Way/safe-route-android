package com.deungbul.anshimgil.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransitTest {
    /** A real /api/transit answer (조선대 → 시청 쪽), trimmed to one route. */
    private val sample = JSONObject(javaClass.getResource("/transit_sample.json")!!.readText())

    @Test
    fun readsRouteLegsAndBuses() {
        val route = parseTransit(sample).routes.single()
        assertEquals(57, route.totalMin)
        assertEquals(1250, route.fare)
        assertEquals(listOf(LegType.WALK, LegType.BUS, LegType.WALK), route.legs.map { it.type })
        val bus = route.legs[1]
        assertEquals(listOf("45"), bus.lines)
        assertEquals("조선대해오름관", bus.from.name)
        assertEquals("45번 · 5분 뒤 (3정거장 전)", nextBusText(bus.buses))
        assertEquals("45번 버스 · 조선대해오름관 → 시청광주청사 · 32정거장", legTitle(bus))
        // Walking parts come with the line to draw, in lat/lon order.
        val walk = route.legs[0]
        assertEquals(3, walk.path.size)
        assertEquals(35.0, walk.path.first().lat, 1.0)
    }

    @Test
    fun noBusInfoMeansNoLine() {
        assertNull(nextBusText(emptyList()))
        assertEquals("1번 · 곧 도착 · 저상버스", nextBusText(listOf(BusComing("1", 0, 0, true))))
    }
}
