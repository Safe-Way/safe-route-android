package com.deungbul.anshimgil.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/*
 * 이동 패턴 (11/8): where the walker goes at about the same time, learned on this phone from the guidance they
 * start (only with the trip-record consent), so the app can offer the trip before they think of it
 * ("보통 8시 반쯤 학교에 가셨어요. 지금 길 안내할까요?"), in its bubble and as a notification.
 *
 * What counts as a habit is the team server's rule (safe-route 027 trip_habit view, MemoryRepository.habits,
 * TodayService): the same weekday, the same 30-minute start slot and the same place, 3 times or more; offered when
 * its slot is from 30 minutes ago to 90 minutes ahead. The app's own rule of 11/8 (any 3 days in 14, weekdays
 * together, ±40 minutes, within 150 m) was dropped for it.
 *
 * Not offered when it would be wrong: on a day the walker already started that trip, or while already there.
 */

/** How long trips are kept on the phone at all, and how many. */
const val TRIPS_KEPT_DAYS = 28L
const val TRIPS_KEPT = 80

/** Times needed for a habit (MemoryRepository.habits: times >= 3). */
const val HABIT_TIMES = 3

/** Start slots this long (trip_log.start_slot, 30 minutes). */
const val HABIT_SLOT_MIN = 30

/** Destinations this close (metres) count as already gone to today. */
const val SAME_PLACE_M = 150

/** A habit is offered while its slot is from [OFFER_AFTER_MIN] minutes ago to [OFFER_BEFORE_MIN] ahead (TodayService). */
const val OFFER_BEFORE_MIN = 90
const val OFFER_AFTER_MIN = 30

/** A guidance the walker started. */
data class TripRecord(val name: String, val lat: Double, val lon: Double, val at: LocalDateTime, val transit: Boolean) {
    companion object {
        fun encodeAll(trips: List<TripRecord>): String = JSONArray(
            trips.map { JSONObject().put("name", it.name).put("lat", it.lat).put("lon", it.lon).put("at", it.at.toString()).put("transit", it.transit) },
        ).toString()

        fun decodeAll(s: String?): List<TripRecord> = runCatching {
            val a = JSONArray(s ?: return emptyList())
            (0 until a.length()).mapNotNull { i ->
                runCatching {
                    val j = a.getJSONObject(i)
                    TripRecord(j.getString("name"), j.getDouble("lat"), j.getDouble("lon"), LocalDateTime.parse(j.getString("at")), j.optBoolean("transit"))
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }
}

/** The trips worth keeping at [now]: the last [TRIPS_KEPT_DAYS] days, at most [TRIPS_KEPT]. */
fun keepTrips(trips: List<TripRecord>, now: LocalDateTime): List<TripRecord> =
    trips.filter { it.at.isAfter(now.minusDays(TRIPS_KEPT_DAYS)) }.sortedBy { it.at }.takeLast(TRIPS_KEPT)

/** A learned habit: going to [place] at about [time] on [days] different days lately. */
data class Habit(val place: Place, val time: LocalTime, val days: Int, val transit: Boolean)

private fun minuteOfDay(t: LocalDateTime) = t.hour * 60 + t.minute

/** The 30-minute start slot of a trip (trip_log.start_slot). */
private fun slotOf(t: LocalDateTime): LocalTime = LocalTime.of(t.hour, t.minute / HABIT_SLOT_MIN * HABIT_SLOT_MIN)

/**
 * All habits for [today]'s weekday, as the server's trip_habit: trips grouped by weekday, start slot and place
 * (name and position), [HABIT_TIMES] or more; most often first.
 */
fun habits(trips: List<TripRecord>, today: LocalDate): List<Habit> =
    trips.filter { it.at.dayOfWeek == today.dayOfWeek }
        .groupBy { Triple(slotOf(it.at), it.name, it.lat to it.lon) }
        .filter { it.value.size >= HABIT_TIMES }
        .map { (key, group) ->
            Habit(
                place = Place(null, key.second, "", key.third.first, key.third.second, null),
                time = key.first,
                days = group.size,
                transit = group.count { it.transit } * 2 > group.size,
            )
        }
        .sortedWith(compareByDescending<Habit> { it.days }.thenBy { it.time })

/**
 * The habit to offer at [now], if any: its usual time is from [before] minutes ahead to [after] minutes past,
 * the walker has not started a trip there today, and is not already there ([here], when known).
 */
fun habitNow(
    trips: List<TripRecord>,
    now: LocalDateTime,
    here: LatLon?,
    before: Int = OFFER_BEFORE_MIN,
    after: Int = OFFER_AFTER_MIN,
): Habit? {
    val today = now.toLocalDate()
    val nowMin = minuteOfDay(now)
    return habits(trips, today)
        .filter { h -> (h.time.hour * 60 + h.time.minute - nowMin) in -after..before }
        .filterNot { h ->
            trips.any { it.at.toLocalDate() == today && distanceMeters(it.lat, it.lon, h.place.lat, h.place.lon) <= SAME_PLACE_M }
        }
        .filterNot { h -> here != null && distanceMeters(here.lat, here.lon, h.place.lat, h.place.lon) <= STILL_THERE_M }
        .maxByOrNull { it.days }
}

/** "8시 반", "오후 2시 10분" style time for the bubble and the notification. */
fun sayTime(t: LocalTime): String {
    val h = when {
        t.hour == 0 -> 12
        t.hour > 12 -> t.hour - 12
        else -> t.hour
    }
    val m = when (t.minute) {
        0 -> ""
        30 -> " 반"
        else -> " ${t.minute}분"
    }
    return "${h}시$m"
}

/** The bubble for a habit: "보통 8시 반쯤 학교에 가셨어요. 지금 길 안내할까요?". */
fun habitHint(h: Habit, today: LocalDate): MapHint = MapHint(
    "habit-$today-${h.place.name}",
    "보통 ${sayTime(h.time)}쯤 ${h.place.name}에 가셨어요. 지금 길 안내할까요?",
    h.place,
    startNow = true,
    transit = h.transit,
    until = today.atTime(h.time).plusMinutes(OFFER_AFTER_MIN.toLong()),
)
