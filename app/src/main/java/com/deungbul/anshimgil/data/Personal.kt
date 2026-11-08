package com.deungbul.anshimgil.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.hypot

/*
 * 쓰는 대로 맞추기 (11/8): the app watches how it is used — on this phone only, never sent — and
 *  - offers bigger buttons when taps keep missing them, and bigger text when the map is zoomed in close on
 *    several days — the text one step at a time on top of the text size in settings ([TEXT_BOOST_STEPS]), which
 *    stays 작게·보통·크게 (11/8);
 *  - puts the nearby button used clearly most first;
 *  - picks the kind of route the walker keeps choosing (safe, shortest, transit) first;
 *  - after arriving somewhere, offers the way home once the usual stay at that place is over;
 *  - learns where the walker goes at about the same time ([Habits.kt]) and offers the trip before it.
 * Each change is offered by AI 비서 first (a yes/no bubble), except the button order and the route
 * picked first, which only reorder what is already there. Settings can switch it off or undo it.
 *
 * The signals are chosen so that ordinary use is never mistaken for difficulty (11/8 review):
 *  - pressing the same button again is normal (e.g. 내 위치 after moving the map) and is not counted;
 *    a miss is a tap that does nothing just beside a button, followed quickly by a tap on that button;
 *  - one clumsy moment (e.g. on a shaking bus) is not enough: misses at least half a day apart, and only
 *    among the recent presses, so clean use since then makes it go away;
 *  - zooms moved by the app itself (내 위치, a found place) are not counted, and zooming in a lot in one
 *    sitting counts once: it is the days on which the walker zoomed in close that count.
 */

/** The nearby-place buttons on the map, in their first order. */
val NEARBY_KEYS = listOf("store", "police", "toilet")

/** A nearby button moves ahead only once it was used this often, and this many times more than the one ahead. */
const val NEARBY_MIN_TAPS = 3
const val NEARBY_LEAD = 2

/** A tap that does nothing this close to a button (dp), then a tap on that button within [MISS_FOLLOW_MS], is a miss. */
const val MISS_NEAR_DP = 24f
const val MISS_FOLLOW_MS = 1_500L

/** A tap is a touch that lifts within this long and moves less than the touch slop. */
const val TAP_MAX_MS = 400L

/**
 * Recent button presses kept, and the misses among them that ask for bigger buttons: at least
 * [MISSES_FOR_BIG_BUTTONS], the first and last [MISS_SPREAD_HOURS] hours apart (not one clumsy moment, even
 * one just around midnight).
 */
const val PRESSES_KEPT = 20
const val MISSES_FOR_BIG_BUTTONS = 3
const val MISS_SPREAD_HOURS = 12L

/** Days with a close zoom-in (within the last [ZOOM_WINDOW_DAYS]) that ask for bigger text. */
const val ZOOM_DAYS_FOR_BIG_TEXT = 3
const val ZOOM_WINDOW_DAYS = 14L

/** How many recent route choices are kept, and how many of them must agree to pick that kind first. */
const val ROUTE_CHOICES_KEPT = 4
const val ROUTE_CHOICES_AGREE = 3

/**
 * How much AI 비서 has made the text bigger, on top of the text size in settings: one step each time it is
 * offered and accepted (11/8: the settings keep 작게·보통·크게, the AI part is separate and can go further).
 */
val TEXT_BOOST_STEPS = listOf(1f, 1.15f, 1.3f)

/** Kinds of route the walker can choose. */
enum class RouteChoice { SAFE, SHORTEST, TRANSIT }

/** One button press: [missedAt] is when, if it came right after a tap that missed the button. */
data class Press(val missedAt: LocalDateTime? = null) {
    val missed get() = missedAt != null
    fun encode() = missedAt?.let { "m$it" } ?: "h"

    companion object {
        fun decode(s: String): Press? = when {
            s == "h" -> Press()
            s.startsWith("m") -> runCatching { Press(LocalDateTime.parse(s.drop(1))) }.getOrNull()
            else -> null
        }
    }
}

/** How long the walker stayed at a place before leaving it again. */
data class Stay(val name: String, val minutes: Int)

