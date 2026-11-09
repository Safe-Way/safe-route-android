package com.deungbul.anshimgil.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/*
 * 이동 패턴 (11/8): where the walker goes at about the same time, learned on this phone from the guidance they
 * start, so that within the first week of use the app can offer the trip before they think of it
 * ("보통 8시 반쯤 학교에 가셨어요. 지금 길 안내할까요?"), in its bubble and as a notification.
 *
 * The server's suggestion needs the same weekday three times (three weeks at least), so this one groups the
 * days the way people live: weekdays together, weekends together. Three different days within two weeks,
 * starting within [HABIT_SPREAD_MIN] minutes of each other to places within [SAME_PLACE_M] of each other, make a
 * habit: Monday, Tuesday and Wednesday are enough to offer it on Thursday.
 *
 * Not offered when it would be wrong: on a day the walker already started that trip, while already there, or
 * outside the time around it. Nothing leaves the phone.
 */

/** Days looked back for a habit, and how long trips are kept at all. */
const val HABIT_WINDOW_DAYS = 14L
const val TRIPS_KEPT_DAYS = 28L
const val TRIPS_KEPT = 80

/** Different days needed for a habit. */
const val HABIT_DAYS = 3

/** Starts this close in time (minutes, either way) count as the same time. */
const val HABIT_SPREAD_MIN = 40

/** Destinations this close (metres) count as the same place. */
const val SAME_PLACE_M = 150

/** The habit is offered from this long before its usual time until [OFFER_AFTER_MIN] after it. */
const val OFFER_BEFORE_MIN = 30
const val OFFER_AFTER_MIN = 10

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

private fun LocalDate.isWeekend() = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

private fun minuteOfDay(t: LocalDateTime) = t.hour * 60 + t.minute

/** All habits for days like [today] (weekday or weekend), from trips before today. */
fun habits(trips: List<TripRecord>, today: LocalDate): List<Habit> {
    val past = trips.filter {
        val day = it.at.toLocalDate()
        day.isBefore(today) && day.isAfter(today.minusDays(HABIT_WINDOW_DAYS + 1)) && day.isWeekend() == today.isWeekend()
    }
    // Places: each trip joins the first group whose first trip is close enough.
    val groups = mutableListOf<MutableList<TripRecord>>()
    past.sortedBy { it.at }.forEach { t ->
        groups.firstOrNull { g -> distanceMeters(g[0].lat, g[0].lon, t.lat, t.lon) <= SAME_PLACE_M }?.add(t) ?: groups.add(mutableListOf(t))
    }
    return groups.mapNotNull { group ->
        // Around each start, the days that started within the spread; the time with the most days wins.
        val best = group.map { anchor ->
            group.filter { kotlin.math.abs(minuteOfDay(it.at) - minuteOfDay(anchor.at)) <= HABIT_SPREAD_MIN }
                // One trip a day: the first one at that time.
                .groupBy { it.at.toLocalDate() }.values.map { day -> day.minBy { it.at } }
        }.maxByOrNull { it.size } ?: return@mapNotNull null
        if (best.size < HABIT_DAYS) return@mapNotNull null
        val minutes = best.map { minuteOfDay(it.at) }.sorted().let { it[it.size / 2] }
        val latest = best.maxBy { it.at }
        Habit(
            place = Place(null, latest.name, "", latest.lat, latest.lon, null),
            time = LocalTime.of(minutes / 60, minutes % 60),
            days = best.size,
            transit = best.count { it.transit } * 2 > best.size,
        )
    }
}

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
