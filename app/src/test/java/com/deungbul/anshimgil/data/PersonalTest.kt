package com.deungbul.anshimgil.data

import com.deungbul.anshimgil.reminderFor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class PersonalTest {
    @Test
    fun nearbyButtonsMostUsedFirst() {
        assertEquals(listOf("store", "police", "toilet"), nearbyOrder(Usage()))
        assertEquals(listOf("toilet", "store", "police"), nearbyOrder(Usage(nearbyTaps = mapOf("toilet" to 4, "store" to 1))))
        // Switched off: the first order.
        assertEquals(NEARBY_KEYS, nearbyOrder(Usage(adapt = false, nearbyTaps = mapOf("toilet" to 4))))
    }

    @Test
    fun routeKindChosenThreeOfFourTimesComesFirst() {
        val s = RouteChoice.SHORTEST
        val t = RouteChoice.TRANSIT
        assertNull(preferredRoute(Usage(routeChoices = listOf(s, s))))
        assertEquals(s, preferredRoute(Usage(routeChoices = listOf(s, RouteChoice.SAFE, s, s))))
        assertEquals(t, preferredRoute(Usage(routeChoices = listOf(t, t, t))))
        assertNull(preferredRoute(Usage(routeChoices = listOf(s, t, s, t))))
    }

    @Test
    fun biggerButtonsThenBiggerTextOfferedOnce() {
        assertNull(adaptationToOffer(Usage(retaps = 2), TextSize.NORMAL))
        assertEquals(Adaptation.BIG_BUTTONS, adaptationToOffer(Usage(retaps = 3), TextSize.NORMAL))
        assertNull(adaptationToOffer(Usage(retaps = 3, askedBigButtons = true), TextSize.NORMAL))
        assertEquals(Adaptation.BIG_TEXT, adaptationToOffer(Usage(retaps = 3, askedBigButtons = true, zoomIns = 5), TextSize.NORMAL))
        assertNull(adaptationToOffer(Usage(adapt = false, retaps = 9), TextSize.NORMAL))
    }

    @Test
    fun aiTextGrowsStepByStepOnTopOfTheSetting() {
        // The setting stays 작게·보통·크게; the AI part is separate and can still grow on top of 크게.
        assertEquals(Adaptation.BIG_TEXT, adaptationToOffer(Usage(zoomIns = 5), TextSize.LARGE))
        assertEquals(Adaptation.BIGGER_TEXT, adaptationToOffer(Usage(zoomIns = 5, textBoost = 1.15f), TextSize.LARGE))
        assertNull(adaptationToOffer(Usage(zoomIns = 9, textBoost = 1.3f), TextSize.NORMAL)) // already the biggest
        assertNull(adaptationToOffer(Usage(zoomIns = 9, askedBigText = true), TextSize.NORMAL)) // said no
        assertEquals(1.15f, nextTextBoost(1f)!!, 0f)
        assertEquals(1.3f, nextTextBoost(1.15f)!!, 0f)
        assertNull(nextTextBoost(1.3f))
    }

    private val home = Place(null, "우리집", "", 35.1395, 126.9245, null)
    private val acc = LatLon(35.1467, 126.9203)
    private val arrivedAt = LocalDateTime.of(2026, 11, 9, 13, 0)

    @Test
    fun wayHomeAfterTheUsualStay() {
        val usage = Usage(lastArrival = Arrival("국립아시아문화전당", acc.lat, acc.lon, arrivedAt), stays = listOf(100, 130, 120))
        assertEquals(120, usualStayMinutes(usage))
        assertNull(returnHint(usage, acc, arrivedAt.plusMinutes(90), home))
        val hint = returnHint(usage, acc, arrivedAt.plusMinutes(125), home)!!
        assertEquals("국립아시아문화전당에 온 지 2시간 지났어요. 집으로 갈까요?", hint.text)
        assertEquals("집", hint.place!!.name)
        assertTrue(hint.startNow)
        // Gone somewhere else, no home saved, or not enough stays seen: nothing.
        assertNull(returnHint(usage, LatLon(35.1600, 126.9000), arrivedAt.plusMinutes(125), home))
        assertNull(returnHint(usage, acc, arrivedAt.plusMinutes(125), null))
        assertNull(returnHint(usage.copy(stays = listOf(100)), acc, arrivedAt.plusMinutes(125), home))
    }

    @Test
    fun departureReminderJustBeforeTheUsualTime() {
        val json = JSONObject(
            """{"say":"8시 반에 국립아시아문화전당 가시죠? 버스 30번, 19분이에요.",
               "habit":{"toName":"국립아시아문화전당","lat":35.147,"lon":126.92,"slot":"08:30","times":3,"usual":"walk"},
               "pick":{"type":"transit","request":{"destination":{"lat":35.1467,"lon":126.9203,"name":"국립아시아문화전당"}}}}""",
        )
        val r = reminderFor(json, LocalTime.of(8, 15))!!
        assertEquals("8시 반에 국립아시아문화전당 가시죠? 버스 30번, 19분이에요.", r.text)
        assertTrue(r.transit)
        assertEquals(35.1467, r.lat, 1e-9)
        assertNull(reminderFor(json, LocalTime.of(7, 30)))   // too early
        assertNull(reminderFor(json, LocalTime.of(8, 40)))   // too late
        assertNull(reminderFor(JSONObject("""{"say":"어디 가실 곳이 있으세요?","habit":null}"""), LocalTime.of(8, 15)))
    }
}
