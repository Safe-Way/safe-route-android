package com.deungbul.anshimgil.data.user

import androidx.datastore.preferences.core.edit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan
import com.deungbul.anshimgil.data.place.Bbox
import com.deungbul.anshimgil.data.place.EventRepository
import com.deungbul.anshimgil.data.place.Place
import com.deungbul.anshimgil.data.place.RoadEvent
import com.deungbul.anshimgil.data.place.distanceMeters
import com.deungbul.anshimgil.data.route.EVENING_BEFORE_SUNSET_MIN
import com.deungbul.anshimgil.data.route.LatLon
import com.deungbul.anshimgil.data.route.autoNightReason
import com.deungbul.anshimgil.data.route.night
import com.deungbul.anshimgil.data.server.ServerApi

/*
 * AI 비서 말풍선: one short line under the AI 비서 button on the map, like a pinned comment on a live
 * stream. Only from real data, never filler:
 *  - the walker's usual trip at this time, when the server's assistant recommends it (with the weather
 *    reason in its words) — the only one that asks, and its button opens that route;
 *  - a road event (festival, roadworks, accident, closure) near the walker, from the server;
 *  - sunset within the hour, worked out on the phone; routes then use night rules by themselves.
 * Nothing applies, nothing shows. Each line shows once per app run.
 */

/** One bubble. [place] is set when the bubble offers a route there (its button opens it). */
data class MapHint(
    val key: String,
    val text: String,
    val place: Place? = null,
    /** Offer [바로 출발]: guidance starts at once with the route the walker usually picks. */
    val startNow: Boolean = false,
    /** The suggestion was the bus or subway: open the 대중교통 tab. */
    val transit: Boolean = false,
    /** A suggested trip is taken down at this time (past the usual time, the event started). */
    val until: LocalDateTime? = null,
)

/** Lines already shown while the app runs, so leaving the map and coming back does not repeat them. */
object ShownHints {
    private val keys = mutableSetOf<String>()
    private var prefs: android.content.SharedPreferences? = null

    /**
     * Suggestions of a trip (a habit, a calendar event) are remembered across app starts, so that one closed
     * with ✕ does not come back each time the app opens that day. Their keys carry the day.
     */
    fun init(context: android.content.Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences("shown_hints", android.content.Context.MODE_PRIVATE).also {
            keys += it.getStringSet("keys", emptySet()).orEmpty()
        }
    }

    fun isNew(key: String) = key !in keys

    /** A habit for [name] today was already offered (so the server's suggestion of the same place is not repeated). */
    fun habitOffered(today: LocalDate, name: String) = "habit-$today-$name" in keys

    fun mark(key: String) {
        keys += key
        if (key.startsWith("habit-") || key.startsWith("cal-")) {
            prefs?.edit()?.putStringSet("keys", keys.filter { it.startsWith("habit-") || it.startsWith("cal-") }.takeLast(40).toSet())?.apply()
        }
    }
}

private val KST: ZoneId = ZoneId.of("Asia/Seoul")

/** Today's sunset at [lat], [lon] in Korean time (NOAA's simple formula, within a minute or two). Null in polar day or night. */
fun sunsetAt(date: LocalDate, lat: Double, lon: Double): LocalTime? {
    val n = date.dayOfYear
    val gamma = 2 * Math.PI / 365 * (n - 1)
    val eqTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
        0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
    val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) - 0.006758 * cos(2 * gamma) +
        0.000907 * sin(2 * gamma) - 0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
    val latR = Math.toRadians(lat)
    val cosHa = cos(Math.toRadians(90.833)) / (cos(latR) * cos(decl)) - tan(latR) * tan(decl)
    if (cosHa < -1 || cosHa > 1) return null
    val ha = Math.toDegrees(acos(cosHa))
    val utcMinutes = 720 - 4 * (lon - ha) - eqTime
    val offset = KST.rules.getOffset(date.atStartOfDay()).totalSeconds / 60
    val minutes = ((utcMinutes + offset).roundToInt() % 1440 + 1440) % 1440
    return LocalTime.of(minutes / 60, minutes % 60)
}

/** Within the hour before sunset: say so, since routes switch to night rules from then (autoNightReason). */
fun eveningHint(now: LocalDateTime, sunset: LocalTime?): MapHint? {
    sunset ?: return null
    val left = java.time.Duration.between(now.toLocalTime(), sunset).toMinutes()
    if (left !in 1..EVENING_BEFORE_SUNSET_MIN) return null
    val time = "%d시 %02d분".format(if (sunset.hour > 12) sunset.hour - 12 else sunset.hour, sunset.minute)
    return MapHint("sunset-${now.toLocalDate()}", "${time}에 해가 져요. 지금부터는 밝은 길로 먼저 안내할게요.")
}

