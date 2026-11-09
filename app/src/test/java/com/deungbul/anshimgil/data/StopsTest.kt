package com.deungbul.anshimgil.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StopsTest {
    private val names = listOf("다이소", "CU", "약국")

    @Test
    fun arrivedSaysWhatIsNext() {
        assertEquals("다이소에 도착했어요. 다음은 CU예요.", stopArrivedText(names, 0, emptySet(), "집"))
        assertEquals("CU에 도착했어요. 다음은 약국이에요.", stopArrivedText(names, 1, setOf(0), "집"))
        assertEquals("약국에 도착했어요. 다음은 집이에요.", stopArrivedText(names, 2, setOf(0, 1), "집"))
        // A skipped stop is not next.
        assertEquals("CU에 도착했어요. 다음은 국립아시아문화전당이에요.", stopArrivedText(names, 1, setOf(0, 2), "국립아시아문화전당"))
    }

    @Test
    fun leftCountsAmongAll() {
        assertEquals("1/3 다이소", stopsLeftText(names, emptySet()))
        assertEquals("2/3 CU", stopsLeftText(names, setOf(0)))
        assertEquals("3/3 약국", stopsLeftText(names, setOf(0, 1)))
        assertNull(stopsLeftText(names, setOf(0, 1, 2)))
    }

    @Test
    fun reachedInOrder() {
        val along = listOf(100.0, 250.0)
        assertNull(stopReached(along, 50.0, emptySet()))
        assertEquals(0, stopReached(along, 85.0, emptySet()))
        // Past the second already but the first is not done: the first.
        assertEquals(0, stopReached(along, 260.0, emptySet()))
        assertNull(stopReached(along, 120.0, setOf(0)))
        assertEquals(1, stopReached(along, 240.0, setOf(0)))
        assertNull(stopReached(along, 300.0, setOf(0, 1)))
    }

    @Test
    fun ieyoFollowsTheLastSound() {
        assertEquals("다이소예요", ieyo("다이소"))
        assertEquals("편의점이에요", ieyo("편의점"))
        assertEquals("CU예요", ieyo("CU"))
    }

    @Test
    fun parsesRouteStopsAndSendsThem() {
        val json = JSONObject(
            """{"routes":[{"kind":"safe","distanceM":500,"durationMin":8,
               "geometry":[[126.0,35.0],[126.002,35.0],[126.004,35.0]],
               "stops":[{"name":"CU","lat":35.0,"lon":126.003,"atM":270},{"name":"다이소","lat":35.0,"lon":126.001,"atM":90}]}]}""",
        )
        val stops = parseRouteResult(json).routes.single().stops
        assertEquals(listOf("다이소", "CU"), stops.map { it.name })
        assertEquals(90.0, stops[0].atM, 0.1)

        val req = RouteRequest(
            LatLon(35.0, 126.0), LatLon(35.0, 126.004), UserMode.SENIOR,
            stops = (1..4).map { StopPlace("곳$it", LatLon(35.0, 126.0 + it / 1000.0)) },
        ).toJson()
        assertEquals(MAX_WALK_STOPS, req.getJSONArray("stops").length())
        assertEquals("곳1", req.getJSONArray("stops").getJSONObject(0).getString("name"))
        assertTrue(!RouteRequest(LatLon(35.0, 126.0), LatLon(35.0, 126.004), UserMode.SENIOR).toJson().has("stops"))
    }

    @Test
    fun alongInOrderKeepsOrderOnAnOutAndBackRoute() {
        // East 200 m, then back west 200 m: a place at 100 m is passed twice.
        val path = listOf(LatLon(35.0, 126.0), LatLon(35.0, 126.0022), LatLon(35.0, 126.0))
        val g = Guidance(path, emptyList())
        val mid = LatLon(35.0, 126.0011)
        val end = LatLon(35.0, 126.0022)
        val along = g.alongInOrder(listOf(end, mid))
        assertEquals(200.0, along[0], 5.0)
        // The second visit to the middle, on the way back.
        assertEquals(300.0, along[1], 5.0)
    }

    @Test
    fun transitStopLegAndFindingTheRestAgain() {
        val json = JSONObject(
            """{"paths":[{"totalMin":30,"transfers":0,"walkM":300,"legs":[
                {"type":"walk","distanceM":200,"minutes":3,"from":{"name":null,"lat":35.0,"lon":126.0},"to":{"name":"다이소","lat":35.0,"lon":126.002}},
                {"type":"stop","to":{"name":"다이소","lat":35.0,"lon":126.002}},
                {"type":"walk","distanceM":100,"minutes":2,"from":{"name":"다이소","lat":35.0,"lon":126.002},"to":{"name":"정류장","lat":35.0,"lon":126.003}},
                {"type":"bus","distanceM":3000,"minutes":12,"lines":["45"],"from":{"name":"정류장","lat":35.0,"lon":126.003},"to":{"name":"시청","lat":35.01,"lon":126.03},
                 "buses":[{"lineName":"45","remainMin":7,"remainStop":3,"lowFloor":true}]},
                {"type":"stop","place":{"name":"CU","lat":35.01,"lon":126.031}}
            ]}]}""",
        )
        val route = parseTransit(json).routes.single()
        assertEquals(LegType.STOP, route.legs[1].type)
        assertEquals("CU", route.legs[4].to.name)
        assertEquals("들르기 · 다이소", legTitle(route.legs[1]))
        assertEquals("다이소에 들러요", stopTitle(route.legs[1]))
        // The bus comes after a stop: how long to wait is not known.
        assertNull(firstWait(route))

        val legs = guideLegs(route)
        assertEquals(listOf("CU"), stopsAfter(legs, 1).map { it.name })
        val rest = TransitRoute(20, null, 0, 0, listOf(route.legs[3]))
        val joined = withRestFoundAgain(legs, 1, rest)
        assertEquals(listOf(LegType.WALK, LegType.STOP, LegType.BUS), joined.map { it.type })
    }
}