/** What has been learned so far. */
data class Usage(
    /** Switched on by default; off keeps everything as it first was. */
    val adapt: Boolean = true,
    val bigButtons: Boolean = false,
    val nearbyTaps: Map<String, Int> = emptyMap(),
    /** The latest [PRESSES_KEPT] presses of the map's buttons, most recent last. */
    val presses: List<Press> = emptyList(),
    /** Days the map was zoomed in close by hand, most recent last. */
    val zoomDays: List<LocalDate> = emptyList(),
    val askedBigButtons: Boolean = false,
    /** Said no to bigger text: not asked again (until 처음대로). */
    val askedBigText: Boolean = false,
    /** AI 비서's text enlargement ([TEXT_BOOST_STEPS]); 1 = none. */
    val textBoost: Float = 1f,
    /** Most recent last. */
    val routeChoices: List<RouteChoice> = emptyList(),
    val lastArrival: Arrival? = null,
    /** Most recent last. */
    val stays: List<Stay> = emptyList(),
    /** Guidance started (where to and when), for the habits ([Habits.kt]); most recent last. */
    val trips: List<TripRecord> = emptyList(),
    /** 캘린더 일정으로 길 알려 주기 ([CalendarTrips.kt]): off until the walker says yes. */
    val calendarOn: Boolean = false,
    val askedCalendar: Boolean = false,
    /** The last change accepted in a bubble and when, for "방금 거 원래대로" in AI 비서. */
    val lastChange: Pair<Adaptation, LocalDateTime>? = null,
) {
    val misses: List<Press> get() = presses.filter { it.missed }
}

/** Where and when the walker last arrived with guidance. */
data class Arrival(val name: String, val lat: Double, val lon: Double, val at: LocalDateTime) {
    fun encode(): String = JSONObject().put("name", name).put("lat", lat).put("lon", lon).put("at", at.toString()).toString()

    companion object {
        fun decode(s: String?): Arrival? = s?.let {
            runCatching {
                val j = JSONObject(it)
                Arrival(j.getString("name"), j.getDouble("lat"), j.getDouble("lon"), LocalDateTime.parse(j.getString("at")))
            }.getOrNull()
        }
    }
}

/**
 * The nearby buttons: a button moves ahead of the one before it only when it was used at least [NEARBY_MIN_TAPS]
 * times and [NEARBY_LEAD] more than that one, so one tap never moves the buttons under the walker's finger.
 */
fun nearbyOrder(usage: Usage): List<String> {
    if (!usage.adapt) return NEARBY_KEYS
    val taps = { k: String -> usage.nearbyTaps[k] ?: 0 }
    val order = NEARBY_KEYS.toMutableList()
    var moved = true
    while (moved) {
        moved = false
        for (i in 1 until order.size) {
            val (a, b) = order[i - 1] to order[i]
            if (taps(b) >= NEARBY_MIN_TAPS && taps(b) - taps(a) >= NEARBY_LEAD) {
                order[i - 1] = b; order[i] = a; moved = true
            }
        }
    }
    return order
}

/** The kind of route to show first: the one chosen at least [ROUTE_CHOICES_AGREE] of the last [ROUTE_CHOICES_KEPT] times. */
fun preferredRoute(usage: Usage): RouteChoice? {
    if (!usage.adapt) return null
    val recent = usage.routeChoices.takeLast(ROUTE_CHOICES_KEPT)
    return recent.groupingBy { it }.eachCount().entries.firstOrNull { it.value >= ROUTE_CHOICES_AGREE }?.key
}

/**
 * Whether a route choice says something: with a kind already picked first, keeping it only shows the
 * walker went along with it, so only changing it is counted (otherwise the pick would feed itself).
 */
fun choiceCounts(preferred: RouteChoice?, chosen: RouteChoice): Boolean = preferred == null || preferred != chosen

/** A change AI 비서 can offer, with its question and the answer that accepts it. */
enum class Adaptation(val question: String, val yes: String) {
    BIG_BUTTONS("버튼 옆을 누르셨다가 다시 누르시는 일이 몇 번 있었어요. 버튼을 더 크게 바꿔 드릴까요?", "크게 바꿔 줘"),
    BIG_TEXT("요즘 지도를 자주 크게 보시네요. 글씨를 더 크게 바꿔 드릴까요?", "글씨 크게"),
    BIGGER_TEXT("아직 글씨가 작으신가요? 한 단계 더 크게 바꿔 드릴까요?", "더 크게"),
    CALENDAR("캘린더에 장소가 적힌 일정이 있으면 미리 길을 알려 드릴까요? 일정은 폰 안에서만 봐요.", "좋아요"),
}

/** The next text enlargement, or null when it is already the biggest. */
fun nextTextBoost(current: Float): Float? = TEXT_BOOST_STEPS.firstOrNull { it > current + 0.001f }

