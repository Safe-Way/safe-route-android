package com.deungbul.anshimgil.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 결정 57: the team's own cases for `road_report_active` (safe-route scripts/check_report_active.py, 21 checks),
 * run through the app's copy of the rules, so the map shows what the route engine and the server count.
 */
class RoadReportsTest {
    private fun r(id: Long, code: String, lat: Double, lon: Double, day: String, status: String = "open") =
        RoadReport(id, code, code, LatLon(lat, lon), null, status == "confirmed", LocalDate.parse(day).atTime(9, 0), 0)

    // The team's reports; hidden ones never reach the app (the server's list leaves them out), so 902 is not here.
    private val all = listOf(
        r(101, "construction", 35.1000, 126.9000, "2026-11-10"),
        r(201, "construction", 35.1100, 126.9000, "2026-11-10"),
        r(202, "construction", 35.11005, 126.9000, "2026-11-16"),
        r(203, "construction", 35.1100, 126.90005, "2026-11-17"),
        r(301, "construction", 35.1200, 126.9000, "2026-11-10"),
        r(302, "construction", 35.1200, 126.9001, "2026-11-13"),
        r(401, "construction", 35.1300, 126.9000, "2026-11-10"),
        r(402, "construction", 35.1300, 126.9000, "2026-11-18"),
        r(501, "accident", 35.1400, 126.9000, "2026-11-10"),
        r(601, "curb", 35.1500, 126.9000, "2026-11-10"),
        r(701, "construction", 35.1600, 126.9000, "2026-11-10"),
        r(702, "curb", 35.1600, 126.9000, "2026-11-10"),
        r(801, "construction", 35.1700, 126.9000, "2026-11-10"),
        r(802, "construction", 35.17045, 126.9000, "2026-11-10"),
        r(901, "obstacle", 35.1800, 126.9000, "2026-11-10"),
        r(1001, "construction", 35.1900, 126.9000, "2026-11-10"),
        r(1002, "construction", 35.1900, 126.9000, "2026-11-16"),
        r(1003, "construction", 35.1900, 126.9000, "2026-11-16"),
        r(1101, "broken", 35.2000, 126.9000, "2026-11-10", "confirmed"),
    )

    /** (today, spot, null = not shown, or (start, end, count, confirmed)) — the team's EXPECT list. */
    private val expect = listOf(
        Triple("2026-11-16", 101L, Exp("2026-11-10", "2026-11-16", 1, false)),
        Triple("2026-11-17", 101L, null),
        Triple("2026-11-17", 201L, Exp("2026-11-10", "2026-11-18", 3, true)),
        Triple("2026-11-18", 201L, Exp("2026-11-10", "2026-11-18", 3, true)),
        Triple("2026-11-19", 201L, null),
        Triple("2026-11-14", 301L, Exp("2026-11-10", "2026-11-16", 2, true)),
        Triple("2026-11-17", 301L, null),
        Triple("2026-11-17", 401L, null),
        Triple("2026-11-20", 401L, Exp("2026-11-18", "2026-11-24", 1, false)),
        Triple("2026-11-10", 501L, Exp("2026-11-10", "2026-11-10", 1, false)),
        Triple("2026-11-11", 501L, null),
        Triple("2026-12-09", 601L, Exp("2026-11-10", "2026-12-09", 1, false)),
        Triple("2026-12-10", 601L, null),
        Triple("2026-11-12", 701L, Exp("2026-11-10", "2026-11-16", 1, false)),
        Triple("2026-11-12", 702L, Exp("2026-11-10", "2026-12-09", 1, false)),
        Triple("2026-11-12", 801L, Exp("2026-11-10", "2026-11-16", 1, false)),
        Triple("2026-11-12", 802L, Exp("2026-11-10", "2026-11-16", 1, false)),
        Triple("2026-11-10", 901L, Exp("2026-11-10", "2026-11-10", 1, false)),
        Triple("2026-11-17", 1001L, Exp("2026-11-10", "2026-11-17", 3, true)),
        Triple("2026-11-18", 1001L, null),
        Triple("2026-11-12", 1101L, Exp("2026-11-10", "2026-12-09", 1, true)),
    )

    data class Exp(val start: String, val end: String, val count: Int, val confirmed: Boolean)

