package com.deungbul.anshimgil.data.route

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteEventsTest {
    @Test
    fun eventsComeWithTheRouteAndAreSaidPlainly() {
        val json = JSONObject(
            """
            {"routes":[{"kind":"safe","distanceM":1200,"durationMin":18,
              "geometry":[[126.92,35.14],[126.93,35.15]],
              "events":[
                {"kind":"행사","title":"금남로 차 없는 거리 걷자잉","place":"금남로","message":null,"endsAt":"2026-11-07 23:59",
                 "fromM":420,"toM":780,"avoid":true,"near":null},
                {"kind":"공사","title":"충장로 보도 공사","place":null,"message":null,"endsAt":null,
                 "fromM":0,"toM":120,"avoid":false,"near":"start"}
              ]}]}
            """.trimIndent(),
        )
        val events = parseRouteResult(json).routes.single().events
        assertEquals(2, events.size)
        assertEquals("420m쯤 가면 행사 '금남로 차 없는 거리 걷자잉' 근처를 지나요. 붐비거나 막힐 수 있으니 조심하세요. (23:59 까지)", eventLine(events[0]))
        assertEquals("출발하는 곳 근처에 공사 '충장로 보도 공사'이(가) 있어요.", eventLine(events[1]))
    }
}
