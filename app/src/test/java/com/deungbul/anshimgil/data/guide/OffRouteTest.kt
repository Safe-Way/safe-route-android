package com.deungbul.anshimgil.data.guide

// 시험에 쓰는 도구들
import org.junit.Assert.assertEquals    // assertEquals(기대값, 실제값): 둘이 같아야 통과
import org.junit.Assert.assertFalse     // assertFalse(조건): 조건이 거짓이어야 통과
import org.junit.Assert.assertTrue      // assertTrue(조건): 조건이 참이어야 통과
import org.junit.Test                   // @Test가 붙은 함수 하나하나가 시험 하나
import com.deungbul.anshimgil.data.route.LatLon

/**
 * '경로 벗어남' 판단 시험.
 *
 * 앱은 걷는 사람이 경로에서 너무 멀어지면 길을 벗어난 것으로 본다. 기준 거리(허용 거리)는
 * 보통 25m(OFF_ROUTE_M)이고, 회전 지점(횡단보도) 30m(TURN_NEAR_M) 안에서는 길이 넓어서 40m(OFF_ROUTE_AT_CROSSING_M)다.
 * 또 GPS가 한 번 튄 것으로 착각하지 않도록, 허용 거리를 넘은 위치가 2번(OFF_ROUTE_FIXES) 연속 나와야 벗어난 것으로 본다.
 *
 * 여기서는 진짜 GPS를 쓰지 않는다. '경로에서 떨어진 거리(m)'와 '허용 거리(m)' 숫자를 직접 넣는다.
 * - atCrossing(위치, 회전 지점 목록): 회전 지점 가까이면 true를 돌려준다.
 * - offRouteLimitM(횡단보도인지): 그곳의 허용 거리(25 또는 40)를 돌려준다.
 * - OffRouteCounter.update(떨어진 거리, 허용 거리): 허용 거리를 넘은 횟수를 연속으로 세어,
 *   벗어난 것으로 봐야 하면 true, 아니면 false를 돌려준다. 한 번이라도 허용 거리 안이면 횟수는 0으로 돌아간다.
 */
class OffRouteTest {
    /** 역할: 회전 지점 가까이(20m)는 횡단보도로 보고 먼 곳(60m)은 아니며, 허용 거리가 횡단보도 40m·그 밖 25m인지 확인한다. */
    @Test
    fun aCrossingAllowsMore() {
        val turn = TurnMark(LatLon(35.1460, 126.9200), left = true)
        val near = LatLon(35.1460 + 20 / 111_320.0, 126.9200)
        val far = LatLon(35.1460 + 60 / 111_320.0, 126.9200)
        assertTrue(atCrossing(near, listOf(turn)))
        assertFalse(atCrossing(far, listOf(turn)))
        assertEquals(40, offRouteLimitM(atCrossing = true))
        assertEquals(25, offRouteLimitM(atCrossing = false))
    }

    /**
     * 역할: GPS가 한 번 튄 것은 '길 벗어남'으로 보지 않고, 두 번 연속 벗어나야 벗어난 것으로 보는지 확인한다.
     * 횡단보도처럼 넓은 곳은 허용 거리가 더 크다. 잘못 판단하면 경로 재탐색과 보호자 알림이 엉뚱하게 나간다.
     */
    @Test
    fun oneJumpIsNotLeavingTwoInARowIs() {
        // 새 '벗어남 세기' 도구를 만든다. 연속으로 넘은 횟수는 0에서 시작한다.
        val c = OffRouteCounter()
        // 30m 떨어짐, 허용 25m → 넘음 1번째. 아직 한 번뿐이라 GPS가 튄 것일 수 있어 false
        assertFalse(c.update(30, 25)) // one fix past: maybe GPS
        // 10m 떨어짐 → 허용 거리 안. 경로로 돌아왔으니 횟수가 0으로 돌아가고 false
        assertFalse(c.update(10, 25)) // back on it
        // 다시 30m → 넘음 1번째(앞의 넘음은 끊겼으므로 처음부터 다시 셈). 아직 false
        assertFalse(c.update(30, 25))
        // 32m → 넘음 2번째. 두 번 연속 넘었으니 이제 벗어남(true)
        assertTrue(c.update(32, 25)) // two in a row: off
        // 횡단보도라 허용 거리가 40m. 35m는 그 안이므로 경로 위로 보고 false(횟수도 0으로 돌아감)
        assertFalse(c.update(35, 40)) // at a crossing 35 m is still on it
    }
}