/** How near (beyond its own radius) an event must be to mention. */
private const val EVENT_NEAR_EXTRA_M = 500

/** The nearest road event whose area is within [EVENT_NEAR_EXTRA_M] of [here]. */
fun eventHint(here: LatLon, events: List<RoadEvent>): MapHint? {
    val (event, meters) = events
        .map { it to distanceMeters(here.lat, here.lon, it.lat, it.lon) }
        .filter { (e, d) -> d <= e.radiusM + EVENT_NEAR_EXTRA_M }
        .minByOrNull { it.second } ?: return null
    val where = if (meters <= event.radiusM) "지금 계신 곳 근처에서" else "약 ${((meters - event.radiusM) / 50 + 1) * 50}m 떨어진 곳에서"
    val what = when (event.kind) {
        "행사" -> "'${event.title}' 행사가 열리고 있어요"
        "공사" -> "공사를 하고 있어요 (${event.title})"
        "사고" -> "사고가 났어요 (${event.title})"
        "통제" -> "길을 막고 있어요 (${event.title})"
        else -> "${event.kind} 상황이 있어요 (${event.title})"
    }
    return MapHint("event-${event.id}", "$where $what. 그쪽을 지나게 되면 미리 알려 드릴게요.")
}

/** When the app is opened: the first time today, at a time it is often opened, or neither. */
enum class OpenMoment { FIRST_TODAY, USUAL_TIME, OTHER }

/** How many earlier days opened in the same half hour make it a usual time. */
const val USUAL_OPEN_DAYS = 3

/**
 * Which moment [now] is, given earlier opens [history] ("2026-11-06|08:30" — the day and its half hour).
 * Only days within the last 30 count, each day once per half hour.
 */
fun openMoment(history: List<String>, now: LocalDateTime): OpenMoment {
    val today = now.toLocalDate()
    val slot = halfHour(now.toLocalTime())
    val earlier = history.mapNotNull { line ->
        val (d, t) = line.split('|').takeIf { it.size == 2 } ?: return@mapNotNull null
        runCatching { LocalDate.parse(d) to t }.getOrNull()
    }.filter { (d, _) -> d.isBefore(today) && !d.isBefore(today.minusDays(30)) }
    val openedToday = history.any { it.startsWith("$today|") }
    return when {
        !openedToday -> OpenMoment.FIRST_TODAY
        earlier.filter { it.second == slot }.map { it.first }.distinct().size >= USUAL_OPEN_DAYS -> OpenMoment.USUAL_TIME
        else -> OpenMoment.OTHER
    }
}

/** "08:30" for any time from 8:30 to 8:59. */
fun halfHour(t: LocalTime): String = "%02d:%02d".format(t.hour, if (t.minute < 30) 0 else 30)

/**
 * App opens kept on this phone only (never sent), to tell the first open of the day and usual times.
 * Kept only while [keep] says so: the walker said yes to keeping trips (팀 결정 11-09).
 */
class AppOpenLog(private val context: android.content.Context, private val keep: suspend () -> Boolean = { true }) {
    private val key = androidx.datastore.preferences.core.stringPreferencesKey("app_opens")

    /** Notes this open (when kept) and says which moment it is. */
    suspend fun record(now: LocalDateTime): OpenMoment {
        var moment = OpenMoment.OTHER
        val keeping = runCatching { keep() }.getOrDefault(false)
        context.settingsDataStore.edit { prefs ->
            val history = prefs[key].orEmpty().lines().filter { it.isNotBlank() }
            moment = openMoment(history, now)
            if (!keeping) return@edit
            val line = "${now.toLocalDate()}|${halfHour(now.toLocalTime())}"
            prefs[key] = (history.filter { it != line } + line).takeLast(200).joinToString("\n")
        }
        return moment
    }

    /** Forgets every open noted (on saying no to keeping trips). */
    suspend fun clear() {
        context.settingsDataStore.edit { it.remove(key) }
    }
}

/**
 * The bubble from the server's today suggestion (`GET /api/suggestions/today`): the usual trip at this time
 * with its route, or else what the walker usually does in this weather, or else bad weather itself.
 * Null when there is nothing but the general greeting.
 */
