package com.deungbul.anshimgil.data.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NearestSameBrandTest {
    private fun place(name: String, distanceM: Int?) = Place(null, name, "", 35.15, 126.92, distanceM)

    @Test
    fun brandIsTheFirstWord() {
        assertEquals("스타벅스", brandOf("스타벅스 광주용봉점"))
        assertEquals("이디야커피", brandOf("이디야커피"))
        assertEquals("A 마트", brandOf("A 마트"))
    }

    @Test
    fun picksTheNearestOtherBranchWithinWalkingDistance() {
        val far = place("스타벅스 광주용봉점", 5_400)
        val results = listOf(
            place("스타벅스 광주지산DT점", 1_100),
            place("스타벅스 조선대국제관점", 235),
            place("스타방스고시텔", 100),
            far,
        )
        assertEquals("스타벅스 조선대국제관점", nearestSameBrand(far, results)?.name)
    }

    @Test
    fun noneWhenEveryBranchIsTooFar() {
        val far = place("스타벅스 광주용봉점", 5_400)
        assertNull(nearestSameBrand(far, listOf(place("스타벅스 광주첨단점", 4_000), far)))
    }
}
