package com.deungbul.anshimgil.data

// 시험에 쓰는 도구들
import org.json.JSONObject              // 서버 답(JSON)을 코드로 만들 때 씀
import org.junit.Assert.assertEquals    // assertEquals(기대값, 실제값): 둘이 같아야 통과
import org.junit.Assert.assertNull      // assertNull(값): 값이 null(없음)이어야 통과
import org.junit.Assert.assertTrue      // assertTrue(조건): 조건이 참이어야 통과
import org.junit.Test                   // @Test가 붙은 함수 하나하나가 시험 하나
import java.time.LocalDate              // 날짜(연·월·일)
import java.time.LocalDateTime          // 날짜와 시각(연·월·일·시·분)

/**
 * 결정 57: the team's own cases for `road_report_active` (safe-route scripts/check_report_active.py, 21 checks),
 * run through the app's copy of the rules, so the map shows what the route engine and the server count.
 *
 * (한국어 설명) 사용자가 신고한 위험 장소(공사, 턱, 사고 등)를 지도에 언제부터 언제까지 보여 줄지 시험한다.
 *
 * 앱은 보통 서버(GET /api/reports/active)가 계산해 준 "지금 유효한 신고 장소" 목록을 그대로 받는다.
 * 그 주소가 없는 옛 서버일 때만, 신고 목록(GET /api/reports)을 받아 앱의 activeSpots()로 직접 계산한다.
 * 그래서 activeSpots()는 서버 팀의 규칙(결정 57)과 한 줄 한 줄 똑같이 계산해야 한다.
 *
 * 결정 57 규칙 (앱 RoadReports.kt의 reportDays(), activeSpots()에서 확인):
 * - 같은 종류의 신고가 30m(REPORT_SPOT_M) 안에 있으면 같은 장소로 본다. 장소 번호는 그 안에서 가장 작은 신고 번호다.
 * - 보여 주는 기간은 첫 신고 날을 1일째로 센다: 막는 물건·사고 1일, 공사 7일, 턱·급경사·보도 파손 30일, 기타 7일.
 * - 공사만: 기간의 마지막 날에 또 신고가 오면 하루 늘어난다.
 * - 기간이 끝난 뒤에 온 신고는 그날부터 새 기간을 시작한다.
 * - 이번 기간 신고가 2건 이상이거나, 팀이 확인한(status=confirmed) 신고가 있으면 "확인됨"이다.
 *
 * 여기서는 진짜 서버를 부르지 않는다. 신고 목록을 직접 만들어 activeSpots()에 넣고,
 * 날짜별로 나온 결과가 서버 팀이 적어 둔 기대값과 같은지 본다.
 * activeSpots()는 그날 보여 줄 장소(ReportSpot) 목록을 돌려준다.
 * 장소마다 번호(spotId), 시작일(startsOn), 끝나는 날(endsOn), 이번 기간 신고 수(count), 확인됨(confirmed)이 있다.
 */
class RoadReportsTest {
    /**
     * 가짜 신고 하나를 짧게 만드는 도우미 함수.
     * id = 신고 번호, code = 종류(construction 공사, curb 턱, accident 사고, obstacle 막는 물건, broken 보도 파손),
     * lat·lon = 위치, day = 신고한 날(그날 오전 9시로 넣는다), status = "confirmed"면 팀이 확인한 신고.
     * 이름(label)은 종류 글자를 그대로 쓰고, 메모는 없음(null), 사진은 0장으로 넣는다.
     * 예: r(101, "construction", 35.1, 126.9, "2026-11-10") → 11월 10일 오전 9시에 들어온 공사 신고 101번
     */
    private fun r(id: Long, code: String, lat: Double, lon: Double, day: String, status: String = "open") =
        RoadReport(id, code, code, LatLon(lat, lon), null, status == "confirmed", LocalDate.parse(day).atTime(9, 0), 0)

