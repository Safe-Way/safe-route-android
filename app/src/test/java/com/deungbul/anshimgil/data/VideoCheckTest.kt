package com.deungbul.anshimgil.data

import com.deungbul.anshimgil.ui.guide.steadyArrival
import com.deungbul.anshimgil.ui.home.detailParts
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

/** Things the 11/9 demo video showed. */
class VideoCheckTest {
    @Test
    fun detailsLeaveOutTheDataSource() {
        assertEquals(listOf("계단", "길이 58m", "경사로 없음"), detailParts("계단 · 길이 58m · 경사로 없음 (OSM)"))
    }

    @Test
    fun arrivalTimeDoesNotFlick() {
        val shown = LocalTime.of(10, 1)
        assertEquals(shown, steadyArrival(shown, LocalTime.of(10, 2)))
        assertEquals(shown, steadyArrival(shown, LocalTime.of(10, 0, 30)))
        assertEquals(LocalTime.of(10, 4), steadyArrival(shown, LocalTime.of(10, 4)))
    }
}
