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
    fun stationsShowElevatorsForSeniorAndDisabled() {
        val detail = "지하철역 · 엘리베이터 3대 · 장애인 화장실 2곳"
        assertEquals(null to FacilityLook.NORMAL, poiLabel(FacilityKind.SUBWAY, true, detail, UserMode.GENERAL))
        assertEquals("지하철역 · 엘리베이터 3대" to FacilityLook.NORMAL, poiLabel(FacilityKind.SUBWAY, true, detail, UserMode.SENIOR))
        // 녹동역: no elevator, grayed out only in disabled mode
        val nokdong = "지하철역 · 엘리베이터 없음"
        assertEquals("지하철역 · 엘리베이터 없음" to FacilityLook.GRAY, poiLabel(FacilityKind.SUBWAY, false, nokdong, UserMode.DISABLED))
        assertEquals(FacilityLook.NORMAL, poiLabel(FacilityKind.SUBWAY, false, nokdong, UserMode.SENIOR).second)
        // Every mode shows stations
        UserMode.entries.forEach { assertTrue(FacilityKind.SUBWAY in it.shownKinds(emptySet())) }
    }

    @Test
    fun entrancesWithElevatorStandOutForSeniorAndDisabled() {
        assertEquals("역 입구 · 엘리베이터", poiLabel(FacilityKind.SUBWAY_ENTRANCE, true, null, UserMode.DISABLED).first)
        assertNull(poiLabel(FacilityKind.SUBWAY_ENTRANCE, null, null, UserMode.DISABLED).first)
        assertNull(poiLabel(FacilityKind.SUBWAY_ENTRANCE, true, null, UserMode.CHILD).first)
    }
}
