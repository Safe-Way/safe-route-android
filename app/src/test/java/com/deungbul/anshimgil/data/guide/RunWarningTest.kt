package com.deungbul.anshimgil.data.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import com.deungbul.anshimgil.data.route.LatLon

class RunWarningTest {

    /** Acceleration size swinging around gravity at [stepHz] with [swing] m/s², read 50 times a second. */
    private fun feed(d: RunDetector, stepHz: Double, swing: Double, ms: Long = 2_000) {
        var t = 0L
        while (t <= ms) {
            d.addSize(9.8 + swing * sin(2 * PI * stepHz * t / 1000.0), t)
            t += 20
        }
    }

    @Test
    fun runningShakeIsRunning() {
        val d = RunDetector()
        feed(d, stepHz = 2.8, swing = 8.0)
        assertTrue(d.running)
    }

    @Test
    fun walkingIsNotRunning() {
        val d = RunDetector()
        feed(d, stepHz = 1.8, swing = 2.5)
        assertFalse(d.running)
    }

    @Test
    fun fastSmallShakingIsNotRunning() {
        // A phone jiggled in the hand: quick but weak.
        val d = RunDetector()
        feed(d, stepHz = 3.0, swing = 2.0)
        assertFalse(d.running)
    }

    @Test
    fun tooShortIsNotDecided() {
        val d = RunDetector()
        feed(d, stepHz = 2.8, swing = 8.0, ms = 800)
        assertFalse(d.running)
    }

    @Test
    fun gpsRunningSpeedIsRunningButRidingIsNot() {
        val d = RunDetector()
        d.addSpeed(3.0)
        assertTrue(d.running)
        d.addSpeed(8.0)
        assertFalse(d.running)
        d.addSpeed(1.2)
        assertFalse(d.running)
    }

    @Test
    fun warnsAboutCrossingAheadOnce() {
        val a = runAlert(100.0, listOf(50.0, 120.0, 300.0), Turn.STRAIGHT, 200, emptySet())!!
        assertEquals(RUN_CROSSING_TEXT, a.text)
        assertEquals(20, a.aheadM)
        assertNull(runAlert(105.0, listOf(50.0, 120.0, 300.0), Turn.STRAIGHT, 195, setOf(a.key)))
    }

    @Test
    fun crossingTooFarOrPassedIsNotWarned() {
        assertNull(runAlert(100.0, listOf(50.0, 140.0), Turn.STRAIGHT, 200, emptySet()))
    }

    @Test
    fun warnsAboutTurnAheadWhenNoCrossing() {
        val a = runAlert(100.0, emptyList(), Turn.LEFT, 25, emptySet())!!
        assertEquals(RUN_TURN_TEXT, a.text)
        // GPS wobble moves the turn a meter or two: still the same place.
        assertNull(runAlert(102.0, emptyList(), Turn.LEFT, 24, setOf(a.key)))
        assertNull(runAlert(100.0, emptyList(), Turn.STRAIGHT, 25, emptySet()))
        assertNull(runAlert(100.0, emptyList(), Turn.ARRIVE, 10, emptySet()))
    }

    @Test
    fun crossingsAlongRoute() {
        val path = listOf(LatLon(35.0, 126.0), LatLon(35.0, 126.002))
        val near = LatLon(35.00005, 126.001) // ~5 m off, ~91 m along
        val far = LatLon(35.001, 126.001) // ~110 m off
        val along = Guidance(path, emptyList(), listOf(near, far)).crossingsAlongM
        assertEquals(1, along.size)
        assertEquals(91.0, along[0], 2.0)
    }
}
