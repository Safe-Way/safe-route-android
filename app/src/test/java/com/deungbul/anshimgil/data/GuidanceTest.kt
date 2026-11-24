package com.deungbul.anshimgil.data

// 시험에 쓰는 도구들
import org.junit.Assert.assertEquals    // assertEquals(기대값, 실제값): 둘이 같아야 통과
import org.junit.Assert.assertTrue      // assertTrue(조건): 조건이 참이어야 통과
import org.junit.Test                   // @Test가 붙은 함수 하나하나가 시험 하나

/**
 * 길안내(턴바이턴 안내) 시험.
 *
 * 앱은 정해진 경로(점들을 이은 선) 위에서 걷는 사람에게 "다음에 어느 쪽으로 꺾는지",
 * "모퉁이까지 몇 m 남았는지", "경로에서 몇 m 떨어졌는지", "가까운 도움 장소가 어디인지"를 알려 준다.
 * 이 계산은 앱의 Guidance 클래스가 한다.
 *
 * 여기서는 진짜 GPS를 쓰지 않는다. 위도·경도 숫자로 경로와 '지금 위치'를 직접 만들어
 * Guidance.update(위치)나 Guidance.atProgress(경로를 따라 걸은 거리)에 넣고, 나온 결과를 확인한다.
 * 두 함수는 GuidanceState를 돌려준다. 그 안에 다음 회전(nextTurn: LEFT·RIGHT·STRAIGHT·ARRIVE),
 * 다음 회전까지 거리(metersToNextTurn), 남은 거리(remainingM), 경로에서 떨어진 거리(offRouteM),
 * 경로 위로 옮겨 그린 위치(snapped), 가장 가까운 도움 장소(nearestHelp)가 담긴다.
 */
class GuidanceTest {
    // Walk west about 110 m, then turn left (south) for about 110 m.
    // 여러 시험이 함께 쓰는 ㄱ자 경로: 서쪽으로 약 110m 걷다가 모퉁이에서 왼쪽(남쪽)으로 꺾어 약 110m 더 간다.
    // 출발점. 위도 35.1404, 경도 126.9318 (조선대 근처)
    private val start = LatLon(35.1404, 126.9318)
    // 모퉁이. 위도는 같고 경도만 0.0012 작다 = 출발점에서 서쪽으로 약 109m
    private val corner = LatLon(35.1404, 126.9306)
    // 도착점. 경도는 모퉁이와 같고 위도만 0.001 작다 = 모퉁이에서 남쪽으로 약 111m
    private val end = LatLon(35.1394, 126.9306)
    // 위 세 점을 차례로 이은 경로(앱이 서버에서 받는 경로 선과 같은 모양)
    private val path = listOf(start, corner, end)

    /**
     * 역할: 출발점에서 다음 회전 방향(왼쪽)과 모퉁이까지 남은 거리(약 110m)를 맞게 알려 주는지 확인한다.
     * 길안내의 가장 기본이라, 여기가 틀리면 나머지 안내도 다 틀린다.
     */
    @Test
    fun atStart_nextTurnIsLeftAtCorner() {
        // ㄱ자 경로로 길안내를 만들고(도움 장소 목록은 빈 목록), 지금 위치를 출발점으로 넣는다.
        val g = Guidance(path, emptyList()).update(start)
        // 서쪽으로 가다 남쪽으로 꺾으므로 다음 안내는 '왼쪽'이어야 한다.
        assertEquals(Turn.LEFT, g.nextTurn)
        // 모퉁이까지 남은 거리는 약 109m. 지구를 평평하게 보는 근사 계산이라 100~120m 안이면 통과.
        // 틀리면 앞의 글("about 109 m ...")과 실제 값이 실패 메시지로 나온다.
        assertTrue("about 109 m to the corner, got ${g.metersToNextTurn}", g.metersToNextTurn in 100..120)
        // 출발점은 경로 선 바로 위이므로 경로에서 떨어진 거리는 0m여야 한다.
        assertEquals(0, g.offRouteM)
    }

    /** 역할: 모퉁이를 지나 도착까지 50m 남았을 때 다음 안내가 '도착'이고 남은 거리가 50m로 나오는지 확인한다. */
    @Test
    fun afterCorner_headsToArrival() {
        val guidance = Guidance(path, emptyList())
        val g = guidance.atProgress(guidance.totalM - 50)
        assertEquals(Turn.ARRIVE, g.nextTurn)
        assertEquals(50, g.remainingM)
    }

