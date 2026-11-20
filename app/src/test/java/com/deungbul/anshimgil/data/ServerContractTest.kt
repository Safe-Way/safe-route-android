package com.deungbul.anshimgil.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 앱·서버 연결 검사: 실제 서버 응답(`src/test/resources/contract/`)을 앱의 진짜 읽기 코드로 읽어 본다.
 *
 * 앱은 응답을 `optString`·`optInt` 로 읽어서, 서버가 이름을 바꾸거나 빼도 오류 없이 빈 글자·0이 된다
 * (화면에 조용히 잘못 나옴). 그래서 두 가지를 본다.
 * 1. [CONTRACT]: 앱이 읽는 이름이 응답의 그 자리에 모두 있는지 (값이 null 이어도 이름은 있어야 함).
 * 2. 앱 읽기 함수의 결과가 제대로 채워졌는지 (거리·시간이 0이 아님, 이름이 비지 않음, 광주 안의 좌표 등).
 *
 * 응답 파일은 `scripts/capture_contract.sh` 가 운영 서버에서 받는다. 저장소의 파일은 앱 코드가 바뀔 때(PR) 보고,
 * `.github/workflows/contract.yml` 은 매일 아침 운영 서버에서 새로 받아 서버가 바뀌었는지 본다.
 * 서버가 일부러 이름을 바꿨다면: 앱 읽기 코드를 고치고, 이 표를 고치고, 스크립트로 응답 파일을 새로 받는다.
 */
class ServerContractTest {

    /** [path] 자리의 모든 객체에 [keys] 가 있어야 한다. [mustExist] 면 그 자리에 객체가 하나 이상 있어야 한다. */
    private class Rule(val path: String, val mustExist: Boolean, vararg val keys: String)

    private fun fixture(name: String): JSONObject {
        val url = javaClass.getResource("/contract/$name.json") ?: error("응답 파일 contract/$name.json 이 없어요")
        return JSONObject(url.readText())
    }

    /** "routes[].sidewalks[]" 같은 자리에 있는 객체들. 값이 없거나 null 이면 빈 목록. */
    private fun objectsAt(root: JSONObject, path: String): List<JSONObject> {
        if (path.isEmpty()) return listOf(root)
        var current = listOf(root)
        for (segment in path.split('.')) {
            val many = segment.endsWith("[]")
            val key = segment.removeSuffix("[]")
            current = current.flatMap { o ->
                val v = o.opt(key)
                when {
                    many && v is JSONArray -> (0 until v.length()).mapNotNull { v.opt(it) as? JSONObject }
                    !many && v is JSONObject -> listOf(v)
                    else -> emptyList()
                }
            }
        }
        return current
    }

    @Test
    fun everyNameTheAppReadsIsInTheServerAnswers() {
        val problems = mutableListOf<String>()
        for ((name, rules) in CONTRACT) {
            val root = fixture(name)
            for (rule in rules) {
                val objects = objectsAt(root, rule.path)
                val where = "$name ${rule.path.ifEmpty { "(맨 위)" }}"
                if (rule.mustExist && objects.isEmpty()) problems += "$where: 비어 있어요"
                objects.forEachIndexed { i, o ->
                    val missing = rule.keys.filterNot { o.has(it) }
                    if (missing.isNotEmpty()) problems += "$where #$i: 앱이 읽는 ${missing.joinToString()} 이(가) 없어요"
                }
            }
        }
        if (problems.isNotEmpty()) {
            fail("서버 응답이 앱이 읽는 모양과 달라요 (${problems.size}곳):\n" + problems.distinct().take(40).joinToString("\n"))
        }
    }

    @Test
    fun walkingRouteIsReadWithLengthTimeAndLine() {
        val result = parseRouteResult(fixture("route"), UserMode.DISABLED)
        assertTrue("경로가 하나도 안 읽혀요 (routes[].kind 가 safe·shortest 가 아닐 수 있음)", result.routes.isNotEmpty())
        assertNotNull("안전한 길(kind=safe)이 없어요", result.route(RouteType.SAFE))
        for (r in result.routes) {
            assertTrue("${r.type} 거리 ${r.distanceM}", r.distanceM > 0)
            assertTrue("${r.type} 시간 ${r.durationMin}", r.durationMin > 0)
            assertTrue("${r.type} 선 점 ${r.path.size}개", r.path.size >= 2)
            r.path.forEach { assertInGwangju("${r.type} 선", it) }
            r.sidewalks.forEach { assertTrue("보도 구간 ${it.from}..${it.to}", it.from <= it.to) }
        }
        assertTrue("날씨·밤 같은 상황(conditions)이 안 읽혀요", result.conditions.isNotEmpty())
        assertNotNull("해 지는 시각(conditions.sunset)이 안 읽혀요", result.sunset)
        assertFalse("경로 설명(explanation.summary)이 비었어요", result.explanation.summary.isBlank())
    }