/** Whether recent presses show the buttons are hard to hit (see [PRESSES_KEPT]). */
fun buttonsHardToHit(usage: Usage): Boolean {
    val misses = usage.presses.takeLast(PRESSES_KEPT).mapNotNull { it.missedAt }.sorted()
    return misses.size >= MISSES_FOR_BIG_BUTTONS && Duration.between(misses.first(), misses.last()).toHours() >= MISS_SPREAD_HOURS
}

/** Days with a close zoom-in in the last [ZOOM_WINDOW_DAYS] days. */
fun recentZoomDays(usage: Usage, today: LocalDate): Int =
    usage.zoomDays.filter { !it.isAfter(today) && it.isAfter(today.minusDays(ZOOM_WINDOW_DAYS)) }.distinct().size

/** The change to offer now, if any (one at a time, each asked once). */
fun adaptationToOffer(usage: Usage, today: LocalDate = LocalDate.now()): Adaptation? = when {
    !usage.adapt -> null
    !usage.bigButtons && !usage.askedBigButtons && buttonsHardToHit(usage) -> Adaptation.BIG_BUTTONS
    // Zoomed in close on several days since the last answer, and the text can still grow.
    !usage.askedBigText && recentZoomDays(usage, today) >= ZOOM_DAYS_FOR_BIG_TEXT && nextTextBoost(usage.textBoost) != null ->
        if (usage.textBoost > 1f) Adaptation.BIGGER_TEXT else Adaptation.BIG_TEXT
    // After the first guidance, when the app has shown what it does.
    !usage.calendarOn && !usage.askedCalendar && usage.trips.isNotEmpty() -> Adaptation.CALENDAR
    else -> null
}

/** A button's place on screen, in pixels. */
data class ButtonBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    operator fun contains(p: Pair<Float, Float>) = p.first in left..right && p.second in top..bottom

    /** How far a point is from the box (0 inside). */
    fun distanceTo(x: Float, y: Float): Float {
        val dx = maxOf(left - x, 0f, x - right)
        val dy = maxOf(top - y, 0f, y - bottom)
        return hypot(dx, dy)
    }
}

/** Keys of other things on screen that take taps (bubbles, cards) start with this. */
const val NOT_A_BUTTON = "_"

/**
 * Tells a missed button from ordinary use, from the taps on the map screen (not from the buttons' own clicks,
 * which only see the taps that worked). A tap beside a button within [nearPx] that hit no button, then within
 * [followMs] a tap on that same button, is one miss. Every tap on a button is a press.
 */
class MissDetector(private val nearPx: Float, private val followMs: Long = MISS_FOLLOW_MS) {
    private var near: Pair<String, Long>? = null

    /** The last tap opened something after all (e.g. a place on the map): it was no miss. */
    fun forget() {
        near = null
    }

    /** A tap at ([x], [y]) at [timeMs]; returns the press it made (button and whether it came after a miss), or null. */
    fun tap(x: Float, y: Float, timeMs: Long, buttons: Map<String, ButtonBox>): Pair<String, Boolean>? {
        val hit = buttons.entries.firstOrNull { (x to y) in it.value }?.key
        val before = near
        near = null
        // Something else that takes taps (a bubble, a card): not a button, and not a miss either.
        if (hit != null && hit.startsWith(NOT_A_BUTTON)) return null
        if (hit != null) return hit to (before != null && before.first == hit && timeMs - before.second in 0..followMs)
        // Missed everything: remember the nearest button close by.
        near = buttons.entries
            .filterNot { it.key.startsWith(NOT_A_BUTTON) }
            .map { it.key to it.value.distanceTo(x, y) }
            .filter { it.second <= nearPx }
            .minByOrNull { it.second }
            ?.let { it.first to timeMs }
        return null
    }
}

/** The usual stay at a place in minutes (the middle of the stays there), once there are at least two. */
fun usualStayMinutes(usage: Usage, name: String): Int? =
    usage.stays.filter { it.name == name }.map { it.minutes }.takeIf { it.size >= 2 }?.sorted()?.let { it[it.size / 2] }

/** How near the last arrival the walker must still be for "집으로 갈까요?". */
const val STILL_THERE_M = 300

/**
 * "문화전당에 온 지 2시간 지났어요. 집으로 갈까요?": after the usual stay at the place last arrived at (learned
 * from that place alone, since a stay at school says nothing about a stay at a café), while still near it,
 * the same day; null otherwise or without a saved home.
 */
