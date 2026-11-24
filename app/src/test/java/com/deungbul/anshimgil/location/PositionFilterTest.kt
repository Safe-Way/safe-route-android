package com.deungbul.anshimgil.location

// 시험에 쓰는 도구들
import com.deungbul.anshimgil.data.LatLon          // 앱의 위치 한 점(위도, 경도)
import com.deungbul.anshimgil.data.distanceMeters  // 앱의 두 점 사이 거리(m) 계산 함수
import org.junit.Assert.assertNotNull              // assertNotNull(값): 값이 null이 아니어야(있어야) 통과
import org.junit.Assert.assertNull                 // assertNull(값): 값이 null(없음)이어야 통과
import org.junit.Assert.assertTrue                 // assertTrue(조건): 조건이 참이어야 통과
import org.junit.Test                              // @Test가 붙은 함수 하나하나가 시험 하나

/**
 * 지도 위 '내 위치' 표시를 안정시키는 필터 시험.
 *
 * 휴대폰은 GPS, 와이파이, 기지국으로 위치를 동시에 알려 주는데, 거친 위치는 수십 m씩 엉뚱한 곳에 찍혀
 * 내 위치 표시가 왔다 갔다 했다. 앱의 PositionFilter가 이것을 걸러 낸다.
 * - 직전 좋은 위치보다 훨씬 거친 위치(오차 30m 넘고 직전 오차의 2배 넘음)는 무시한다.
 * - 위치 자체의 오차보다 작게 움직인 것(오차의 60%, 최소 3m 미만)은 서 있는 것으로 보고 무시한다.
 * - 받아들인 위치는 바로 옮기지 않고 지금 표시와 섞는다(정확한 위치일수록 많이 옮긴다).
 * - 8초 동안 받아들인 위치가 없으면 무엇이 오든 그대로 받는다.
 *
 * 여기서는 진짜 GPS를 쓰지 않는다. 위치(위도·경도), 오차(m), 시각(ms)을 직접 만들어
 * PositionFilter.accept(위치, 오차, 시각)에 넣는다. accept()는 지도에 새로 보여 줄 위치를 돌려주고,
 * 지금 표시를 그대로 두어야 하면 null을 돌려준다.
 */
class PositionFilterTest {
    // 시험의 출발 위치. 위도 35.14, 경도 126.93 (조선대 근처)
    private val start = LatLon(35.1400, 126.9300)

    /**
     * A point [north] meters north of [start].
     * 도우미: 출발 위치에서 북쪽으로 [m]미터 떨어진 점을 만든다(음수면 남쪽). 위도 1도는 약 111,320m로 계산한다.
     */
    private fun north(m: Double) = LatLon(start.lat + m / 111_320.0, start.lon)

    /** 역할: 정확한 GPS 위치 바로 뒤에 온 거친(오차 80m) 네트워크 위치는 무시하는지 확인한다. */
    @Test
    fun roughNetworkFixRightAfterGpsIsIgnored() {
        val f = PositionFilter()
        f.accept(start, 5f, 0)
        assertNull(f.accept(north(60.0), 80f, 1_000))
    }

    /**
     * 역할: 가만히 서 있을 때 GPS가 몇 m씩 흔들려도 지도 위 내 위치가 움직이지 않는지 확인한다.
     * 정확도의 60%(최소 3m)보다 덜 움직인 위치는 오차 안의 흔들림으로 보고 버린다(null).
     */
    @Test
    fun jitterWhileStandingStillDoesNotMoveTheMarker() {
        // 새 필터를 만든다. 아직 보여 준 위치가 없다.
        val f = PositionFilter()
        // 0초: 출발 위치, 오차 10m. 첫 위치는 무조건 받아서 지도에 보여 준다(돌려준 값은 여기서 보지 않음).
        f.accept(start, 10f, 0)
        // 1초 뒤: 북쪽 4m, 오차 10m. 무시 기준은 오차의 60% = 6m(최소 3m보다 큼). 4m < 6m라 흔들림으로 보고 null
        assertNull(f.accept(north(4.0), 10f, 1_000))
        // 2초 뒤: 남쪽 3m. 앞 위치는 버려졌으니 표시는 아직 출발 위치. 3m < 6m라 역시 null(표시가 안 움직임)
        assertNull(f.accept(north(-3.0), 10f, 2_000))
    }

    /** 역할: 실제로 걸어서 20m 움직이면 표시도 따라 움직이되, 한 번에 튀지 않고 섞여서 부드럽게 움직이는지 확인한다. */
    @Test
    fun walkingMovesTheMarkerSmoothly() {
        val f = PositionFilter()
        f.accept(start, 5f, 0)
        val p = assertNotNull(f.accept(north(10.0), 5f, 1_000)).let { f.accept(north(20.0), 5f, 2_000) }
        assertNotNull(p)
        val moved = distanceMeters(start.lat, start.lon, p!!.lat, p.lon)
        // Most of the way, but blended rather than jumping all 20 m.
        assertTrue(moved in 10..20)
    }

    /** 역할: 오래(20초) 좋은 위치가 없었다면 거친 위치라도 받아들이는지 확인한다(건물에서 나올 때 등). */
    @Test
    fun afterAWhileWithoutGoodFixesTakesWhatComes() {
        val f = PositionFilter()
        f.accept(start, 5f, 0)
        assertNotNull(f.accept(north(60.0), 80f, 20_000))
    }
}