    @Test
    fun transitRoutesAreReadWithRidesAndKnownLegTypes() {
        val json = fixture("transit")
        val known = setOf("walk", "bus", "subway", "stop")
        // 모르는 type 은 앱이 조용히 '걷기'로 읽으므로 따로 본다.
        objectsAt(json, "paths[].legs[]").forEach { assertTrue("모르는 구간 종류 '${it.optString("type")}'", it.optString("type") in known) }
        val result = parseTransit(json)
        assertTrue("대중교통 길이 하나도 안 읽혀요", result.routes.isNotEmpty())
        for (route in result.routes) {
            assertTrue("총 시간 ${route.totalMin}", route.totalMin > 0)
            assertTrue("구간이 없어요", route.legs.isNotEmpty())
            val rides = route.legs.filter { it.type == LegType.BUS || it.type == LegType.SUBWAY }
            assertTrue("타는 구간이 없어요", rides.isNotEmpty())
            for (ride in rides) {
                assertTrue("${ride.type} 노선 이름이 없어요", ride.lines.isNotEmpty() && ride.lines.all { it.isNotBlank() })
                assertFalse("${ride.type} 타는 곳 이름이 없어요", ride.from.name.isNullOrBlank())
                assertFalse("${ride.type} 내리는 곳 이름이 없어요", ride.to.name.isNullOrBlank())
                assertInGwangju("타는 곳", ride.from.point)
                assertInGwangju("내리는 곳", ride.to.point)
                ride.buses.forEach { assertFalse("오는 버스 번호가 없어요", it.line.isBlank()) }
            }
        }
    }

    @Test
    fun nearbyBusStopsAndSubwayStationsAreRead() {
        val stops = parseBusNearby(fixture("bus"))
        assertTrue("가까운 정류장이 안 읽혀요", stops.isNotEmpty())
        for (s in stops) {
            assertFalse("정류장 이름이 없어요", s.name.isBlank())
            assertTrue("정류장 거리 ${s.distanceM}", s.distanceM in 0..2000)
            assertInGwangju("정류장 ${s.name}", s.point)
            s.buses.forEach { assertFalse("${s.name} 오는 버스 번호가 없어요", it.line.isBlank()) }
        }
        val stations = parseSubwayNearby(fixture("subway"))
        assertTrue("가까운 지하철역이 안 읽혀요", stations.isNotEmpty())
        for (st in stations) {
            assertFalse("역 이름이 없어요", st.name.isBlank())
            assertInGwangju("역 ${st.name}", LatLon(st.lat, st.lon))
            // 밤에는 열차가 없을 수 있어 열차는 있을 때만 본다.
            st.trains.forEach {
                assertFalse("${st.name} 열차 방향이 없어요", it.direction.isBlank())
                assertTrue("${st.name} 도착까지 ${it.inMin}분", it.inMin >= 0)
            }
        }
    }

    @Test
    fun reportsAndPlacesToAvoidAreRead() {
        val reportsJson = fixture("reports")
        val reports = parseReports(reportsJson)
        assertEquals("읽힌 신고 수", objectsAt(reportsJson, "reports[]").size, reports.size)
        for (r in reports) {
            assertTrue("신고 종류 '${r.code}' 를 앱이 몰라요", ReportType.entries.any { it.code == r.code })
            assertFalse("신고 이름이 없어요", r.label.isBlank())
            assertNotNull("신고 시각(createdAt)이 안 읽혀요", r.createdAt)
            assertInGwangju("신고 ${r.id}", r.point)
        }
        val activeJson = fixture("reports_active")
        val spots = parseActiveSpots(activeJson)
        // 날짜를 못 읽으면 앱은 그 자리를 빼 버린다: 수가 같아야 한다.
        assertEquals("읽힌 피할 곳 수 (startsOn·endsOn 형식)", objectsAt(activeJson, "spots[]").size, spots.size)
        for (s in spots) {
            assertTrue("피할 곳 종류 '${s.code}' 를 앱이 몰라요", ReportType.entries.any { it.code == s.code })
            assertFalse("${s.spotId} 기간 ${s.startsOn}~${s.endsOn}", s.endsOn.isBefore(s.startsOn))
            assertTrue("${s.spotId} 신고 수 ${s.count}", s.count >= 1)
            assertInGwangju("피할 곳 ${s.spotId}", s.point)
        }
    }

