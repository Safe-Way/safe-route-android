package com.deungbul.anshimgil.data

// 시험에 쓰는 도구들
import org.json.JSONArray               // JSON의 목록([...])을 다룰 때 씀
import org.json.JSONObject              // JSON의 객체({...})를 다룰 때 씀
import org.junit.Assert.assertEquals    // assertEquals(기대값, 실제값): 둘이 같아야 통과
import org.junit.Assert.assertFalse     // assertFalse(조건): 조건이 거짓이어야 통과
import org.junit.Assert.assertNotNull   // assertNotNull(값): 값이 null이 아니어야(있어야) 통과
import org.junit.Assert.assertTrue      // assertTrue(조건): 조건이 참이어야 통과
import org.junit.Assert.fail            // fail(글): 부르는 순간 그 글을 보여 주며 시험 실패
import org.junit.Test                   // @Test가 붙은 함수 하나하나가 시험 하나

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
 *
 * (쉬운 설명) 앱은 경로, 대중교통, 버스, 지하철, 신고, 시설, 장소, 행사 정보를 서버에서 받아 화면에 그린다.
 * 이 시험은 진짜 서버를 부르지 않는다. 미리 운영 서버에서 받아 저장해 둔 답(json 파일 11개)을
 * fixture()로 열어서, 앱이 읽는 칸 이름이 다 있는지와 앱 읽기 함수(parseRouteResult(), parseTransit() 등)가
 * 쓸 만한 값을 돌려주는지 본다. 읽기 함수들은 화면에 쓸 앱 데이터(경로 목록, 정류장 목록 등)를 돌려준다.
 */
class ServerContractTest {

    /**
     * [path] 자리의 모든 객체에 [keys] 가 있어야 한다. [mustExist] 면 그 자리에 객체가 하나 이상 있어야 한다.
     *
     * 규칙 한 줄. 예: Rule("stops[]", true, "name", "distanceM") 은
     * "stops 목록의 정류장마다 name과 distanceM 칸이 있어야 하고, 정류장이 하나 이상 있어야 한다"는 뜻이다.
     * path가 빈 글자("")면 답의 맨 위 객체를 뜻한다.
     */
    private class Rule(val path: String, val mustExist: Boolean, vararg val keys: String)

    /**
     * 저장해 둔 서버 답 파일을 읽어 JSON 객체로 돌려주는 도우미 함수.
     * 예: fixture("bus") 는 test/resources/contract 폴더의 bus.json 을 읽는다. 파일이 없으면 바로 오류를 낸다.
     */
    private fun fixture(name: String): JSONObject {
        val url = javaClass.getResource("/contract/$name.json") ?: error("응답 파일 contract/$name.json 이 없어요")
        return JSONObject(url.readText())
    }

    /**
     * "routes[].sidewalks[]" 같은 자리에 있는 객체들. 값이 없거나 null 이면 빈 목록.
     *
     * 길(path)은 점(.)으로 나눈 칸 이름을 차례로 따라간다. 이름 끝의 []는 "목록이니 안의 객체를 하나하나 다 본다"는 뜻이다.
     * 예: "routes[].sidewalks[]" 는 모든 경로(routes)의 모든 보도 구간(sidewalks) 객체를 한 목록으로 모아 돌려준다.
     */
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