    /** 역할: 경로에서 약 45m 벗어난 위치를 경로 위로 옮겨 그리고, 떨어진 거리를 맞게 재는지 확인한다. */
    @Test
    fun offRoutePosition_isSnappedAndMeasured() {
        val g = Guidance(path, emptyList()).update(LatLon(35.1408, 126.9312)) // ~45 m north of the first leg
        assertTrue("off route ${g.offRouteM}", g.offRouteM in 35..55)
        assertEquals(35.1404, g.snapped.lat, 1e-5)
    }

    /** 역할: 가장 가까운 도움 장소(경비실)와 그것이 걷는 방향의 어느 쪽(왼쪽)에 있는지 알려 주는지 확인한다. */
    @Test
    fun nearestHelp_reportsSide() {
        // A guard house just south of the first leg: walking west, south is on the left.
        val help = Facility(FacilityKind.GUARD_HOUSE, 35.1403, 126.9314)
        val g = Guidance(path, listOf(help)).update(start)
        assertEquals(FacilityKind.GUARD_HOUSE, g.nearestHelp?.kind)
        assertEquals(Turn.LEFT, g.nearestHelp?.side)
    }

    /** 역할: CCTV는 바로 옆에 있어도 '도움 장소'로 치지 않고, 경비실 같은 곳만 도움 장소로 고르는지 확인한다. */
    @Test
    fun cctvIsNotAHelpPlace() {
        // A CCTV right beside the walker and a guard house farther away: only the guard house is help.
        val cctv = Facility(FacilityKind.CCTV, 35.14041, 126.9318)
        val guard = Facility(FacilityKind.GUARD_HOUSE, 35.1403, 126.9314)
        assertEquals(FacilityKind.GUARD_HOUSE, Guidance(path, listOf(cctv, guard)).update(start).nearestHelp?.kind)
        assertEquals(null, Guidance(path, listOf(cctv)).update(start).nearestHelp)
    }

    /** 역할: 앞으로 지나갈 도움 장소만 가까운 순서로, 어느 쪽에 있는지와 함께 보여 주고 지나간 곳은 빼는지 확인한다. */
    @Test
    fun helpListShowsPlacesAheadNearestFirst() {
        // A straight route north, about 220 m long.
        val path = listOf(LatLon(35.1400, 126.9300), LatLon(35.1420, 126.9300))
        val store = Facility(FacilityKind.CONVENIENCE_STORE, 35.1415, 126.93015) // ~165 m along, a little east
        val police = Facility(FacilityKind.POLICE, 35.1405, 126.92985) // ~55 m along, a little west
        val cctv = Facility(FacilityKind.CCTV, 35.1410, 126.9300) // not a help place
        val far = Facility(FacilityKind.EMERGENCY_BELL, 35.1410, 126.9320) // ~180 m off the route
        val guidance = Guidance(path, listOf(store, police, cctv, far))

        val list = guidance.helpAhead(0.0)
        assertEquals(listOf(FacilityKind.POLICE, FacilityKind.CONVENIENCE_STORE), list.map { it.kind })
        assertEquals(Turn.LEFT, list[0].side)
        assertEquals(Turn.RIGHT, list[1].side)
        // Walked past the police box: only the store is left, about 55 m ahead.
        val later = guidance.helpAhead(110.0)
        assertEquals(listOf(FacilityKind.CONVENIENCE_STORE), later.map { it.kind })
        assertTrue(later[0].aheadM in 45..65)
    }

    // Points given as meters east/north of a corner near 조선대.
    /** 도우미: 기준점(위도 35.14, 경도 126.93)에서 동쪽 [east]m, 북쪽 [north]m 떨어진 점을 만든다. 위도 1도는 약 111,320m로 계산한다. */
    private fun m(east: Double, north: Double) = LatLon(35.14 + north / 111_320.0, 126.93 + east / (111_320.0 * kotlin.math.cos(Math.toRadians(35.14))))

    /** 도우미: 경로를 처음부터 1m씩 걸으며 안내되는 왼쪽·오른쪽 회전을 모아 (회전 위치 m, 방향) 목록으로 돌려준다. */
    private fun turnsOf(g: Guidance): List<Pair<Int, Turn>> {
        val out = mutableListOf<Pair<Int, Turn>>()
        var p = 0.0
        while (p < g.totalM) {
            val s = g.atProgress(p)
            if (s.nextTurn == Turn.LEFT || s.nextTurn == Turn.RIGHT) {
                val at = (p + s.metersToNextTurn).toInt()
                if (out.none { kotlin.math.abs(it.first - at) <= 1 }) out += at to s.nextTurn
            }
            p += 1.0
        }
        return out
    }