    @Test
    fun mapFacilitiesAreReadAsKnownKinds() {
        val json = fixture("pois")
        // 앱이 모르는 kind 는 지도에서 조용히 빠지므로 하나하나 본다 (앱 poiKind 의 목록).
        // 높은 턱으로 확인된 횡단보도(wheelchair=false)는 일부러 빼는 것이라 괜찮다.
        val unknown = objectsAt(json, "pois[]").mapNotNull { p ->
            val kind = p.optString("kind")
            val sub = p.optString("sub").takeUnless { p.isNull("sub") }
            when {
                kind !in KNOWN_POI_KINDS -> kind
                kind == "shelter" && sub !in setOf("무더위쉼터", "한파쉼터") -> "shelter/$sub"
                else -> null
            }
        }.distinct()
        assertTrue("앱이 모르는 시설 종류: $unknown", unknown.isEmpty())
        val page = parsePois(json, UserMode.DISABLED.shownKinds(emptySet()), UserMode.DISABLED)
        assertTrue("휠체어 지도에 그릴 시설이 안 읽혀요", page.layers.facilities.isNotEmpty())
        page.layers.facilities.forEach { assertInGwangju("시설 ${it.kind}", LatLon(it.lat, it.lon)) }
    }

    @Test
    fun placesAreReadWithNameAndPosition() {
        val here = Place.fromJson(fixture("places_here"))
        assertFalse("지금 있는 곳 이름이 없어요", here.name.isBlank())
        assertInGwangju("지금 있는 곳", LatLon(here.lat, here.lon))

        val found = objectsAt(fixture("places"), "places[]").map { Place.fromJson(it) }
        assertTrue("'문화전당' 검색 결과가 안 읽혀요", found.isNotEmpty())
        for (p in found) {
            assertFalse("장소 이름이 없어요", p.name.isBlank())
            assertNotNull("${p.name} 거리(distanceM)가 안 읽혀요", p.distanceM)
        }

        val nearby = parseNearby(fixture("places_nearby"))
        assertTrue("가까운 화장실이 안 읽혀요 (${nearby.message})", nearby.places.isNotEmpty())
        nearby.places.forEach {
            assertFalse("화장실 이름이 없어요", it.name.isBlank())
            assertNotNull("${it.name} 거리가 안 읽혀요", it.distanceM)
        }
    }

    @Test
    fun roadEventsAreReadWhenThereAreAny() {
        val json = fixture("events")
        val events = parseRoadEvents(json)
        // 제목·좌표가 없으면 앱이 그 행사를 빼 버린다: 수가 같아야 한다 (행사가 없는 날은 둘 다 0).
        assertEquals("읽힌 행사 수", objectsAt(json, "events[]").size, events.size)
        events.forEach { assertInGwangju("행사 ${it.title}", LatLon(it.lat, it.lon)) }
    }

    private fun assertInGwangju(what: String, p: LatLon) {
        assertTrue("$what 좌표 (${p.lat}, ${p.lon}) 가 광주 밖이에요 (lat·lon 이 바뀌었거나 0)", p.lat in 34.9..35.4 && p.lon in 126.6..127.1)
    }

