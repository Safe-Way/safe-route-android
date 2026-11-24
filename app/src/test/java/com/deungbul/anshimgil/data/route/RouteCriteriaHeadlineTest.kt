package com.deungbul.anshimgil.data.route

import org.junit.Assert.assertEquals
import org.junit.Test
import com.deungbul.anshimgil.data.user.UserMode

class RouteCriteriaHeadlineTest {
    private val senior = routeCriteria(UserMode.SENIOR, night = false).short

    @Test
    fun stairsLeftOnEveryWayLetsTheServerLead() {
        // 11/10 phone: 어르신, 낮, IT융합대학 앞 → 조선대학교 체육대학 화장실. The server said the stairs could not be avoided.
        val e = Explanation("가장 짧은 길로 갑니다", emptyList(), listOf(RouteNote(true, "stairs", "계단 1곳을 지납니다. 계단을 피하는 길은 없습니다")))
        assertEquals("가장 짧은 길로 갑니다", headlineFor(senior, e))
    }

    @Test
    fun otherwiseTheModesLine() {
        val e = Explanation("24m(1분) 더 돌지만 쉼 곳 +1곳", emptyList())
        assertEquals("계단을 피하고, 쉴 곳이 가까운 길이에요.", headlineFor(senior, e))
        // A stairs note does not change a line that promises nothing about stairs.
        val general = routeCriteria(UserMode.GENERAL, night = false).short
        val stairs = Explanation("가장 짧은 길로 갑니다", emptyList(), listOf(RouteNote(true, "stairs", "계단 1곳을 지납니다.")))
        assertEquals(general, headlineFor(general, stairs))
    }
}