    /** 역할: 여러 번 조금씩 꺾어 그린 둥근 모퉁이를 회전 한 번(오른쪽)으로 안내하는지 확인한다. */
    @Test
    fun aRoundedCornerIsOneTurn() {
        // North 100 m, then a right-angle corner drawn as four 22.5° bends, then east 100 m.
        val path = listOf(m(0.0, 0.0), m(0.0, 100.0), m(1.5, 103.5), m(4.0, 106.0), m(7.5, 108.5), m(11.0, 110.0), m(111.0, 110.0))
        assertEquals(listOf(Turn.RIGHT), turnsOf(Guidance(path, emptyList())).map { it.second })
    }

    /** 역할: 인도에서 옆으로 3m 비켜 가는 것은 회전이 아니고, 10m 건너편으로 길을 건너는 것은 회전 두 번인지 확인한다. */
    @Test
    fun aStepAsideIsNoTurnButCrossingIsTwo() {
        // A 3 m step to the side on the pavement: walk on, nothing to say.
        val step = listOf(m(0.0, 0.0), m(0.0, 100.0), m(3.0, 103.0), m(3.0, 200.0))
        assertTrue(turnsOf(Guidance(step, emptyList())).isEmpty())
        // Crossing a road to the other side (10 m over), then on: right, then left.
        val cross = listOf(m(0.0, 0.0), m(0.0, 100.0), m(10.0, 100.0), m(10.0, 200.0))
        assertEquals(listOf(Turn.RIGHT, Turn.LEFT), turnsOf(Guidance(cross, emptyList())).map { it.second })
    }

    /** 역할: 인도가 7m 옆으로 어긋날 때, 서버가 알려 준 횡단보도가 거기 없으면 회전으로 안내하지 않는지 확인한다. */
    @Test
    fun aJogWithoutACrossingIsNoTurnWhenTheServerListsCrossings() {
        // The pavement shifts 7 m over (90° right, 7 m, 90° left), as on our server's routes near 조선대.
        val jog = listOf(m(0.0, 0.0), m(0.0, 100.0), m(7.0, 100.0), m(7.0, 200.0))
        // Crossings not known (an older server): told as before.
        assertEquals(listOf(Turn.RIGHT, Turn.LEFT), turnsOf(Guidance(jog, emptyList())).map { it.second })
        // The server lists crossings, none here: walk on.
        assertTrue(turnsOf(Guidance(jog, emptyList(), crossings = listOf(m(0.0, 50.0)))).isEmpty())
        // The server lists a crossing right here: crossing over, two turns.
        assertEquals(
            listOf(Turn.RIGHT, Turn.LEFT),
            turnsOf(Guidance(jog, emptyList(), crossings = listOf(m(3.5, 100.0)))).map { it.second },
        )
    }

    /** 역할: 왼쪽·오른쪽·왼쪽으로 짧게 꺾이는 갈지자 길에서 진짜 왼쪽 회전 하나는 남기는지 확인한다. */
    @Test
    fun aZigzagKeepsItsRealTurn() {
        // Left 78°, right 81° 5 m on, left 88° 4 m on (back_hospital near 1117 m): the line ends up turned left.
        val path = listOf(m(0.0, 0.0), m(0.0, 100.0), m(-5.0, 101.0), m(-5.5, 105.0), m(-9.5, 105.3), m(-60.0, 106.0))
        assertEquals(listOf(Turn.LEFT), turnsOf(Guidance(path, emptyList())).map { it.second })
    }

    /** 역할: 30m에 걸쳐 40도쯤 완만하게 휘는 길은 회전으로 안내하지 않는지 확인한다. */
    @Test
    fun aGentleBendIsNoTurn() {
        // A road curving 40° over 30 m (OSM's walking steps do not call it a turn either).
        val path = listOf(m(0.0, 0.0), m(0.0, 100.0), m(3.0, 110.0), m(8.0, 120.0), m(12.0, 128.0), m(70.0, 200.0))
        assertTrue(turnsOf(Guidance(path, emptyList())).isEmpty())
    }

    /** 역할: 갔다가 바로 옆으로 되돌아오는 머리핀 길에서, GPS가 조금 튀어도 지금 걷는 쪽 위치를 유지하는지 확인한다. */
    @Test
    fun aHairpinKeepsTheSideBeingWalked() {
        // Up 100 m, a hairpin, and back down 12 m to the east.
        val path = listOf(m(0.0, 0.0), m(0.0, 100.0), m(12.0, 100.0), m(12.0, 0.0))
        val g = Guidance(path, emptyList())
        // 40 m up, GPS 5 m off towards the other side: still 40 m along, not 170 m.
        val s = g.update(m(5.0, 40.0), nearM = 35.0)
        assertTrue("progress ${s.progressM}", s.progressM in 30.0..50.0)
        // Without knowing where the walker was, the nearest side wins as before.
        assertTrue(g.update(m(7.0, 40.0)).progressM > 150)
    }
}
