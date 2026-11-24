package com.deungbul.anshimgil.location

import com.deungbul.anshimgil.data.LatLon
import com.deungbul.anshimgil.data.distanceMeters
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionFilterTest {
    private val start = LatLon(35.1400, 126.9300)

    /** A point [north] meters north of [start]. */
    private fun north(m: Double) = LatLon(start.lat + m / 111_320.0, start.lon)

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
        val f = PositionFilter()
        f.accept(start, 10f, 0)
        assertNull(f.accept(north(4.0), 10f, 1_000))
        assertNull(f.accept(north(-3.0), 10f, 2_000))
    }

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

    @Test
    fun afterAWhileWithoutGoodFixesTakesWhatComes() {
        val f = PositionFilter()
        f.accept(start, 5f, 0)
        assertNotNull(f.accept(north(60.0), 80f, 20_000))
    }
}