internal fun todayHint(json: org.json.JSONObject, today: LocalDate): MapHint? {
    fun org.json.JSONObject.text(k: String) = optString(k).takeIf { !isNull(k) && it.isNotBlank() }
    val habit = json.optJSONObject("habit")
    if (habit != null) {
        val dest = json.optJSONObject("pick")?.optJSONObject("request")?.optJSONObject("destination")
        val name = dest?.text("name") ?: habit.text("toName") ?: return null
        val place = Place(
            id = null, name = name, address = "",
            lat = dest?.optDouble("lat") ?: habit.getDouble("lat"),
            lon = dest?.optDouble("lon") ?: habit.getDouble("lon"),
            distanceM = null,
        )
        val say = json.text("say") ?: "이 시간엔 보통 ${name}에 가셨어요."
        val transit = json.optJSONObject("pick")?.optString("type") == "transit"
        return MapHint("today-$today-$name", say, place, startNow = true, transit = transit)
    }
    val weather = json.optJSONObject("weather")
    val word = weather?.takeIf { it.optBoolean("weatherAvailable") || it.optBoolean("dustAvailable") }?.text("word")
    val temp = weather?.takeIf { !it.isNull("tempC") && it.has("tempC") }?.let { " (${it.optDouble("tempC").roundToInt()}°C)" }.orEmpty()
    val now = when (word) {
        "rain" -> "지금 비가 와요$temp. 미끄러운 길을 조심하세요."
        "heat" -> "지금 무척 더워요$temp. 그늘과 쉼터를 챙기세요."
        "cold" -> "지금 무척 추워요$temp. 따뜻하게 입고 나가세요."
        "dust" -> "지금 미세먼지가 나빠요. 마스크를 챙기세요."
        else -> null
    }
    val hint = json.text("weatherHint")
    val text = listOfNotNull(now, hint).joinToString(" ").takeIf { it.isNotBlank() } ?: return null
    return MapHint("today-$today-$word", text)
}

/** Picks the bubble for the map: today's suggestion (first open today or a usual time), then a nearby event, then sunset. */
class MapHintSource(
    private val events: EventRepository,
    private val guestId: suspend () -> String?,
    /** Records this app start; null skips today's suggestion. */
    private val openLog: AppOpenLog? = null,
    /** What has been learned on this phone, for the way home. */
    private val usage: suspend () -> Usage? = { null },
    /** The saved home (자주 가는 곳 '집'), when there is one. */
    private val home: suspend () -> Place? = { null },
    /** An upcoming calendar event with a place (when switched on), see [calendarHint]. */
    private val calendar: suspend (LatLon, LocalDateTime) -> MapHint? = { _, _ -> null },
) {
    // Worked out once per app start, on the first bubble.
    private var moment: OpenMoment? = null

    suspend fun next(mode: UserMode, here: LatLon, now: LocalDateTime = LocalDateTime.now(KST)): MapHint? {
        val m = moment ?: (openLog?.let { runCatching { it.record(now) }.getOrNull() } ?: OpenMoment.OTHER).also { moment = it }
        val learned = runCatching { usage() }.getOrNull()
        // An event the walker wrote down themselves comes first.
        if (learned?.adapt == true && learned.calendarOn) {
            runCatching { calendar(here, now) }.getOrNull()?.takeIf { ShownHints.isNew(it.key) }?.let { return it }
        }
        // A habit learned on this phone (이동 패턴), around its usual time: it knows the walker within a week.
        if (learned?.adapt == true) {
            habitNow(learned.trips, now, here)?.let { habitHint(it, now.toLocalDate()) }?.takeIf { ShownHints.isNew(it.key) }?.let { return it }
        }
        if (ServerApi.isConfigured && m != OpenMoment.OTHER) {
            val today = runCatching {
                val guest = guestId() ?: return@runCatching null
                todayHint(
                    ServerApi.get("/api/suggestions/today", mapOf("guestId" to guest, "lat" to here.lat, "lon" to here.lon, "mode" to mode.key)),
                    now.toLocalDate(),
                )
            }.getOrNull()
            if (today != null && ShownHints.isNew(today.key) && !ShownHints.habitOffered(now.toLocalDate(), today.place?.name.orEmpty())) return today
        }
        // After the usual stay where the walker arrived: the way home.
        runCatching { learned?.let { u -> returnHint(u, here, now, home()) } }.getOrNull()
            ?.takeIf { ShownHints.isNew(it.key) }?.let { return it }
        val event = runCatching {
            eventHint(here, events.within(Bbox(here.lon - 0.012, here.lat - 0.01, here.lon + 0.012, here.lat + 0.01)))
        }.getOrNull()
        if (event != null && ShownHints.isNew(event.key)) return event
        return eveningHint(now, sunsetAt(now.toLocalDate(), here.lat, here.lon))?.takeIf { ShownHints.isNew(it.key) }
    }
}