    // The team's reports; hidden ones never reach the app (the server's list leaves them out), so 902 is not here.
    // (한국어) 서버 팀 사례에 나오는 신고 전체. 한 줄이 신고 하나다: r(번호, 종류, 위도, 경도, 신고한 날[, 확인 여부]).
    // 끝 두 자리를 뺀 앞 번호가 같은 신고(201·202·203, 1001·1002·1003처럼)는 한 사례로, 서로 가까운 곳에 있다.
    // 같은 사례라도 종류가 다르거나(701 공사·702 턱) 30m보다 멀면(801·802는 약 50m) 다른 장소로 센다.
    // 숨긴 신고(902)는 서버가 보내지 않아서 없다.
    private val all = listOf(
        r(101, "construction", 35.1000, 126.9000, "2026-11-10"), // 예: 11/10 공사 신고 하나뿐 → 11/10~11/16(7일) 동안 보임
        r(201, "construction", 35.1100, 126.9000, "2026-11-10"), // 예: 201~203은 서로 30m 안의 공사 신고 → 한 장소(201번)
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
    // (한국어) 서버 팀의 기대값 목록. 한 줄이 확인 하나다: Triple(오늘 날짜, 장소 번호, 기대 결과).
    // 기대 결과가 null이면 그날 지도에 그 장소가 없어야 한다.
    // Exp(시작일, 끝나는 날, 신고 수, 확인됨)이면 그 값 그대로 나와야 한다.
    private val expect = listOf(
        Triple("2026-11-16", 101L, Exp("2026-11-10", "2026-11-16", 1, false)), // 예: 11/16(7일째)에는 아직 보이고, 신고 1건이라 확인됨 아님
        Triple("2026-11-17", 101L, null),                                       // 예: 11/17(8일째)에는 기간이 끝나 안 보임
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

    /** 기대 결과 한 묶음: 시작일, 끝나는 날(이날까지 보임), 이번 기간 신고 수, 확인됨 여부. 날짜는 "2026-11-10" 같은 글자로 적는다. */
    data class Exp(val start: String, val end: String, val count: Int, val confirmed: Boolean)

    /**
     * 역할: 신고된 위험 장소를 언제부터 언제까지 보여 줄지(결정 57)를 서버 팀 사례(check_report_active.py)와 똑같이 계산하는지 확인한다.
     * 앱과 서버가 다르게 계산하면 지도에 보이는 위험 장소와 경로가 피하는 장소가 달라진다.
     */
    @Test
    fun theTeamsCasesForDecision57() {
        // 기대값 목록을 한 줄씩 돌며, 틀린 것만 글로 모은다(맞으면 null이라 빠진다).
        val wrong = expect.mapNotNull { (today, spot, want) ->
            // 그날(today) 기준으로 activeSpots()를 계산하고, 그중 이 장소 번호(spot)를 찾는다. 없으면 null.
            val got = activeSpots(all, LocalDate.parse(today)).firstOrNull { it.spotId == spot }
                // 찾았으면 기대값과 비교하기 쉽게 같은 모양(Exp)으로 바꾼다: 시작일, 끝나는 날, 신고 수, 확인됨.
                ?.let { Exp(it.startsOn.toString(), it.endsOn.toString(), it.count, it.confirmed) }
            // 기대값과 같으면 null(문제 없음), 다르면 "날짜 장소: 기대 …, 나옴 …" 글을 남긴다.
            if (got == want) null else "$today $spot: 기대 $want, 나옴 $got"
        }
        // 틀린 것이 하나도 없어야 통과. 실패하면 틀린 줄을 모두 한 번에 보여 준다.
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    /** 역할: 여러 신고가 묶인 장소는 가장 먼저(가장 작은 번호로) 신고된 자리에 그려지는지 확인한다. */
    @Test
    fun aSpotIsDrawnAtItsFirstReport() {
        val spot = activeSpots(all, LocalDate.parse("2026-11-17")).first { it.spotId == 201L }
        assertEquals(LatLon(35.1100, 126.9000), spot.point)
    }

    /** 역할: 서버의 신고 목록 답(GET /api/reports)에서 종류, 이름, 신고 시각, 사진 수, 팀 확인, 메모를 제대로 읽는지 확인한다. */
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

    /** 역할: 신고 장소를 지도 꼬리표, 눌렀을 때 카드, 보이는 기한, 길안내 음성에서 어떤 말로 보여 주는지 확인한다. */
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

    /** 역할: 길안내 중 신고 장소가 경로 앞쪽 가까이(100m 안, 경로에서 30m 안)에 있을 때만 알려 주는지 확인한다. */
    @Test
    fun toldOnlyWhenAheadAndNearTheRoute() {
        val spot = activeSpots(listOf(r(1, "construction", 35.1, 126.9, "2026-11-10")), LocalDate.of(2026, 11, 10)).single()
        fun at(along: Double, off: Double) = reportSpotsAhead(listOf(spot), progressM = 200.0) { along to off }
        assertEquals(60, at(260.0, 10.0).single().second)
        assertTrue(at(150.0, 10.0).isEmpty()) // behind
        assertTrue(at(350.0, 10.0).isEmpty()) // too far ahead yet
        assertTrue(at(260.0, 45.0).isEmpty()) // not on this road
    }

    /** 역할: 서버가 계산해 준 유효 신고 장소 답(GET /api/reports/active)을 제대로 읽는지 확인한다. */
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
