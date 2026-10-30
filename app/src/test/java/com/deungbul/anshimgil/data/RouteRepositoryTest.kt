package com.deungbul.anshimgil.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRepositoryTest {
    @Test
    fun pointAlong_halfway_isMiddleOfStraightLine() {
        val path = listOf(LatLon(35.0, 126.0), LatLon(35.002, 126.0))
        val mid = pointAlong(path, 0.5)
        assertEquals(35.001, mid.lat, 1e-6)
        assertEquals(126.0, mid.lon, 1e-6)
    }

    @Test
    fun fakeRoute_safeIsLongerAndExplained() = runBlocking {
        val result = FakeRouteRepository().findRoutes(
            RouteRequest(LatLon(35.1404, 126.9318), LatLon(35.1372, 126.9322), UserMode.CHILD),
        )
        val safe = result.route(RouteType.SAFE)!!
        val shortest = result.route(RouteType.SHORTEST)!!
        assertTrue(safe.distanceM > shortest.distanceM)
        assertTrue(result.explanation.summary.startsWith("${safe.distanceM - shortest.distanceM}m"))
        assertEquals(3, result.explanation.items.size)
    }

    @Test
    fun parseRouteResult_readsProvisionalShape() {
        val json = JSONObject(
            """
            {"conditions":[{"key":"night","label":"밤","on":true}],
             "routes":[{"type":"safe","distanceM":2100,"durationMin":27,"path":[[35.1,126.9],[35.2,126.9]]}],
             "explanation":{"summary":"120m 더","items":[{"label":"CCTV","shortest":3,"safe":9,"unit":"대"}]},
             "facilities":[{"kind":"cctv","lat":35.1,"lon":126.9},{"kind":"unknown","lat":0,"lon":0}],
             "dataQuality":{"ok":false,"message":"데이터 부족"}}
            """.trimIndent(),
        )
        val result = parseRouteResult(json)
        assertEquals(RouteType.SAFE, result.routes.single().type)
        assertEquals(2, result.routes.single().path.size)
        assertEquals(1, result.facilities.size)
        assertEquals("데이터 부족", result.dataQuality.message)
    }
}
