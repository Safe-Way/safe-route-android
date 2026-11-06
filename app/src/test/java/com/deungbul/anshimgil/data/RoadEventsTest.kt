package com.deungbul.anshimgil.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadEventsTest {
    // A straight walk east about 900 m along one street.
    private val path = listOf(LatLon(35.1460, 126.9150), LatLon(35.1460, 126.9250))
    private val guidance = Guidance(path, emptyList())
    private fun event(id: Long, lat: Double, lon: Double, radius: Int = 100, kind: String = "공사") =
        RoadEvent(id, kind, "보수작업", null, lat, lon, radius, null)

    @Test
    fun parsesServerEvents() {
        val json = JSONObject(
            """{"events":[{"id":9,"kind":"행사","detail":"축제","title":"금남로 차 없는 거리 걷자잉","place":"금남로공원",
               "lat":35.1461414,"lon":126.9230951,"radiusM":300,"startsAt":"2026-04-04T00:00","endsAt":"2026-11-07T23:59",
               "message":"놀자잉","url":null},
               {"id":10,"kind":"공사","title":"","lat":35.1,"lon":126.9,"radiusM":100}]}""",
        )
        // the second has no title, so it is left out
        assertEquals(listOf(RoadEvent(9, "행사", "금남로 차 없는 거리 걷자잉", "금남로공원", 35.1461414, 126.9230951, 300, "놀자잉")), parseRoadEvents(json))
    }

    @Test
    fun eventOnTheRoadAheadIsFoundWithDistance() {
        // About 640 m along the street, 30 m to the north.
        val ahead = eventsAhead(guidance, progressM = 100.0, events = listOf(event(1, 35.14627, 126.9220)), known = emptySet()).single()
        assertTrue(ahead.aheadM in 520..560)
        assertTrue(eventAheadText(ahead).startsWith("앞 5"))
        assertTrue(eventAheadText(ahead).contains("공사가 있어요"))
    }

    @Test
    fun eventsBehindFarOffOrKnownAreLeftOut() {
        val behind = event(1, 35.1460, 126.9160)   // about 90 m from the start
        val far = event(2, 35.1500, 126.9200)      // 440 m north of the street
        val known = event(3, 35.1460, 126.9220)
        assertTrue(eventsAhead(guidance, 400.0, listOf(behind, far, known), known = setOf(3L)).isEmpty())
    }

    @Test
    fun walkerAlreadyInsideTheCircleHearsNearHere() {
        val ahead = eventsAhead(guidance, 300.0, listOf(event(1, 35.1460, 126.9183, radius = 300)), emptySet()).single()
        assertEquals(0, ahead.aheadM)
        assertTrue(eventAheadText(ahead).startsWith("이 근처에"))
    }

    @Test
    fun passesWithinIsNullWhenTheRouteKeepsClear() {
        assertNull(guidance.passesWithin(LatLon(35.1500, 126.9200), 100.0, 0.0))
    }
}