fun returnHint(usage: Usage, here: LatLon, now: LocalDateTime, home: Place?): MapHint? {
    if (!usage.adapt || home == null) return null
    val arrival = usage.lastArrival ?: return null
    if (arrival.at.toLocalDate() != now.toLocalDate()) return null
    if (distanceMeters(here.lat, here.lon, arrival.lat, arrival.lon) > STILL_THERE_M) return null
    // Already home: nothing to offer.
    if (distanceMeters(arrival.lat, arrival.lon, home.lat, home.lon) < STILL_THERE_M) return null
    val stay = usualStayMinutes(usage, arrival.name) ?: return null
    val stayed = Duration.between(arrival.at, now).toMinutes()
    if (stayed < stay || stayed > 12 * 60) return null
    val hours = stayed / 60
    val minutes = stayed % 60
    val since = if (hours > 0) "${hours}시간${if (minutes >= 10) " ${minutes}분" else ""}" else "${minutes}분"
    return MapHint(
        "return-${arrival.at}",
        "${arrival.name}에 온 지 $since 지났어요. 집으로 갈까요?",
        home.copy(name = "집"),
        startNow = true,
    )
}

class UsageRepository(private val context: Context) {
    private val adaptKey = booleanPreferencesKey("adapt_on")
    private val bigButtonsKey = booleanPreferencesKey("big_buttons")
    private val nearbyKey = stringPreferencesKey("nearby_taps")
    private val pressesKey = stringPreferencesKey("presses")
    private val zoomDaysKey = stringPreferencesKey("zoom_days")
    private val askedButtonsKey = booleanPreferencesKey("asked_big_buttons")
    private val askedTextKey = booleanPreferencesKey("asked_big_text")
    private val routeChoicesKey = stringPreferencesKey("route_choices")
    private val arrivalKey = stringPreferencesKey("last_arrival")
    private val staysKey = stringPreferencesKey("stays_at")
    private val textBoostKey = stringPreferencesKey("text_boost")
    private val tripsKey = stringPreferencesKey("trips_local")
    private val calendarKey = booleanPreferencesKey("calendar_on")
    private val lastChangeKey = stringPreferencesKey("last_change")
    private val askedCalendarKey = booleanPreferencesKey("asked_calendar")

    /** Keys of the first version (11/8 morning), no longer read; removed by [reset]. */
    private val oldKeys = listOf("retaps", "zoom_ins", "stays").map { stringPreferencesKey(it) }

    val usage: Flow<Usage> = context.settingsDataStore.data.map(::read)

