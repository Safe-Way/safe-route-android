package com.deungbul.anshimgil.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FacilityRepositoryTest {
    @Test
    fun wheelchairToiletIsDrawnAsWhicheverToiletTheModeShows() {
        val disabled = UserMode.DISABLED.shownKinds(emptySet())
        val senior = UserMode.SENIOR.shownKinds(emptySet())
        assertEquals(FacilityKind.ACCESSIBLE_TOILET, poiKind("toilet", "개방화장실", true, disabled))
        assertEquals(FacilityKind.TOILET, poiKind("toilet", "개방화장실", true, senior))
        // An ordinary toilet is not shown in wheelchair mode
        assertNull(poiKind("toilet", "개방화장실", false, disabled))
    }

    @Test
    fun footbridgesAndCrossingsFollowWheelchairAccess() {
        val shown = UserMode.DISABLED.shownKinds(emptySet())
        assertEquals(FacilityKind.ELEVATOR, poiKind("footbridge", null, true, shown))
        assertEquals(FacilityKind.OVERPASS, poiKind("footbridge", "없음", false, shown))
        assertEquals(FacilityKind.CURB_RAMP, poiKind("crosswalk", null, true, shown))
        // Unknown curb height is a small gray dot; a known high curb is left off
        assertEquals(FacilityKind.CURB_RAMP, poiKind("crosswalk", "신호 모름", null, shown))
        assertEquals(FacilityLook.DOT, poiLabel(FacilityKind.CURB_RAMP, null, null, UserMode.DISABLED).second)
        assertEquals(FacilityLook.NORMAL, poiLabel(FacilityKind.CURB_RAMP, true, null, UserMode.DISABLED).second)
        assertNull(poiKind("crosswalk", null, false, shown))
    }

    @Test
    fun sheltersSplitBySub() {
        val shown = UserMode.SENIOR.shownKinds(emptySet())
        assertEquals(FacilityKind.HEAT_SHELTER, poiKind("shelter", "무더위쉼터", null, shown))
        assertEquals(FacilityKind.COLD_SHELTER, poiKind("shelter", "한파쉼터", null, shown))
    }

    @Test
    fun serverKindsCoverChildZonesOnlyWhenShown() {
        assertTrue("school_zone" in serverKinds(UserMode.CHILD.shownKinds(emptySet())))
        assertTrue("school_zone" !in serverKinds(UserMode.GENERAL.shownKinds(emptySet())))
        assertTrue("school_zone" in serverKinds(UserMode.GENERAL.shownKinds(setOf(CHILD_ZONE_KEY))))
    }

    @Test
    fun parsePoisReadsServerShape() {
        val json = JSONObject(
            """
            {"pois":[
              {"kind":"cctv","sub":null,"lat":35.14,"lon":126.93,"wheelchair":null},
              {"kind":"stairs","sub":"계단","lat":35.15,"lon":126.92,"wheelchair":false}],
             "truncated":true}
            """.trimIndent(),
        )
        val page = parsePois(json, UserMode.GENERAL.shownKinds(emptySet()), UserMode.GENERAL)
        // General mode does not show stairs
        assertEquals(listOf(Facility(FacilityKind.CCTV, 35.14, 126.93)), page.layers.facilities)
        assertTrue(page.truncated)
    }

    @Test
    fun wideViewKeepsTheMiddle() {
        val b = Bbox(126.0, 35.0, 127.0, 35.5).clamped()
        assertEquals(0.2, b.east - b.west, 1e-9)
        assertEquals(0.2, b.north - b.south, 1e-9)
        assertEquals(126.5, (b.east + b.west) / 2, 1e-9)
    }

    @Test
    fun parseNearbyKeepsServerMessage() {
        val empty = parseNearby(JSONObject("""{"radiusM":1000,"places":[],"message":"1km 안에 이 시설이 없어요"}"""))
        assertTrue(empty.places.isEmpty())
        assertEquals("1km 안에 이 시설이 없어요", empty.message)
        val one = parseNearby(
            JSONObject("""{"places":[{"id":578606,"name":"이마트24","address":"동구","lat":35.14,"lon":126.93,"distanceM":60}],"message":null}"""),
        )
        assertEquals("578606", one.places.single().id)
        assertEquals(60, one.places.single().distanceM)
        assertNull(one.message)
    }

    @Test
    fun stationsShowElevatorExitsForSeniorAndDisabled() {
        assertEquals(null to FacilityLook.NORMAL, poiLabel(FacilityKind.SUBWAY, true, 2, UserMode.GENERAL))
        assertEquals("지하철역" to FacilityLook.NORMAL, poiLabel(FacilityKind.SUBWAY, true, 2, UserMode.SENIOR))
        // 양동시장역: no street elevator, grayed out only in disabled mode
        assertEquals("지하철역 · 엘리베이터 출구 없음" to FacilityLook.GRAY, poiLabel(FacilityKind.SUBWAY, false, 1, UserMode.DISABLED))
        assertEquals(FacilityLook.NORMAL, poiLabel(FacilityKind.SUBWAY, false, 1, UserMode.SENIOR).second)
        // 녹동역: not confirmed
        assertEquals("지하철역 · 엘리베이터 확인 안 됨", poiLabel(FacilityKind.SUBWAY, null, 1, UserMode.DISABLED).first)
        // Every mode shows stations and entrances
        UserMode.entries.forEach {
            assertTrue(FacilityKind.SUBWAY in it.shownKinds(emptySet()))
            assertTrue("subway_entrance" in serverKinds(it.shownKinds(emptySet())))
        }
    }

    @Test
    fun entrancesWithElevatorStandOutForSeniorAndDisabled() {
        assertEquals("역 입구", poiLabel(FacilityKind.SUBWAY_ENTRANCE, true, null, UserMode.DISABLED).first)
        assertEquals("3번 출구", poiLabel(FacilityKind.SUBWAY_ENTRANCE, true, null, UserMode.DISABLED, "남광주역 3번 출구").first)
        assertEquals("1번 출구" to FacilityLook.GRAY, poiLabel(FacilityKind.SUBWAY_ENTRANCE, false, null, UserMode.DISABLED, "남광주역 1번 출구"))
        assertNull(poiLabel(FacilityKind.SUBWAY_ENTRANCE, null, null, UserMode.DISABLED).first)
        assertNull(poiLabel(FacilityKind.SUBWAY_ENTRANCE, true, null, UserMode.CHILD).first)
        assertEquals("2번 출구", poiLabel(FacilityKind.SUBWAY_ENTRANCE, false, null, UserMode.CHILD, "남광주역 2번 출구").first)
    }

    @Test
    fun stationKeepsNameAndDetailForTheTapCard() {
        val json = JSONObject(
            """{"pois":[{"kind":"subway","sub":null,"name":"문화전당역","lat":35.147,"lon":126.919,"wheelchair":true,"count":3,
               "detail":"지하철역 · 엘리베이터 있는 출구 3곳(2·3·4번) · 장애인 화장실 2곳"}],"truncated":false}""",
        )
        val station = parsePois(json, UserMode.DISABLED.shownKinds(emptySet()), UserMode.DISABLED).layers.facilities.single()
        assertEquals("문화전당역", station.name)
        assertEquals("지하철역 · 엘리베이터 있는 출구 3곳(2·3·4번) · 장애인 화장실 2곳", station.detail)
        assertEquals(true, station.elevator)
    }

    @Test
    fun stationCardListsItsExitsInOrder() {
        val station = Facility(FacilityKind.SUBWAY, 35.14, 126.92, name = "남광주역")
        val exits = listOf(
            Facility(FacilityKind.SUBWAY_ENTRANCE, 0.0, 0.0, name = "남광주역 4번 출구", detail = "지하철 출입구 · 엘리베이터 있음"),
            Facility(FacilityKind.SUBWAY_ENTRANCE, 0.0, 0.0, name = "남광주역 1번 출구", detail = "지하철 출입구 · 엘리베이터 없음"),
            Facility(FacilityKind.SUBWAY_ENTRANCE, 0.0, 0.0, name = "학동증심사입구역 1번 출구", detail = "지하철 출입구 · 엘리베이터 있음"),
        )
        assertEquals(listOf("1번 출구 · 엘리베이터 없음", "4번 출구 · 엘리베이터 있음"), stationExits(station, exits))
    }

    @Test
    fun schoolZonesComeWithAnAreaAroundTheSchool() {
        val json = JSONObject("""{"pois":[{"kind":"school_zone","sub":"초등학교","name":"서석초등학교","lat":35.1475,"lon":126.9255}],"truncated":false}""")
        val page = parsePois(json, UserMode.CHILD.shownKinds(emptySet()), UserMode.CHILD)
        val zone = page.layers.zones.single()
        assertEquals(4, zone.points.size)
        assertTrue(zone.points.all { (lat, lon) -> kotlin.math.abs(lat - 35.1475) < 0.002 && kotlin.math.abs(lon - 126.9255) < 0.002 })
    }
}
