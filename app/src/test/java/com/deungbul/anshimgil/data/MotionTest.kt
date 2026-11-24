package com.deungbul.anshimgil.data

// 시험에 쓰는 도구들
import org.junit.Assert.assertEquals    // assertEquals(기대값, 실제값): 둘이 같아야 통과(소수는 허용 오차를 함께 줌)
import org.junit.Assert.assertFalse     // assertFalse(조건): 조건이 거짓이어야 통과
import org.junit.Assert.assertNull      // assertNull(값): 값이 null(없음)이어야 통과
import org.junit.Assert.assertTrue      // assertTrue(조건): 조건이 참이어야 통과
import org.junit.Test                   // @Test가 붙은 함수 하나하나가 시험 하나

/**
 * 걷는 사람의 움직임 판단 시험(속도, 가는 방향, 차를 탔는지, 지도·화살표가 볼 방향).
 *
 * 앱의 MotionTracker는 최근 10초 동안의 위치들로 속도(speedMps), 가는 방향(courseDeg),
 * 차나 버스를 탔는지(inVehicle)를 계산한다. 차를 탔다고 보면 걸음 안내를 잠시 멈춘다.
 * - 초속 6m(약 22km/h, VEHICLE_SPEED_MPS) 이상이 8초(VEHICLE_AFTER_MS) 이어지고,
 *   위치 사이 한 칸 한 칸도 빠른 걸음이 3번(VEHICLE_FAST_STEPS) 넘게 이어져야 '차'로 본다.
 * - 초속 3m(약 11km/h, WALK_AGAIN_SPEED_MPS) 이하가 15초(WALK_AGAIN_AFTER_MS) 이어져야 다시 '걷기'로 본다.
 * - 오차가 20m(MOTION_MAX_ACCURACY_M)보다 큰 위치는 계산에서 뺀다.
 * 그 밖에 guideHeading()은 지도가 바라볼 방향, arrowHeading()은 내 화살표 방향,
 * MovingState는 걷는 중인지 멈췄는지를 정한다.
 *
 * 여기서는 진짜 GPS를 쓰지 않는다. 출발점에서 북쪽으로 몇 m 간 위치와 시각(ms)을 직접 만들어
 * MotionTracker.add(위치, 시각)에 차례로 넣고, inVehicle·speedMps·courseDeg 값을 확인한다.
 * add()는 아무것도 돌려주지 않고, 넣을 때마다 이 세 값을 새로 고친다.
 */
class MotionTest {
    // 시험의 출발 위치. 위도 35.146, 경도 126.92
    private val start = LatLon(35.1460, 126.9200)

    /**
     * A point [meters] north of [start].
     * 도우미: 출발 위치에서 북쪽으로 [meters]미터 떨어진 점을 만든다. 위도 1도는 약 111,320m로 계산한다.
     */
    private fun north(meters: Double) = LatLon(start.lat + meters / 111_320.0, start.lon)

    /** 역할: 초속 1.3m로 북쪽으로 걸으면 차로 보지 않고, 속도 1.3m/s와 방향 0도(북쪽)를 맞게 내는지 확인한다. */
    @Test
    fun walkingIsNotAVehicleAndHasACourse() {
        val m = MotionTracker()
        for (s in 0..30) m.add(north(s * 1.3), s * 1000L) // 1.3 m/s, a normal walk
        assertFalse(m.inVehicle)
        assertEquals(1.3, m.speedMps!!, 0.1)
        assertEquals(0f, m.courseDeg!!, 2f) // going north
    }

    /**
     * 역할: 차(버스)를 탔는지를 몇 초가 아니라 한동안 지켜보고 판단하는지 확인한다.
     * 내린 뒤에도 잠시 '차'로 유지해서, 버스가 빨간불에 설 때마다 걸음 안내가 켜졌다 꺼졌다 하지 않게 한다.
     */
    @Test
    fun aCarIsNoticedAfterAWhileAndWalkingAgainLater() {
        // 새 움직임 추적기를 만든다. 처음에는 '차 아님'(inVehicle = false)이다.
        val m = MotionTracker()
        // t: 지금 시각(ms), at: 출발점에서 북쪽으로 간 거리(m)
        var t = 0L
        var at = 0.0
        // 1초마다 10m씩 5번 = 초속 10m(시속 36km). 버스 속도다. 오차를 안 넣으면 0(모름)이라 빼지 않고 다 쓴다.
        repeat(5) { at += 10.0; t += 1000; m.add(north(at), t) } // 36 km/h
        // 빠르게 움직인 지 아직 2초뿐(속도는 위치가 2초 이상 모여야 계산됨). 8초가 안 됐으니 아직 '차 아님'
        assertFalse("not after only a few seconds", m.inVehicle)
        // 같은 속도로 15초 더 간다. 빠른 상태가 8초를 넘고 빠른 걸음도 3번 넘게 이어졌다.
        repeat(15) { at += 10.0; t += 1000; m.add(north(at), t) }
        // 이제 '차를 탐'(true)이어야 한다.
        assertTrue(m.inVehicle)
        // Off the bus, walking: stays "vehicle" for a while so a red light does not switch it.
        // 버스에서 내려 1초에 1.2m씩(보통 걸음) 5초 걷는다. 최근 10초 평균 속도는 아직 초속 5.6m 이상이라
        // '걷기'로 돌아가는 기준(초속 3m 이하 15초)에 한참 못 미친다.
        repeat(5) { at += 1.2; t += 1000; m.add(north(at), t) }
        // 그래서 아직 '차'로 남아 있어야 한다(빨간불에 잠깐 선 버스와 구별하려는 것).
        assertTrue(m.inVehicle)
        // 30초 더 걷는다. 평균 속도가 초속 3m 아래로 내려간 뒤 15초가 지난다.
        repeat(30) { at += 1.2; t += 1000; m.add(north(at), t) }
        // 이제 다시 '걷기'(false)여야 한다.
        assertFalse(m.inVehicle)
    }

