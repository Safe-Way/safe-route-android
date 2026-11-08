package com.deungbul.anshimgil.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/*
 * 캘린더 일정으로 길 알려 주기 (11/8): with the walker's yes (asked once by AI 비서, and a switch in settings) and
 * the calendar permission, the phone's own calendar is read — on the phone, nothing is sent but the place
 * written in the event, which is searched like a typed search — and an event with a place a few hours ahead
 * is offered in the map's bubble: "3시에 '치과 예약' 일정이 있어요 (○○치과). 길 찾아 드릴까요?".
 *
 * Left out, so that it is never wrong: all-day events, events without a place, events already started or
 * starting in less than [CALENDAR_MIN_AHEAD_MIN] minutes, cancelled ones and ones the walker declined, and
 * places the search cannot find near Gwangju ([CALENDAR_MAX_KM]), and places too vague to find the right one
 * ("치과", "회사", "온라인"): the place found must carry a word of what the walker wrote ([placeMatches]).
 */

/** Words that say what kind of place, or where in general, but not which place. */
private val VAGUE_WORDS = setOf(
    "광주", "광주광역시", "광주시", "동구", "서구", "남구", "북구", "광산구", "시내", "근처",
    "치과", "병원", "의원", "약국", "회사", "사무실", "학교", "학원", "집", "본가", "카페", "식당", "회의실", "미팅룸",
    "온라인", "zoom", "줌", "화상", "전화", "teams", "meet", "구글미트",
)

/** The words of an event's place that can name one place: no floors or rooms, no vague words. */
fun placeWords(location: String): List<String> =
    location.lowercase()
        .replace(Regex("\\d+층|\\d+호|b\\d+|\\(.*?\\)"), " ")
        .split(Regex("[\\s,·/]+"))
        .map { it.trim() }
        .filter { it.length >= 2 && it !in VAGUE_WORDS && !it.all(Char::isDigit) && !it.startsWith("http") }

/** Whether a found place is the one written: its name or address carries one of [placeWords]. */
fun placeMatches(location: String, place: Place): Boolean {
    val words = placeWords(location)
    val name = place.name.lowercase().replace(" ", "")
    val address = place.address.lowercase().replace(" ", "")
    return words.any { w -> w.replace(" ", "").let { name.contains(it) || (it.length >= 3 && address.contains(it)) } }
}

/** Events this far ahead are looked at. */
const val CALENDAR_HOURS_AHEAD = 3L

/** Too late to offer a way there. */
const val CALENDAR_MIN_AHEAD_MIN = 10L

/** A found place farther than this from the walker is probably another place of the same name. */
const val CALENDAR_MAX_KM = 30

/** One event from the phone's calendar. */
data class CalendarEvent(val id: Long, val title: String, val location: String, val begin: LocalDateTime)

fun hasCalendarPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

private val KST: ZoneId = ZoneId.of("Asia/Seoul")

/** Events with a place starting between [CALENDAR_MIN_AHEAD_MIN] minutes and [hoursAhead] hours from [now], soonest first. */
fun upcomingEvents(context: Context, now: LocalDateTime, hoursAhead: Long = CALENDAR_HOURS_AHEAD): List<CalendarEvent> {
    if (!hasCalendarPermission(context)) return emptyList()
    val from = now.plusMinutes(CALENDAR_MIN_AHEAD_MIN).atZone(KST).toInstant().toEpochMilli()
    val to = now.plusHours(hoursAhead).atZone(KST).toInstant().toEpochMilli()
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, from)
        ContentUris.appendId(it, to)
    }.build()
    val columns = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.EVENT_LOCATION,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.STATUS,
        CalendarContract.Instances.SELF_ATTENDEE_STATUS,
    )
    val events = mutableListOf<CalendarEvent>()
    runCatching {
        context.contentResolver.query(uri, columns, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val location = c.getString(2)?.trim().orEmpty()
                val begin = c.getLong(3)
                val allDay = c.getInt(4) == 1
                val cancelled = !c.isNull(5) && c.getInt(5) == CalendarContract.Events.STATUS_CANCELED
                val declined = !c.isNull(6) && c.getInt(6) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
                if (location.isBlank() || allDay || cancelled || declined || begin < from || begin > to) continue
                events += CalendarEvent(
                    id = c.getLong(0),
                    title = c.getString(1)?.trim().orEmpty().ifBlank { "일정" },
                    location = location,
                    begin = LocalDateTime.ofInstant(Instant.ofEpochMilli(begin), KST),
                )
            }
        }
    }
    return events
}

/** Where an event's place is: the search's first answer, when it is near enough to the walker. */
suspend fun placeOf(event: CalendarEvent, places: PlaceRepository, here: LatLon): Place? {
    // Nothing that names one place ("치과", "Zoom"): not guessed.
    if (placeWords(event.location).isEmpty()) return null
    // "광주 동구 ○○치과 2층" → also try without the floor or room part.
    val queries = listOf(event.location, event.location.replace(Regex("\\s*(\\d+층|\\d+호|B\\d+).*$"), "")).distinct()
    for (q in queries) {
        if (q.length < 2) continue
        val found = runCatching { places.search(q, here.lat, here.lon) }.getOrNull().orEmpty()
            .firstOrNull { placeMatches(event.location, it) && distanceMeters(here.lat, here.lon, it.lat, it.lon) <= CALENDAR_MAX_KM * 1000 }
        if (found != null) return found
    }
    return null
}

/** "3시에 '치과 예약' 일정이 있어요 (○○치과). 길 찾아 드릴까요?". */
fun calendarText(event: CalendarEvent, place: Place): String {
    val title = event.title.take(20)
    val where = if (title.contains(place.name) || place.name.contains(title)) "" else " (${place.name})"
    return "${sayTime(event.begin.toLocalTime())}에 '$title' 일정이 있어요$where. 길 찾아 드릴까요?"
}

/** The bubble for the first upcoming event whose place is found. */
suspend fun calendarHint(context: Context, places: PlaceRepository, here: LatLon, now: LocalDateTime): MapHint? {
    for (event in upcomingEvents(context, now).take(3)) {
        val place = placeOf(event, places, here) ?: continue
        // Already there.
        if (distanceMeters(here.lat, here.lon, place.lat, place.lon) <= STILL_THERE_M) continue
        return MapHint("cal-${event.id}-${event.begin}", calendarText(event, place), place, startNow = true, until = event.begin)
    }
    return null
}