    /**
     * 역할: 신고된 위험 장소를 언제부터 언제까지 보여 줄지(결정 57)를 서버 팀 사례(check_report_active.py)와 똑같이 계산하는지 확인한다.
     * 앱과 서버가 다르게 계산하면 지도에 보이는 위험 장소와 경로가 피하는 장소가 달라진다.
     */
    @Test
    fun theTeamsCasesForDecision57() {
        val wrong = expect.mapNotNull { (today, spot, want) ->
            val got = activeSpots(all, LocalDate.parse(today)).firstOrNull { it.spotId == spot }
                ?.let { Exp(it.startsOn.toString(), it.endsOn.toString(), it.count, it.confirmed) }
            if (got == want) null else "$today $spot: 기대 $want, 나옴 $got"
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun aSpotIsDrawnAtItsFirstReport() {
        val spot = activeSpots(all, LocalDate.parse("2026-11-17")).first { it.spotId == 201L }
        assertEquals(LatLon(35.1100, 126.9000), spot.point)
    }

    @Test
    fun readsTheServersList() {
        val json = JSONObject(
            """{"reports":[{"id":7,"type":"curb","typeName":"턱","mode":"senior","memo":null,"lat":35.14,"lon":126.93,
            "status":"confirmed","createdAt":"2026-11-10T14:40","photoUrls":["/api/reports/7/photos/1"]}],"truncated":false}""",
        )
        val list = parseReports(json)
        assertEquals("curb", list[0].code)
        assertEquals("턱", list[0].label)
        assertEquals(LocalDateTime.of(2026, 11, 10, 14, 40), list[0].createdAt)
        assertEquals(1, list[0].photoCount)
        assertTrue(list[0].teamConfirmed)
        assertNull(list[0].memo)
    }

    @Test
    fun wordsForTheMapTheCardAndTheVoice() {
        val spot = activeSpots(
            listOf(
                r(1, "construction", 35.1, 126.9, "2026-11-10").copy(label = "공사", createdAt = LocalDateTime.of(2026, 11, 10, 8, 21)),
                r(2, "construction", 35.1, 126.9, "2026-11-10").copy(label = "공사", createdAt = LocalDateTime.of(2026, 11, 10, 8, 21)),
            ),
            LocalDate.of(2026, 11, 10),
        ).single()
        assertEquals("공사 2", reportSpotTag(spot))
        assertEquals("공사 신고 2건 · 마지막 신고 20분 전 · 확인됨", reportSpotTitle(spot, LocalDateTime.of(2026, 11, 10, 8, 41)))
        assertEquals("11월 16일까지 지도에 보여요.", reportSpotUntil(spot))
        assertEquals("앞에 공사 신고가 있는 곳을 지나요.", reportAheadText(spot, 60))
    }

    @Test
    fun toldOnlyWhenAheadAndNearTheRoute() {
        val spot = activeSpots(listOf(r(1, "construction", 35.1, 126.9, "2026-11-10")), LocalDate.of(2026, 11, 10)).single()
        fun at(along: Double, off: Double) = reportSpotsAhead(listOf(spot), progressM = 200.0) { along to off }
        assertEquals(60, at(260.0, 10.0).single().second)
        assertTrue(at(150.0, 10.0).isEmpty()) // behind
        assertTrue(at(350.0, 10.0).isEmpty()) // too far ahead yet
        assertTrue(at(260.0, 45.0).isEmpty()) // not on this road
    }

    @Test
    fun readsTheServersActiveSpots() {
        // GET /api/reports/active (server #151), the view's rows.
        val json = JSONObject(
            """{"spots":[{"spotId":3,"reportType":"construction","lat":35.14015,"lon":126.93425,"startsOn":"2026-11-10",
            "endsOn":"2026-11-16","reportCount":2,"photoCount":0,"confirmed":true,"avoidModes":["adult","child","senior","wheelchair"],
            "costModes":[],"lastAt":"2026-11-10T08:21:00","lastMemo":"[시험] 지도 표시 확인용"}],"truncated":false}""",
        )
        val spot = parseActiveSpots(json).single()
        assertEquals(3L, spot.spotId)
        assertEquals("공사", spot.label)
        assertEquals(2, spot.count)
        assertTrue(spot.confirmed)
        assertEquals(LocalDate.of(2026, 11, 16), spot.endsOn)
        assertEquals("[시험] 지도 표시 확인용", spot.lastMemo)
        assertEquals("공사 신고 2건 · 마지막 신고 20분 전 · 확인됨", reportSpotTitle(spot, LocalDateTime.of(2026, 11, 10, 8, 41)))
    }
}