    /** 역할: 제자리에서 GPS만 2m씩 흔들릴 때는 가는 방향을 정하지 않는지(null) 확인한다. */
    @Test
    fun standingStillHasNoCourse() {
        val m = MotionTracker()
        for (s in 0..10) m.add(north(if (s % 2 == 0) 0.0 else 2.0), s * 1000L) // GPS jitter
        assertNull(m.courseDeg)
    }

    /** 역할: 지도가 볼 방향을 경로 위에서는 경로 앞쪽, 경로 밖에서 걸을 때는 GPS 방향, 서 있을 때는 나침반으로 정하는지 확인한다. */
    @Test
    fun headingFollowsTheRouteWhileWalkingOnIt() {
        // Walking north on the route; the compass says south (phone held upright, flipped): the route wins.
        assertEquals(0f, guideHeading(onRoute = true, moving = true, routeAhead = 0f, course = 5f, compass = 180f))
        // Off the route: the way they actually go.
        assertEquals(90f, guideHeading(onRoute = false, moving = true, routeAhead = 0f, course = 90f, compass = 180f))
        // On the route standing still too: the route ahead, not the compass (switching swung the map, 11/9).
        assertEquals(0f, guideHeading(onRoute = true, moving = false, routeAhead = 0f, course = null, compass = 180f))
        // Standing still off the route: where the phone points.
        assertEquals(180f, guideHeading(onRoute = false, moving = false, routeAhead = 0f, course = 90f, compass = 180f))
        assertEquals(0f, guideHeading(onRoute = false, moving = false, routeAhead = 0f, course = null, compass = null))
    }

    /** 역할: 모퉁이에서 잠깐 느려져도 '걷는 중'을 유지하고, 3초 넘게 느려야 '멈춤'으로 바뀌는지 확인한다. */
    @Test
    fun movingDoesNotFlickAtACorner() {
        val m = MovingState()
        assertEquals(true, m.update(1.0, 0))
        // Slowing at a corner for a moment, or between the two speeds: still moving.
        assertEquals(true, m.update(0.2, 1_000))
        assertEquals(true, m.update(0.4, 2_000))
        assertEquals(true, m.update(0.2, 3_000))
        assertEquals(true, m.update(0.2, 5_500))
        // Slow for 3 s: stopped.
        assertEquals(false, m.update(0.1, 6_100))
        assertEquals(false, m.update(0.5, 7_000))
        assertEquals(true, m.update(0.7, 8_000))
    }

    /** 역할: 내 화살표는 걷든 서 있든 나침반 방향을 따르고, 나침반이 없으면 안내 방향을 쓰는지 확인한다. */
    @Test
    fun standingStillTheArrowTurnsWithTheWalker() {
        assertEquals(200f, arrowHeading(moving = false, heading = 0f, compass = 200f))
        assertEquals(200f, arrowHeading(moving = true, heading = 0f, compass = 200f))
        assertEquals(0f, arrowHeading(moving = false, heading = 0f, compass = null))
    }

    /** 역할: 경로 위에서도 경로 방향과 45도 이상 다르게 걸으면 지도가 실제 가는 방향을 따르는지 확인한다. */
    @Test
    fun walkingAnotherWayTheMapFollowsTheCourse() {
        assertEquals(90f, guideHeading(onRoute = true, moving = true, routeAhead = 0f, course = 90f, compass = null))
        assertEquals(0f, guideHeading(onRoute = true, moving = true, routeAhead = 0f, course = 30f, compass = null))
    }

    /** 역할: 걷는 중 거친 위치가 섞이거나 위치가 한 번 70m 튀어도 차를 탄 것으로 보지 않는지 확인한다. */
    @Test
    fun roughOrJumpingFixesAreNotARide() {
        // m26 (영상 9:40): walking, with rough fixes far off now and then.
        val rough = MotionTracker()
        var t = 0L
        var at = 0.0
        repeat(40) { s ->
            at += 1.3; t += 1000
            if (s % 3 == 0) rough.add(north(at + 80), t, accuracyM = 45f) else rough.add(north(at), t, accuracyM = 6f)
        }
        assertFalse(rough.inVehicle)
        // Walking with a single 70 m jump that looks precise: one fast step, not a ride.
        val jump = MotionTracker()
        t = 0L; at = 0.0
        repeat(40) { s ->
            at += 1.3; t += 1000
            jump.add(north(if (s in 10..25) at + 70 else at), t, accuracyM = 8f)
        }
        assertFalse(jump.inVehicle)
    }
}