    private companion object {
        /** 앱 poiKind 가 그리는 서버 kind (FacilityRepository.kt). 서버에 새 kind 가 생기면 여기서 걸린다. */
        val KNOWN_POI_KINDS = setOf(
            "cctv", "police", "guard_house", "bell", "store", "shelter", "toilet", "crosswalk",
            "footbridge", "stairs", "school_zone", "subway", "subway_entrance",
        )

        /**
         * 앱이 읽는 이름 (2026-11-20, 앱 data/ 의 읽기 함수에서 뽑음). 서버 응답 이름을 바꿀 때 이 표가 먼저 깨진다.
         * mustExist = true 는 시연 지역에서 늘 있어야 하는 자리, false 는 있을 때만 보는 자리.
         */
        val CONTRACT: Map<String, List<Rule>> = mapOf(
            "route" to listOf(
                Rule("", true, "conditions", "routes", "explanation", "facilities", "dataQuality"),
                Rule("conditions", true, "night", "nightSource", "sunset", "rain", "heat", "cold", "tempC", "weatherAvailable", "dustBad", "dustAvailable"),
                Rule("routes[]", true, "kind", "distanceM", "durationMin", "geometry", "counts", "covered", "sidewalks", "crossings", "events", "stops", "crossAt"),
                Rule("routes[].sidewalks[]", false, "from", "to", "side", "highway", "lanes", "oneway", "offsetM", "widthM"),
                Rule("routes[].turns[]", false, "atM"),
                Rule("routes[].crossings[]", false, "fromM", "toM"),
                Rule("routes[].events[]", false, "kind", "title", "place", "message", "endsAt", "fromM", "toM", "avoid", "near"),
                Rule("routes[].crossAt[]", false, "at", "lat", "lon", "kind"),
                Rule("explanation", true, "summary", "notes"),
                Rule("explanation.notes[]", false, "level", "kind", "text"),
                Rule("facilities[]", false, "type", "lat", "lon", "on"),
                Rule("dataQuality", true, "status", "message"),
            ),
            "transit" to listOf(
                Rule("", true, "paths", "hiddenForWheelchair", "note"),
                Rule("paths[]", true, "totalMin", "fare", "transfers", "walkM", "legs"),
                Rule("paths[].legs[]", true, "type", "distanceM", "minutes", "from", "to", "lines", "way", "stationCount", "geometry", "buses", "trains", "stops"),
                Rule("paths[].legs[].from", true, "name", "lat", "lon"),
                Rule("paths[].legs[].to", true, "name", "lat", "lon"),
                Rule("paths[].legs[].stops[]", false, "name", "lat", "lon"),
                Rule("paths[].legs[].buses[]", false, "lineName", "remainMin", "remainStop", "lowFloor"),
                Rule("paths[].legs[].trains[]", false, "direction", "inMin", "stopsAway"),
                Rule("paths[].legs[].sidewalks[]", false, "from", "to"),
            ),
            "bus" to listOf(
                Rule("", true, "stops"),
                Rule("stops[]", true, "name", "nextStop", "distanceM", "lat", "lon", "arrivals"),
                Rule("stops[].arrivals[]", false, "lineName", "remainMin", "remainStop", "lowFloor"),
            ),
            "subway" to listOf(
                Rule("", true, "stations"),
                Rule("stations[]", true, "name", "lat", "lon", "trains"),
                Rule("stations[].trains[]", false, "direction", "inMin", "stopsAway"),
            ),
            "reports" to listOf(
                Rule("", true, "reports"),
                Rule("reports[]", false, "id", "type", "typeName", "lat", "lon", "memo", "status", "createdAt", "photoUrls"),
            ),
            "reports_active" to listOf(
                Rule("", true, "spots"),
                Rule("spots[]", false, "spotId", "reportType", "lat", "lon", "startsOn", "endsOn", "reportCount", "confirmed", "lastAt", "photoCount", "lastMemo"),
            ),
            "pois" to listOf(
                Rule("", true, "pois", "truncated"),
                Rule("pois[]", true, "kind", "sub", "name", "lat", "lon", "wheelchair", "count", "detail"),
            ),
            "places_here" to listOf(Rule("", true, "id", "name", "address", "lat", "lon", "distanceM")),
            "places" to listOf(
                Rule("", true, "places"),
                Rule("places[]", true, "id", "name", "address", "lat", "lon", "distanceM"),
            ),
            "places_nearby" to listOf(
                Rule("", true, "places", "message"),
                Rule("places[]", true, "id", "name", "address", "lat", "lon", "distanceM"),
            ),
            "events" to listOf(
                Rule("", true, "events"),
                Rule("events[]", false, "id", "kind", "title", "place", "lat", "lon", "radiusM", "message"),
            ),
        )
    }
}
