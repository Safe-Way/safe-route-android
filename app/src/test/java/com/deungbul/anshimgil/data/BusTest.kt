package com.deungbul.anshimgil.data

// 시험에 쓰는 도구들
import org.json.JSONObject              // 서버 답(JSON)을 코드로 만들 때 씀
import org.junit.Assert.assertEquals    // assertEquals(기대값, 실제값): 둘이 같아야 통과
import org.junit.Test                   // @Test가 붙은 함수 하나하나가 시험 하나

/**
 * 가까운 버스 정류장 시험.
 *
 * 앱은 홈 화면의 "가까운 정류장" 창을 열 때 서버(GET /api/bus/nearby)에 지금 위치를 보내고,
 * 근처 정류장과 그 정류장에 곧 오는 버스 목록을 받는다. 창에는 정류장마다 버스를 4대까지 보여 준다.
 *
 * 여기서는 진짜 서버를 부르지 않는다. 서버 답 모양 그대로 만든 JSON을 앱의 읽기 함수
 * parseBusNearby()에 넣고, 그 결과를 다시 앱의 순서 정하기 함수 busesToShow()에 넣어 본다.
 * - parseBusNearby()는 정류장 목록(NearbyStop)을 돌려준다. 정류장마다 이름, 거리(m), 오는 버스(BusComing) 목록이 있다.
 * - busesToShow()는 버스 목록을 화면에 보여 줄 순서로 다시 줄 세워 돌려준다.
 *   먼저 빨리 오는 순으로 세우고, 노인(SENIOR)·휠체어(DISABLED) 사용자일 때만 저상 버스를 앞으로 옮긴다.
 */
class BusTest {
    /**
     * 가짜 서버 답. 정류장 하나("조선대해오름관", 31m 거리)에 버스 세 대가 온다.
     * - 27번: 5분 뒤, 일반 버스(lowFloor=false)
     * - 45번: 3분 뒤, 저상 버스(lowFloor=true)
     * - 03번: 4분 뒤, 저상 버스(lowFloor=true)
     * 서버 칸 이름 remainMin(남은 분)은 앱에서 inMin, lineName(노선 번호)은 line으로 읽힌다.
     */
    private val json = JSONObject(
        """{"stops":[{"name":"조선대해오름관","nextStop":"종점","lat":35.1401,"lon":126.9319,"distanceM":31,"arrivals":[
            {"lineName":"27","remainMin":5,"remainStop":2,"lowFloor":false},
            {"lineName":"45","remainMin":3,"remainStop":2,"lowFloor":true},
            {"lineName":"03","remainMin":4,"remainStop":3,"lowFloor":true}]}]}""",
    )

    /** 역할: 서버 답에서 정류장 이름, 거리, 오는 버스 수를 빠짐없이 읽는지 확인한다. */
    @Test
    fun readsStopsAndBuses() {
        val stop = parseBusNearby(json).single()
        assertEquals("조선대해오름관", stop.name)
        assertEquals(31, stop.distanceM)
        assertEquals(3, stop.buses.size)
    }

    /**
     * 역할: 휠체어·노인 사용자에게는 더 빨리 오는 일반 버스보다 저상 버스를 먼저 보여 주는지 확인한다.
     * 일반 사용자는 도착 순서 그대로 본다.
     */
    @Test
    fun lowFloorBusesComeFirstForWheelchairAndSenior() {
        // 서버 답을 읽어 정류장 하나(single: 꼭 하나여야 함)의 버스 목록을 꺼낸다.
        val buses = parseBusNearby(json).single().buses
        // 휠체어 사용자: 저상 버스 45번(3분), 03번(4분)이 먼저, 일반 버스 27번(5분)이 마지막이어야 한다.
        assertEquals(listOf("45", "03", "27"), busesToShow(buses, UserMode.DISABLED).map { it.line })
        // 일반 사용자: 빨리 오는 순서(3분, 4분, 5분)라서 마침 같은 순서가 나와야 한다.
        assertEquals(listOf("45", "03", "27"), busesToShow(buses, UserMode.GENERAL).map { it.line })
        // 27번(일반 버스)만 1분 뒤에 오도록 바꾼 목록을 만든다. 이제 27번이 가장 빨리 온다.
        val late = buses.map { if (it.line == "27") it.copy(inMin = 1) else it }
        // 일반 사용자: 가장 빨리 오는 27번이 맨 앞이어야 한다.
        assertEquals("27", busesToShow(late, UserMode.GENERAL).first().line)
        // 노인 사용자: 27번이 더 빨리 와도 저상 버스 중 가장 빠른 45번이 맨 앞이어야 한다.
        assertEquals("45", busesToShow(late, UserMode.SENIOR).first().line)
    }
}
