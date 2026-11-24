package com.deungbul.anshimgil.data.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.deungbul.anshimgil.data.place.FacilityKind

class IconMeaningTest {
    @Test
    fun aQuestionAboutAPictureIsAnswered() {
        assertEquals(FacilityKind.GUARD_HOUSE.meaning, iconAnswer("집에 하트 그려진 아이콘 뭐야?"))
        assertEquals(FacilityKind.GUARD_HOUSE.meaning, iconAnswer("하트 머야"))
        assertEquals(FacilityKind.EMERGENCY_BELL.meaning, iconAnswer("지도에 나오는 벨 아이콘이 머냐고"))
        assertEquals(FacilityKind.EMERGENCY_BELL.meaning, iconAnswer("빨간 종 그림 뭐예요"))
        assertEquals(FacilityKind.HEAT_SHELTER.meaning, iconAnswer("집 안에 해 그림은 뭐야"))
        assertEquals(FacilityKind.COLD_SHELTER.meaning, iconAnswer("눈송이 아이콘 뜻이 뭐야"))
        assertEquals(FacilityKind.CCTV.meaning, iconAnswer("카메라 모양 표시 뭐야"))
        assertEquals(FacilityKind.STAIRS.meaning, iconAnswer("빨간 계단 아이콘 뭐야"))
        assertEquals(FacilityKind.TOILET.meaning, iconAnswer("화장실 아이콘 뭐야"))
    }

    @Test
    fun aHouseAloneOrIconsInGeneralGetTheChoices() {
        assertTrue(iconAnswer("집 모양 아이콘 뭐야")!!.startsWith("집 모양은 세 가지예요"))
        // "아이콘" is not "아이" (a child).
        assertTrue(iconAnswer("지도 아이콘들 뭐야")!!.startsWith("지도 그림은 이래요"))
    }

    @Test
    fun goingSomewhereIsNotAboutAPicture() {
        assertNull(iconAnswer("가까운 화장실로 안내해 줘"))
        assertNull(iconAnswer("편의점 어디야"))
        assertNull(iconAnswer("글씨 크게 해 줘"))
        assertNull(iconAnswer("아이콘 크게 해 줘"))
    }
}
