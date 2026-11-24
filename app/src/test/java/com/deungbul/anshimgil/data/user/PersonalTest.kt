package com.deungbul.anshimgil.data.user

import com.deungbul.anshimgil.reminderFor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import com.deungbul.anshimgil.data.place.Place
import com.deungbul.anshimgil.data.route.LatLon

class PersonalTest {
    private val today = LocalDate.of(2026, 11, 12) // a Thursday

    @Test
    fun nearbyButtonMovesOnlyWithAClearLead() {
        assertEquals(listOf("store", "police", "toilet"), nearbyOrder(Usage()))
        // One or two taps never move the buttons under the finger.
        assertEquals(NEARBY_KEYS, nearbyOrder(Usage(nearbyTaps = mapOf("toilet" to 1))))
        assertEquals(NEARBY_KEYS, nearbyOrder(Usage(nearbyTaps = mapOf("toilet" to 2))))
        // Used clearly most: first.
        assertEquals(listOf("toilet", "store", "police"), nearbyOrder(Usage(nearbyTaps = mapOf("toilet" to 4, "store" to 1))))
        // Close to the one ahead: stays where it is.
        assertEquals(NEARBY_KEYS, nearbyOrder(Usage(nearbyTaps = mapOf("police" to 4, "store" to 3))))
        assertEquals(NEARBY_KEYS, nearbyOrder(Usage(adapt = false, nearbyTaps = mapOf("toilet" to 9))))
    }

    @Test
    fun routeKindChosenThreeOfFourTimesComesFirst() {
        val s = RouteChoice.SHORTEST
        val t = RouteChoice.TRANSIT
        assertNull(preferredRoute(Usage(routeChoices = listOf(s, s))))
        assertEquals(s, preferredRoute(Usage(routeChoices = listOf(s, RouteChoice.SAFE, s, s))))
        // Walking or transit is the server's prefer_transit (PreferenceLearner), not the app's count.
        assertNull(preferredRoute(Usage(routeChoices = listOf(t, t, t))))
        assertEquals(t, preferredRoute(Usage(routeChoices = listOf(s, s, s)), preferTransit = 2))
        assertEquals(s, preferredRoute(Usage(routeChoices = listOf(s, t, s, t, s)), preferTransit = 0))
    }

    @Test
    fun calendarPlaceMustBeTheOneWritten() {
        val dentist = Place(null, "밝은치과의원", "광주 동구 충장로 10", 35.14, 126.91, null)
        assertTrue(placeMatches("광주 동구 밝은치과 2층", dentist))
        // Too vague to know which one: not guessed.
        assertTrue(placeWords("치과").isEmpty())
        assertTrue(placeWords("Zoom").isEmpty())
        assertTrue(placeWords("회사 3층 회의실").isEmpty())
        // Another dentist than the one written.
        assertFalse(placeMatches("푸른치과", dentist))
        // A street written as the place: found by the address.
        assertTrue(placeMatches("충장로 10", dentist))
    }

    @Test
    fun keepingThePickedRouteDoesNotFeedItself() {
        assertTrue(choiceCounts(null, RouteChoice.SAFE))
        assertFalse(choiceCounts(RouteChoice.SHORTEST, RouteChoice.SHORTEST))
        assertTrue(choiceCounts(RouteChoice.SHORTEST, RouteChoice.SAFE))
    }

    private val buttons = mapOf(
        "locate" to ButtonBox(900f, 1800f, 1000f, 1900f),
        "ai" to ButtonBox(400f, 1850f, 680f, 1950f),
        "_ask" to ButtonBox(40f, 1500f, 1040f, 1780f),
    )

    @Test
    fun pressingTheSameButtonAgainIsNoMiss() {
        val d = MissDetector(nearPx = 60f)
        repeat(4) { i -> assertEquals("locate" to false, d.tap(950f, 1850f, 1_000L + i * 300, buttons)) }
    }

    @Test
    fun aTapJustBesideThenOnTheButtonIsAMiss() {
        val d = MissDetector(nearPx = 60f)
        assertNull(d.tap(880f, 1850f, 1_000, buttons)) // 20 px left of 내 위치: nothing happened
        assertEquals("locate" to true, d.tap(950f, 1850f, 1_800, buttons))
    }

    @Test
    fun notEveryNearbyTapIsAMiss() {
        // Too late after the tap beside it.
        MissDetector(nearPx = 60f).let { d ->
            d.tap(880f, 1850f, 1_000, buttons)
            assertEquals("locate" to false, d.tap(950f, 1850f, 1_000 + MISS_FOLLOW_MS + 1, buttons))
        }
        // Far from any button (looking around the map), then the button.
        MissDetector(nearPx = 60f).let { d ->
            assertNull(d.tap(500f, 900f, 1_000, buttons))
            assertEquals("locate" to false, d.tap(950f, 1850f, 1_300, buttons))
        }
        // Beside 내 위치, then a different button.
        MissDetector(nearPx = 60f).let { d ->
            d.tap(880f, 1850f, 1_000, buttons)
            assertEquals("ai" to false, d.tap(500f, 1900f, 1_300, buttons))
        }
        // An answer in AI 비서's bubble, then its button: the bubble took the tap.
        MissDetector(nearPx = 60f).let { d ->
            assertNull(d.tap(500f, 1770f, 1_000, buttons))
            assertEquals("ai" to false, d.tap(500f, 1900f, 1_300, buttons))
        }
        // A tap beside that opened a place on the map, then the button.
        MissDetector(nearPx = 60f).let { d ->
            d.tap(880f, 1850f, 1_000, buttons)
            d.forget()
            assertEquals("locate" to false, d.tap(950f, 1850f, 1_300, buttons))
        }
        // A tap beside, then another tap on the map, then the button.
        MissDetector(nearPx = 60f).let { d ->
            d.tap(880f, 1850f, 1_000, buttons)
            d.tap(500f, 900f, 1_200, buttons)
            assertEquals("locate" to false, d.tap(950f, 1850f, 1_400, buttons))
        }
    }

