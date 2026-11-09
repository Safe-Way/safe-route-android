package com.deungbul.anshimgil.data

import org.junit.Assert.assertEquals
import org.junit.Test

class JosaTest {
    @Test
    fun hangul() {
        assertEquals("집으로", withRo("집"))
        assertEquals("학교로", withRo("학교"))
        assertEquals("충장로로", withRo("충장로"))
        assertEquals("병원이에요", withIeyo("병원"))
        assertEquals("다이소예요", withIeyo("다이소"))
    }

    @Test
    fun numbersAreReadOut() {
        // The usability check, round 2: "GATE3로" should be "GATE3으로" (게이트삼).
        assertEquals("국립아시아문화전당 GATE3으로", withRo("국립아시아문화전당 GATE3"))
        assertEquals("1번 출구로", withRo("1번 출구"))
        assertEquals("2호관으로", withRo("2호관"))
        assertEquals("GS25로", withRo("GS25")) // 이십오
        assertEquals("7로", withRo("7")) // 칠
        assertEquals("10으로", withRo("10")) // 십
        assertEquals("45번을", withEulReul("45번"))
        assertEquals("518이", withIGa("518")) // 오백십팔
        assertEquals("102가", withIGa("102")) // 백이
    }

    @Test
    fun lettersAreReadOut() {
        assertEquals("CU로", withRo("CU")) // 씨유
        assertEquals("CU예요", withIeyo("CU"))
        assertEquals("KTX를", withEulReul("KTX")) // 케이티엑스
        assertEquals("BYM으로", withRo("BYM")) // 엠
        assertEquals("GS25 충장점으로", withRo("GS25 충장점"))
    }
}
