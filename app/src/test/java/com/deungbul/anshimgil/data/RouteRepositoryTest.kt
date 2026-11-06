package com.deungbul.anshimgil.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun parseRouteResult_readsServerShape() {
        val json = JSONObject(
            """
            {"conditions":{"night":true,"nightSource":"auto","sunset":"17:41"},
             "routes":[
               {"kind":"shortest","distanceM":1028,"durationMin":13,"geometry":[[126.91,35.14],[126.92,35.15]],
                "counts":{"light":10,"cctv":3,"bell":0,"help":1}},
               {"kind":"safe","distanceM":1244,"durationMin":16,"geometry":[[126.91,35.14],[126.90,35.15],[126.92,35.15]],
                "counts":{"light":43,"cctv":25,"bell":0,"help":2}}],
             "explanation":{"text":"216m(3분) 더 돌지만 가로등 +33개, CCTV +22대","extraM":216,"extraMin":3,
                "diff":{"light":33,"cctv":22,"bell":0,"help":1}},
             "facilities":[{"type":"CCTV","lat":35.14,"lon":126.91,"on":"both"},
               {"type":"CHILD_SAFETY_HOUSE","lat":35.15,"lon":126.90,"on":"safe"},
               {"type":"STREETLIGHT","lat":35.15,"lon":126.92,"on":"safe"}],
             "dataQuality":{"status":"PARTIAL","message":"이 지역은 안전 데이터가 부족합니다","dataDate":"2026-11-03"}}
            """.trimIndent(),
        )
        val result = parseRouteResult(json)
        assertEquals(listOf(Condition("night", "밤", true)), result.conditions)
        val shortest = result.route(RouteType.SHORTEST)!!
        // geometry is [lon, lat]
        assertEquals(LatLon(35.14, 126.91), shortest.path.first())
        assertEquals(3, result.route(RouteType.SAFE)!!.path.size)
        assertEquals("216m(3분) 더 돌지만 가로등 +33개, CCTV +22대", result.explanation.summary)
        // bell is 0 on both routes, so no row for it
        assertEquals(
            listOf(CompareItem("가로등", 10, 43, "개", "light"), CompareItem("CCTV", 3, 25, "대", "cctv"), CompareItem("도움 지점", 1, 2, "곳", "help")),
            result.explanation.items,
        )
        assertEquals(listOf(FacilityKind.CCTV, FacilityKind.GUARD_HOUSE), result.facilities.map { it.kind })
        // CCTV is on both routes, the guard house only on the safe one
        assertEquals(listOf(FacilityKind.CCTV, FacilityKind.GUARD_HOUSE), result.facilitiesOn(RouteType.SAFE).map { it.kind })
        assertEquals(listOf(FacilityKind.CCTV), result.facilitiesOn(RouteType.SHORTEST).map { it.kind })
        // Only part of the route lacks data (campus paths), so no notice.
        assertTrue(result.dataQuality.ok)
        assertEquals("이 지역은 안전 데이터가 부족합니다", result.dataQuality.message)
    }

    @Test
    fun parseRouteResult_singleRouteInDaytime() {
        val json = JSONObject(
            """
            {"conditions":{"night":false,"nightSource":"auto","sunset":"17:41"},
             "routes":[{"kind":"safe","distanceM":1435,"durationMin":18,"geometry":[[126.91,35.14],[126.92,35.15]],
                "counts":{"light":20,"cctv":5,"bell":1,"help":2}}],
             "explanation":{"text":"지금은 밤이 아니라 가장 짧은 길을 안내합니다","extraM":0,"extraMin":0,"diff":{}},
             "facilities":[],
             "dataQuality":{"status":"OK","message":null,"dataDate":"2026-11-03"}}
            """.trimIndent(),
        )
        val result = parseRouteResult(json)
        assertTrue(result.conditions.isEmpty())
        assertEquals(RouteType.SAFE, result.routes.single().type)
        assertTrue(result.explanation.items.isEmpty())
        assertTrue(result.dataQuality.ok)
        assertNull(result.dataQuality.message)
    }

    @Test
    fun parseRouteResult_readsRestSchoolCoveredAndNotes() {
        val json = JSONObject(
            """
            {"conditions":{"night":false,"nightSource":"auto"},
             "routes":[
               {"kind":"shortest","distanceM":1000,"durationMin":15,"geometry":[[126.91,35.14],[126.92,35.15]],
                "counts":{"light":80,"help":5,"rest":12,"school":3},"covered":{"school":623,"help":1027}},
               {"kind":"safe","distanceM":1250,"durationMin":19,"geometry":[[126.91,35.14],[126.92,35.15]],
                "counts":{"light":82,"help":4,"rest":21,"school":3},"covered":{"school":1215,"help":1392}}],
             "explanation":{"text":"250m(4분) 더 돌지만 비상벨 +2개 (알림: 행사 구간을 지납니다)","summary":"250m(4분) 더 돌지만 비상벨 +2개",
                "notes":[{"level":"info","kind":"event","text":"행사 구간을 지납니다"},{"level":"warn","kind":"stairs","text":"계단을 지납니다"}]},
             "facilities":[
               {"type":"SCHOOL_ZONE","lat":35.14,"lon":126.91,"on":"both"},
               {"type":"SHELTER","lat":35.14,"lon":126.91,"on":"safe"},
               {"type":"TOILET","lat":35.14,"lon":126.91,"on":"safe"}],
             "dataQuality":{"status":"OK"}}
            """.trimIndent(),
        )
        val child = parseRouteResult(json, UserMode.CHILD)
        assertEquals("250m(4분) 더 돌지만 비상벨 +2개", child.explanation.summary)
        assertEquals(
            listOf(RouteNote(false, "event", "행사 구간을 지납니다"), RouteNote(true, "stairs", "계단을 지납니다")),
            child.explanation.notes,
        )
        assertTrue(CompareItem("쉼 곳", 12, 21, "곳", "rest") in child.explanation.items)
        assertTrue(CompareItem("어린이보호구역", 3, 3, "곳", "school") in child.explanation.items)
        assertTrue(CompareItem("보호구역 안을 걷는 길", 623, 1215, "m", "covered.school") in child.explanation.items)
        assertEquals(mapOf("school" to 1215, "help" to 1392), child.route(RouteType.SAFE)!!.covered)
        // Children see protection zones, not shelters or toilets; seniors the other way round.
        assertEquals(listOf(FacilityKind.SCHOOL_ZONE), child.facilities.map { it.kind })
        val senior = parseRouteResult(json, UserMode.SENIOR)
        assertEquals(listOf(FacilityKind.HEAT_SHELTER, FacilityKind.TOILET), senior.facilities.map { it.kind })
        assertEquals("쉼터", senior.facilities.first().label)
    }

    @Test
    fun parseRouteResult_noDataShowsNotice() {
        val json = JSONObject("""{"routes":[],"dataQuality":{"status":"NO_DATA","message":"이 지역은 안전 데이터가 부족합니다"}}""")
        assertFalse(parseRouteResult(json).dataQuality.ok)
    }

    @Test
    fun parseRouteResult_oldServerTextIsSummary() {
        val json = JSONObject("""{"routes":[],"explanation":{"text":"가장 짧은 길로 갑니다"}}""")
        val result = parseRouteResult(json)
        assertEquals("가장 짧은 길로 갑니다", result.explanation.summary)
        assertTrue(result.explanation.notes.isEmpty())
    }

    @Test
    fun parseRouteResult_nightSwitchedOffKeepsChip() {
        val json = JSONObject("""{"conditions":{"night":false,"nightSource":"manual"},"routes":[]}""")
        assertEquals(listOf(Condition("night", "밤", false)), parseRouteResult(json).conditions)
    }

    @Test
    fun toJson_sendsNightInsteadOfConditionsOff() {
        val origin = LatLon(35.14, 126.91)
        val destination = LatLon(35.15, 126.92)
        val auto = RouteRequest(origin, destination, UserMode.DISABLED).toJson()
        assertEquals("auto", auto.getString("night"))
        assertEquals("wheelchair", auto.getString("mode"))
        assertFalse(auto.has("conditionsOff"))
        assertEquals("off", RouteRequest(origin, destination, UserMode.GENERAL, setOf("night")).toJson().getString("night"))
        // Switched on in daytime
        assertEquals("on", RouteRequest(origin, destination, UserMode.GENERAL, conditionsOn = setOf("night")).toJson().getString("night"))
    }

    @Test
    fun weatherNotes_showOnlyWhatTheServerGot() {
        val all = JSONObject("""{"night":false,"rain":true,"heat":false,"cold":false,"tempC":12.4,"dustBad":true,"weatherAvailable":true,"dustAvailable":true}""")
        assertEquals(listOf("비", "12°C", "미세먼지 나쁨"), weatherNotes(all))
        val none = JSONObject("""{"rain":true,"tempC":12.4,"dustBad":true,"weatherAvailable":false,"dustAvailable":false}""")
        assertTrue(weatherNotes(none).isEmpty())
        // Older servers send no weather fields
        assertTrue(weatherNotes(JSONObject("""{"night":true}""")).isEmpty())
    }

    @Test
    fun nightTurnsOnInTheEveningOrInTheRain() {
        val base = RouteResult(
            conditions = emptyList(), routes = emptyList(),
            explanation = Explanation("", emptyList()), facilities = emptyList(), dataQuality = DataQuality(true, null),
            sunset = java.time.LocalTime.of(17, 34),
        )
        val noon = java.time.LocalTime.of(12, 0)
        val evening = java.time.LocalTime.of(16, 40)
        assertEquals(null, autoNightReason(base, noon))
        assertEquals("저녁이라", autoNightReason(base, evening))
        assertEquals("비가 와서", autoNightReason(base.copy(raining = true), noon))
        // Already night from the server: nothing to add.
        assertEquals(null, autoNightReason(base.copy(conditions = listOf(Condition("night", "밤", true))), evening))
    }

    @Test
    fun sidewalkHintTellsTheSideOnlyWhenKnown() {
        val walks = listOf(Sidewalk(1, 5, SidewalkSide.UNKNOWN), Sidewalk(5, 9, SidewalkSide.RIGHT), Sidewalk(9, 12, SidewalkSide.NONE))
        assertEquals(null, sidewalkHint(walks, 0))
        assertEquals(null, sidewalkHint(walks, 3))
        assertEquals("오른쪽 인도로 걸어요", sidewalkHint(walks, 5))
        assertEquals("인도가 없어요. 차 조심하세요", sidewalkHint(walks, 10))
        assertEquals(null, sidewalkHint(walks, 12))
    }
}