    private fun presses(vararg missedAt: LocalDateTime?, clean: Int = 0) =
        missedAt.map { Press(it) } + List(clean) { Press() }

    @Test
    fun biggerTextForMissedTapsOnlyHalfADayApart() {
        val d1 = today.minusDays(1).atTime(9, 0)
        val now = today.atTime(10, 0)
        // Pressing a lot is no sign at all.
        assertNull(adaptationToOffer(Usage(presses = presses(clean = 20)), today))
        // Three misses in one moment (a shaking bus): not yet, even across midnight.
        assertNull(adaptationToOffer(Usage(presses = presses(now, now.plusMinutes(1), now.plusMinutes(2))), today))
        val midnight = today.atStartOfDay()
        assertNull(adaptationToOffer(Usage(presses = presses(midnight.minusMinutes(1), midnight, midnight.plusMinutes(1))), today))
        assertEquals(Adaptation.MISSED_TAPS, adaptationToOffer(Usage(presses = presses(d1, now, now)), today))
        // The text is already the biggest, or bigger text was turned down: not asked.
        assertNull(adaptationToOffer(Usage(presses = presses(d1, now, now), textBoost = 1.3f), today))
        assertNull(adaptationToOffer(Usage(presses = presses(d1, now, now), askedBigText = true), today))
        // Clean presses since then push the old misses out.
        assertNull(adaptationToOffer(Usage(presses = (presses(d1, now, now) + presses(clean = 20)).takeLast(PRESSES_KEPT)), today))
        assertNull(adaptationToOffer(Usage(presses = presses(d1, now, now), askedMissedTaps = true), today))
        assertNull(adaptationToOffer(Usage(adapt = false, presses = presses(d1, now, now)), today))
        assertEquals(Press(d1), Press.decode(Press(d1).encode()))
    }

    @Test
    fun biggerTextForZoomingOnSeveralDays() {
        val days = listOf(today.minusDays(5), today.minusDays(2), today)
        assertNull(adaptationToOffer(Usage(zoomDays = listOf(today)), today))
        assertEquals(Adaptation.BIG_TEXT, adaptationToOffer(Usage(zoomDays = days), today))
        // Long ago: does not count.
        assertNull(adaptationToOffer(Usage(zoomDays = listOf(today.minusDays(40), today.minusDays(30), today)), today))
        // The setting stays 작게·보통·크게; the AI part grows on top.
        assertEquals(Adaptation.BIGGER_TEXT, adaptationToOffer(Usage(zoomDays = days, textBoost = 1.15f), today))
        assertNull(adaptationToOffer(Usage(zoomDays = days, textBoost = 1.3f), today)) // already the biggest
        assertNull(adaptationToOffer(Usage(zoomDays = days, askedBigText = true), today)) // said no
        assertEquals(1.15f, nextTextBoost(1f)!!, 0f)
        assertNull(nextTextBoost(1.3f))
    }

    @Test
    fun calendarAskedOnceAfterTheFirstGuidance() {
        val trip = TripRecord("학교", 35.14, 126.93, LocalDateTime.of(2026, 11, 10, 8, 30), false)
        assertNull(adaptationToOffer(Usage(), today))
        assertEquals(Adaptation.CALENDAR, adaptationToOffer(Usage(trips = listOf(trip)), today))
        assertNull(adaptationToOffer(Usage(trips = listOf(trip), askedCalendar = true), today))
        assertNull(adaptationToOffer(Usage(trips = listOf(trip), calendarOn = true), today))
    }

    private val home = Place(null, "우리집", "", 35.1395, 126.9245, null)
    private val acc = LatLon(35.1467, 126.9203)
    private val arrivedAt = LocalDateTime.of(2026, 11, 9, 13, 0)

    @Test
    fun wayHomeAfterTheUsualStayAtThatPlace() {
        val stays = listOf(Stay("국립아시아문화전당", 100), Stay("학교", 400), Stay("국립아시아문화전당", 130), Stay("국립아시아문화전당", 120))
        val usage = Usage(lastArrival = Arrival("국립아시아문화전당", acc.lat, acc.lon, arrivedAt), stays = stays)
        // The stay at school says nothing about this place.
        assertEquals(120, usualStayMinutes(usage, "국립아시아문화전당"))
        assertNull(returnHint(usage, acc, arrivedAt.plusMinutes(90), home))
        val hint = returnHint(usage, acc, arrivedAt.plusMinutes(125), home)!!
        assertEquals("국립아시아문화전당에 온 지 2시간 지났어요. 집으로 갈까요?", hint.text)
        assertEquals("집", hint.place!!.name)
        assertTrue(hint.startNow)
        assertNull(returnHint(usage, LatLon(35.1600, 126.9000), arrivedAt.plusMinutes(125), home))
        assertNull(returnHint(usage, acc, arrivedAt.plusMinutes(125), null))
        // Only once seen there: not enough.
        assertNull(returnHint(usage.copy(stays = listOf(Stay("국립아시아문화전당", 100), Stay("학교", 120))), acc, arrivedAt.plusMinutes(125), home))
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
        assertNull(reminderFor(json, LocalTime.of(7, 30)))
        assertNull(reminderFor(json, LocalTime.of(8, 40)))
        assertNull(reminderFor(JSONObject("""{"say":"어디 가실 곳이 있으세요?","habit":null}"""), LocalTime.of(8, 15)))
    }
}
