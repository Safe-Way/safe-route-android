package com.deungbul.anshimgil.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RoadReportsTest {
    private val now = LocalDateTime.of(2026, 11, 10, 15, 0)
    private var nextId = 1L
    private fun r(
        type: ReportType = ReportType.CONSTRUCTION,
        northM: Double = 0.0,
        hoursAgo: Long = 1,
        reporter: String? = null,
        confirmed: Boolean = false,
    ) = RoadReport(nextId++, type, LatLon(35.14 + northM / 111_320.0, 126.93), null, confirmed, now.minusHours(hoursAgo), 0, reporter)

    @Test
    fun twoPeopleShow() {
        val spots = reportSpots(listOf(r(reporter = "a"), r(northM = 10.0, reporter = "b")), now)
        assertEquals(1, spots.size)
        assertEquals(2, spots[0].count)
    }

    @Test
    fun onePersonTwiceDoesNotShowButThreeTimesDoes() {
        assertTrue(reportSpots(listOf(r(reporter = "a"), r(reporter = "a")), now).isEmpty())
        assertEquals(1, reportSpots(listOf(r(reporter = "a"), r(reporter = "a"), r(reporter = "a")), now).size)
        // Two of one person and one of another: two people.
        assertEquals(1, reportSpots(listOf(r(reporter = "a"), r(reporter = "a"), r(reporter = "b")), now).size)
    }

    @Test
    fun thisPhonesOwnReportsAreOnePerson() {
        val mine = listOf(r(), r())
        assertTrue(reportSpots(mine, now, mine = mine.map { it.id }.toSet()).isEmpty())
    }

    @Test
    fun oneReportAloneDoesNotShowUnlessConfirmed() {
        assertTrue(reportSpots(listOf(r()), now).isEmpty())
        assertEquals(1, reportSpots(listOf(r(confirmed = true)), now).size)
    }

    @Test
    fun olderThanADayDropsOff() {
        assertTrue(reportSpots(listOf(r(reporter = "a", hoursAgo = 25), r(reporter = "b")), now).isEmpty())
    }

    @Test
    fun farApartOrOtherKindsAreSeparate() {
        assertTrue(reportSpots(listOf(r(reporter = "a"), r(northM = 80.0, reporter = "b")), now).isEmpty())
        assertTrue(reportSpots(listOf(r(reporter = "a"), r(type = ReportType.CURB, reporter = "b")), now).isEmpty())
    }

    @Test
    fun readsTheServersList() {
        val json = JSONObject(
            """{"reports":[{"id":7,"type":"curb","typeName":"턱","mode":"senior","memo":null,"lat":35.14,"lon":126.93,
            "status":"open","createdAt":"2026-11-10T14:40","photoUrls":["/api/reports/7/photos/1"]}],"truncated":false}""",
        )
        val list = parseReports(json)
        assertEquals(ReportType.CURB, list[0].type)
        assertEquals(LocalDateTime.of(2026, 11, 10, 14, 40), list[0].createdAt)
        assertEquals(1, list[0].photoCount)
        assertEquals(null, list[0].memo)
    }

    @Test
    fun wordsForTheMapAndTheVoice() {
        val spot = reportSpots(listOf(r(reporter = "a", hoursAgo = 0), r(reporter = "b", hoursAgo = 0)), now.plusMinutes(20))[0]
        assertEquals("공사 2", reportSpotTag(spot))
        assertEquals("공사 신고 2건 · 마지막 신고 20분 전", reportSpotTitle(spot, now.plusMinutes(20)))
        assertEquals("앞 60m에 공사 신고가 2건 있어요. 조심해서 가세요.", reportAheadText(spot, 60))
    }
}