    /**
     * 역할: 운영 서버에서 받아 둔 실제 답(test/resources/contract 폴더의 json 파일)에 앱이 읽는 이름이 모두 있는지 확인한다.
     * 앱은 없는 이름을 빈 글자·0으로 조용히 읽기 때문에, 서버가 이름을 바꾸면 이 시험이 먼저 알려 준다.
     */
    @Test
    fun everyNameTheAppReadsIsInTheServerAnswers() {
        // 찾은 문제를 글로 모아 둘 빈 목록. 다 돌고 나서 한 번에 보여 준다.
        val problems = mutableListOf<String>()
        // CONTRACT 표를 답 파일 이름(name)과 그 파일의 규칙들(rules)로 하나씩 꺼낸다.
        for ((name, rules) in CONTRACT) {
            // 저장해 둔 서버 답 파일(예: route.json)을 연다.
            val root = fixture(name)
            // 그 파일의 규칙을 한 줄씩 본다.
            for (rule in rules) {
                // 규칙이 말하는 자리(예: "routes[]")에 있는 객체들을 모두 모은다.
                val objects = objectsAt(root, rule.path)
                // 문제 글에 쓸 위치 이름. 자리가 빈 글자면 "(맨 위)"라고 쓴다. 예: "route routes[]"
                val where = "$name ${rule.path.ifEmpty { "(맨 위)" }}"
                // 꼭 있어야 하는 자리(mustExist)인데 객체가 하나도 없으면 문제로 적는다.
                if (rule.mustExist && objects.isEmpty()) problems += "$where: 비어 있어요"
                // 모은 객체를 하나씩(i는 몇 번째인지) 본다.
                objects.forEachIndexed { i, o ->
                    // 앱이 읽는 이름 중 이 객체에 없는 것만 고른다. 값이 null이어도 이름이 있으면 has()는 참이다.
                    val missing = rule.keys.filterNot { o.has(it) }
                    // 빠진 이름이 있으면 어느 파일, 어느 자리, 몇 번째 객체에 무엇이 없는지 적는다.
                    if (missing.isNotEmpty()) problems += "$where #$i: 앱이 읽는 ${missing.joinToString()} 이(가) 없어요"
                }
            }
        }
        // 문제가 하나라도 있으면 시험 실패. 같은 글은 한 번만, 최대 40줄까지 보여 준다.
        if (problems.isNotEmpty()) {
            fail("서버 응답이 앱이 읽는 모양과 달라요 (${problems.size}곳):\n" + problems.distinct().take(40).joinToString("\n"))
        }
    }

    /** 역할: 걸어가는 경로 답을 읽었을 때 거리·시간·선·해 지는 시각·경로 설명이 제대로 채워지는지 확인한다. */
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
        // result.conditions 는 밤이거나 사용자가 켠 조건만 담아 낮에는 비는 게 정상이라 보지 않는다.
        assertNotNull("해 지는 시각(conditions.sunset)이 안 읽혀요", result.sunset)
        assertFalse("경로 설명(explanation.summary)이 비었어요", result.explanation.summary.isBlank())
    }

    /** 역할: 대중교통 경로 답의 구간 종류를 앱이 다 알고, 타는 구간마다 노선·타는 곳·내리는 곳이 채워지는지 확인한다. */
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

    /** 역할: 가까운 버스 정류장과 지하철역 답에서 이름·거리·위치·도착 정보가 제대로 읽히는지 확인한다. */
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

    /** 역할: 신고 목록과 피할 곳(유효 신고 장소) 답이 빠짐없이, 알맞은 종류·날짜·위치로 읽히는지 확인한다. */
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

    /** 역할: 지도 시설 답의 종류(kind)를 앱이 모두 알고, 휠체어 지도에 그릴 시설이 읽히는지 확인한다. */
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

    /** 역할: 지금 있는 곳, 장소 검색 결과, 가까운 화장실 답에서 이름·위치·거리가 읽히는지 확인한다. */
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

    /** 역할: 행사 답이 있을 때 하나도 빠지지 않고 광주 안의 위치로 읽히는지 확인한다. */
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
        //
        // (읽는 법) "파일 이름" to listOf(규칙들). 파일 이름은 test/resources/contract 폴더의 json 파일 이름이다.
        // 규칙은 Rule(자리, 꼭 있어야 하나, 앱이 읽는 이름들...) 모양이다.
        val CONTRACT: Map<String, List<Rule>> = mapOf(
            "route" to listOf(
                // 예: route.json 맨 위에 conditions, routes, explanation, facilities, dataQuality 이름이 꼭 있어야 한다.
                Rule("", true, "conditions", "routes", "explanation", "facilities", "dataQuality"),
                // 예: conditions 객체에 밤·비·더위 같은 조건 이름들이 꼭 있어야 한다(값은 null이어도 된다).
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
