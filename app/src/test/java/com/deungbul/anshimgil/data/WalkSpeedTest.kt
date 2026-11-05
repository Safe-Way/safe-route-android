package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WalkSpeedTest {
    private fun walks(speed: Double, n: Int) = List(n) { WalkSample(speed, 400, 400, "2026-11-05") }

    @Test
    fun speedMovesFromTheModeDefaultTowardsTheWalkersOwn() {
        // The team's example: senior mode (60), always walking at 45.
        assertEquals(60.0, personalSpeed(UserMode.SENIOR, emptyList()), 0.01)
        assertEquals(57.5, personalSpeed(UserMode.SENIOR, walks(45.0, 1)), 0.01)
        assertEquals(52.5, personalSpeed(UserMode.SENIOR, walks(45.0, 5)), 0.01)
        assertEquals(50.0, personalSpeed(UserMode.SENIOR, walks(45.0, 10)), 0.01)
        // Only the newest 20 walks count.
        assertEquals(personalSpeed(UserMode.SENIOR, walks(45.0, 20)), personalSpeed(UserMode.SENIOR, walks(45.0, 30)), 0.01)
    }

    @Test
    fun sampleSurvivesStorage() {
        val s = WalkSample(52.25, 640, 735, "2026-11-05")
        assertEquals(s.copy(speedMPerMin = 52.25), WalkSample.decode(s.encode()))
    }

    /** A point [north] meters north. */
    private fun north(m: Double) = LatLon(35.14 + m / 111_320.0, 126.93)

    @Test
    fun meterLeavesOutStopsAndShortWalks() {
        val meter = WalkMeter()
        var t = 0L
        // 400 m at 60 m/min: 1 m per second, a fix every 5 s.
        for (i in 0..80) { meter.add(north(i * 5.0), t); t += 5_000 }
        // Then stand at a crossing for a minute.
        repeat(12) { meter.add(north(400.0), t); t += 5_000 }
        val sample = meter.sample("2026-11-05")
        assertNotNull(sample)
        assertEquals(60.0, sample!!.speedMPerMin, 2.0)

        val short = WalkMeter()
        short.add(north(0.0), 0); short.add(north(100.0), 100_000)
        assertNull(short.sample("2026-11-05"))
    }
}
