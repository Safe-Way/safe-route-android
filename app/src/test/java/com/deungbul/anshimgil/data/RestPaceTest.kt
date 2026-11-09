package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestPaceTest {
    /** [mps] meters a second along the route for [seconds], one reading a second. */
    private fun walk(w: PaceWatch, mps: Double, seconds: Int, from: Double = 0.0, startMs: Long = 0) {
        for (s in 0..seconds) w.add(from + mps * s, startMs + s * 1000L)
    }

    @Test
    fun slowingDownForAWhileIsSlow() {
        val w = PaceWatch(usualMps = 0.9)
        walk(w, 0.4, 100)
        assertTrue(w.slow)
    }

    @Test
    fun usualPaceOrShortOrStoppedIsNot() {
        PaceWatch(0.9).also { walk(it, 0.85, 100); assertFalse(it.slow) }
        // Not a full minute and a half yet.
        PaceWatch(0.9).also { walk(it, 0.3, 60); assertFalse(it.slow) }
        // Standing still (a long red light, or resting already).
        PaceWatch(0.9).also { walk(it, 0.0, 100); assertFalse(it.slow) }
    }

    @Test
    fun aWaitAtACrossingInANormalWalkIsNotSlow() {
        val w = PaceWatch(0.9)
        walk(w, 0.9, 60)
        walk(w, 0.0, 30, from = 54.0, startMs = 61_000) // 30 s at a red light
        walk(w, 0.9, 30, from = 54.0, startMs = 92_000)
        assertFalse(w.slow)
    }

    @Test
    fun picksTheBestKindNearby() {
        val here = LatLon(35.0, 126.0)
        fun at(kind: FacilityKind, eastM: Double, name: String? = null) =
            Facility(kind, 35.0, 126.0 + eastM / 91_290.0, name = name)
        val list = listOf(
            at(FacilityKind.CONVENIENCE_STORE, 20.0), at(FacilityKind.TOILET, 80.0),
            at(FacilityKind.HEAT_SHELTER, 150.0, "남구 경로당"), at(FacilityKind.HEAT_SHELTER, 400.0), at(FacilityKind.CCTV, 5.0),
        )
        val p = restPlace(list, here)!!
        assertEquals(FacilityKind.HEAT_SHELTER, p.facility.kind)
        assertEquals("남구 경로당", restName(p))
        assertEquals("걸음이 조금 느려지셨어요. ${p.meters}m 거리 남구 경로당에서 쉬었다 가실래요? 조금 돌아가야 해요.", restOfferText(p))
        assertNull(restPlace(listOf(at(FacilityKind.TOILET, 300.0)), here))
    }

    @Test
    fun onlySeniorsAndWheelchairUsersAreAsked() {
        assertTrue(asksToRest(UserMode.SENIOR))
        assertTrue(asksToRest(UserMode.DISABLED))
        assertFalse(asksToRest(UserMode.CHILD))
        assertFalse(asksToRest(UserMode.GENERAL))
    }

    @Test
    fun demoWalkSlowsDownOnceAndIsNoticed() {
        assertEquals(8.0, demoStepM(5, 8.0, slows = true), 0.0)
        assertEquals(8.0 * DEMO_SLOW_SHARE, demoStepM(DEMO_SLOW_FROM_S, 8.0, slows = true), 1e-9)
        assertEquals(8.0, demoStepM(DEMO_SLOW_FROM_S + DEMO_SLOW_FOR_S, 8.0, slows = true), 0.0)
        assertEquals(8.0, demoStepM(DEMO_SLOW_FROM_S, 8.0, slows = false), 0.0)
        // The demo's own watch notices the slowing within its window.
        val w = PaceWatch(8.0, DEMO_SLOW_WINDOW_MS)
        var m = 0.0
        var noticedAt: Int? = null
        for (s in 0 until DEMO_SLOW_FROM_S + DEMO_SLOW_FOR_S) {
            m += demoStepM(s, 8.0, slows = true)
            w.add(m, s * 1000L)
            if (noticedAt == null && w.slow) noticedAt = s
        }
        assertTrue(noticedAt != null && noticedAt in DEMO_SLOW_FROM_S..DEMO_SLOW_FROM_S + 25)
    }

    @Test
    fun restPlaceAheadOnTheWayComesFirst() {
        // A route going east; the walker is 200 m along.
        val path = listOf(LatLon(35.0, 126.0), LatLon(35.0, 126.0 + 600 / 91_290.0))
        val g = Guidance(path, emptyList())
        fun at(kind: FacilityKind, alongM: Double, offM: Double = 10.0, name: String? = null) =
            Facility(kind, 35.0 + offM / 111_320.0, 126.0 + alongM / 91_290.0, name = name)
        val here = LatLon(35.0, 126.0 + 200 / 91_290.0)
        val behindShelter = at(FacilityKind.HEAT_SHELTER, 120.0, name = "지나온 쉼터")
        val aheadToilet = at(FacilityKind.TOILET, 280.0, name = "앞 화장실")
        val farAhead = at(FacilityKind.HEAT_SHELTER, 590.0)
        val p = restPlaceOnTheWay(listOf(behindShelter, aheadToilet, farAhead), here, 200.0) { g.alongAndOff(LatLon(it.lat, it.lon)) }!!
        assertEquals("앞 화장실", restName(p))
        assertTrue(p.ahead)
        assertEquals("걸음이 조금 느려지셨어요. 가는 길 ${p.meters}m 앞 앞 화장실에서 쉬었다 가실래요?", restOfferText(p))
        // Nothing ahead: the one behind, turning back.
        val back = restPlaceOnTheWay(listOf(behindShelter, farAhead), here, 200.0) { g.alongAndOff(LatLon(it.lat, it.lon)) }!!
        assertEquals("지나온 쉼터", restName(back))
        assertFalse(back.ahead)
    }
}