    private fun read(p: Preferences) = Usage(
        adapt = p[adaptKey] ?: true,
        bigButtons = p[bigButtonsKey] ?: false,
        nearbyTaps = p[nearbyKey].orEmpty().split(',').mapNotNull { e ->
            e.split('=').takeIf { it.size == 2 }?.let { (k, v) -> v.toIntOrNull()?.let { k to it } }
        }.toMap(),
        presses = p[pressesKey].orEmpty().split(',').mapNotNull { Press.decode(it) },
        zoomDays = p[zoomDaysKey].orEmpty().split(',').mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() },
        askedBigButtons = p[askedButtonsKey] ?: false,
        askedBigText = p[askedTextKey] ?: false,
        textBoost = p[textBoostKey]?.toFloatOrNull() ?: 1f,
        routeChoices = p[routeChoicesKey].orEmpty().split(',').mapNotNull { runCatching { RouteChoice.valueOf(it) }.getOrNull() },
        lastArrival = Arrival.decode(p[arrivalKey]),
        stays = runCatching {
            val a = JSONArray(p[staysKey] ?: "[]")
            (0 until a.length()).map { a.getJSONObject(it).let { j -> Stay(j.getString("name"), j.getInt("min")) } }
        }.getOrDefault(emptyList()),
        trips = TripRecord.decodeAll(p[tripsKey]),
        calendarOn = p[calendarKey] ?: false,
        askedCalendar = p[askedCalendarKey] ?: false,
        lastChange = p[lastChangeKey]?.split('@')?.takeIf { it.size == 2 }?.let { (a, t) ->
            runCatching { Adaptation.valueOf(a) to LocalDateTime.parse(t) }.getOrNull()
        },
    )

    private suspend fun change(block: (Preferences, androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { block(it, it) }
    }

    suspend fun tapNearby(key: String) = change { p, m ->
        val taps = read(p).nearbyTaps.toMutableMap()
        taps[key] = (taps[key] ?: 0) + 1
        m[nearbyKey] = taps.entries.joinToString(",") { "${it.key}=${it.value}" }
    }

    /** A press of one of the map's buttons; [missed] when it came right after a tap that missed it. */
    suspend fun press(missed: Boolean, at: LocalDateTime = LocalDateTime.now()) = change { p, m ->
        val press = if (missed) Press(at.withNano(0)) else Press()
        m[pressesKey] = (read(p).presses + press).takeLast(PRESSES_KEPT).joinToString(",") { it.encode() }
    }

    /** The map was zoomed in close by hand today (counted once a day). */
    suspend fun zoomIn(today: LocalDate = LocalDate.now()) = change { p, m ->
        val days = read(p).zoomDays
        if (today in days) return@change
        m[zoomDaysKey] = (days + today).filter { it.isAfter(today.minusDays(ZOOM_WINDOW_DAYS)) }.joinToString(",")
    }

    suspend fun answer(adaptation: Adaptation, yes: Boolean) = change { p, m ->
        if (yes) m[lastChangeKey] = "${adaptation.name}@${LocalDateTime.now().withNano(0)}"
        when (adaptation) {
            Adaptation.BIG_BUTTONS -> {
                m[askedButtonsKey] = true
                if (yes) m[bigButtonsKey] = true
                m.remove(pressesKey)
            }
            Adaptation.BIG_TEXT, Adaptation.BIGGER_TEXT -> {
                // Yes: one step bigger, and the zooming is counted again for the next step. No: not asked again.
                if (yes) nextTextBoost(read(p).textBoost)?.let { m[textBoostKey] = it.toString() } else m[askedTextKey] = true
                m.remove(zoomDaysKey)
            }
            // Yes still needs the calendar permission: [setCalendar] follows with the answer.
            Adaptation.CALENDAR -> m[askedCalendarKey] = true
        }
    }

    /** Takes back AI 비서's text enlargement. */
    suspend fun resetTextBoost() = change { _, m -> m.remove(textBoostKey) }

    suspend fun chooseRoute(choice: RouteChoice) = change { p, m ->
        m[routeChoicesKey] = (read(p).routeChoices + choice).takeLast(ROUTE_CHOICES_KEPT).joinToString(",")
    }

    /** At the end of a guidance that arrived. */
    suspend fun arrived(place: Place, at: LocalDateTime) = change { _, m ->
        m[arrivalKey] = Arrival(place.name, place.lat, place.lon, at).encode()
    }

    /**
     * At the start of a guidance: the trip is kept for the habits, and when it starts near the last arrival,
     * the stay there is learned.
     */
    suspend fun leaving(from: LatLon?, to: Place, transit: Boolean, at: LocalDateTime) = change { p, m ->
        val u = read(p)
        m[tripsKey] = TripRecord.encodeAll(keepTrips(u.trips + TripRecord(to.name, to.lat, to.lon, at, transit), at))
        val arrival = u.lastArrival ?: return@change
        if (from != null && distanceMeters(from.lat, from.lon, arrival.lat, arrival.lon) <= STILL_THERE_M) {
            val minutes = Duration.between(arrival.at, at).toMinutes()
            if (minutes in 5..(12 * 60)) {
                val stays = (u.stays + Stay(arrival.name, minutes.toInt())).takeLast(30)
                m[staysKey] = JSONArray(stays.map { JSONObject().put("name", it.name).put("min", it.minutes) }).toString()
            }
        }
        m.remove(arrivalKey)
    }

    suspend fun setAdapt(on: Boolean) = change { _, m -> m[adaptKey] = on }

    suspend fun setBigButtons(on: Boolean) = change { _, m -> m[bigButtonsKey] = on }

    suspend fun setCalendar(on: Boolean) = change { _, m -> m[calendarKey] = on; m[askedCalendarKey] = true }

    /** Back to how the app first was: everything learned is forgotten (the switch stays as it is). */
    suspend fun reset() = change { _, m ->
        listOf(bigButtonsKey, askedButtonsKey, askedTextKey, textBoostKey, calendarKey, askedCalendarKey).forEach { m.remove(it) }
        listOf(nearbyKey, pressesKey, zoomDaysKey, routeChoicesKey, arrivalKey, staysKey, tripsKey).forEach { m.remove(it) }
        oldKeys.forEach { m.remove(it) }
        m.remove(lastChangeKey)
    }

    suspend fun current(): Usage = usage.first()
}
