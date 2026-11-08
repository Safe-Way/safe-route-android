package com.deungbul.anshimgil.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDateTime

/*
 * 쓰는 대로 맞추기 (11/8): the app watches how it is used — on this phone only, never sent — and
 *  - offers bigger buttons when buttons are tapped again and again, bigger text when the map is zoomed a lot;
 *  - puts the nearby buttons used most first;
 *  - picks the kind of route the walker keeps choosing (safe, shortest, transit) first;
 *  - after arriving somewhere, offers the way home once the usual stay there is over.
 * Each change is offered by AI 비서 first (a yes/no bubble), except the button order and the route
 * picked first, which only reorder what is already there. Settings can switch it off or undo it.
 */

/** The nearby-place buttons on the map, in their first order. */
val NEARBY_KEYS = listOf("store", "police", "toilet")

/** Quick taps on the same button within this long count as tapping again (it did not seem to work, or was hard to hit). */
const val RETAP_WINDOW_MS = 900L

/** After this many re-taps AI 비서 offers bigger buttons. */
const val RETAPS_FOR_BIG_BUTTONS = 3

/** After this many zoom-ins on the map AI 비서 offers bigger text. */
const val ZOOMS_FOR_BIG_TEXT = 5

/** How many recent route choices are kept, and how many of them must agree to pick that kind first. */
const val ROUTE_CHOICES_KEPT = 4
const val ROUTE_CHOICES_AGREE = 3

/** Kinds of route the walker can choose. */
enum class RouteChoice { SAFE, SHORTEST, TRANSIT }

/** What has been learned so far. */
data class Usage(
    /** Switched on by default; off keeps everything as it first was. */
    val adapt: Boolean = true,
    val bigButtons: Boolean = false,
    val nearbyTaps: Map<String, Int> = emptyMap(),
    val retaps: Int = 0,
    val zoomIns: Int = 0,
    val askedBigButtons: Boolean = false,
    val askedBigText: Boolean = false,
    /** Most recent last. */
    val routeChoices: List<RouteChoice> = emptyList(),
    val lastArrival: Arrival? = null,
    /** Minutes spent at a place before leaving again, most recent last. */
    val stays: List<Int> = emptyList(),
)

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

/** The nearby buttons, most used first; ties keep the first order. */
fun nearbyOrder(usage: Usage): List<String> =
    if (!usage.adapt) NEARBY_KEYS else NEARBY_KEYS.sortedByDescending { usage.nearbyTaps[it] ?: 0 }

/** The kind of route to show first: the one chosen at least [ROUTE_CHOICES_AGREE] of the last [ROUTE_CHOICES_KEPT] times. */
fun preferredRoute(usage: Usage): RouteChoice? {
    if (!usage.adapt) return null
    val recent = usage.routeChoices.takeLast(ROUTE_CHOICES_KEPT)
    return recent.groupingBy { it }.eachCount().entries.firstOrNull { it.value >= ROUTE_CHOICES_AGREE }?.key
}

/** A change AI 비서 can offer, with its question and the answer that accepts it. */
enum class Adaptation(val question: String, val yes: String) {
    BIG_BUTTONS("버튼을 여러 번 누르시는 것 같아요. 버튼을 더 크게 바꿔 드릴까요?", "크게 바꿔 줘"),
    BIG_TEXT("지도를 자주 크게 보시네요. 글씨를 더 크게 바꿔 드릴까요?", "글씨 크게"),
}

/** The change to offer now, if any (one at a time, each asked once). */
fun adaptationToOffer(usage: Usage, textSize: TextSize): Adaptation? = when {
    !usage.adapt -> null
    !usage.bigButtons && !usage.askedBigButtons && usage.retaps >= RETAPS_FOR_BIG_BUTTONS -> Adaptation.BIG_BUTTONS
    textSize != TextSize.LARGE && !usage.askedBigText && usage.zoomIns >= ZOOMS_FOR_BIG_TEXT -> Adaptation.BIG_TEXT
    else -> null
}

/** The usual stay at a place in minutes (the middle of the stays seen), once there are at least two. */
fun usualStayMinutes(usage: Usage): Int? =
    usage.stays.takeIf { it.size >= 2 }?.sorted()?.let { it[it.size / 2] }

/** How near the last arrival the walker must still be for "집으로 갈까요?". */
const val STILL_THERE_M = 300

/**
 * "문화전당에 온 지 2시간 지났어요. 집으로 갈까요?": after the usual stay at the place last arrived at, while still
 * near it, the same day; null otherwise or without a saved home.
 */
fun returnHint(usage: Usage, here: LatLon, now: LocalDateTime, home: Place?): MapHint? {
    if (!usage.adapt || home == null) return null
    val arrival = usage.lastArrival ?: return null
    if (arrival.at.toLocalDate() != now.toLocalDate()) return null
    if (distanceMeters(here.lat, here.lon, arrival.lat, arrival.lon) > STILL_THERE_M) return null
    // Already home: nothing to offer.
    if (distanceMeters(arrival.lat, arrival.lon, home.lat, home.lon) < STILL_THERE_M) return null
    val stay = usualStayMinutes(usage) ?: return null
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
    private val retapsKey = stringPreferencesKey("retaps")
    private val zoomsKey = stringPreferencesKey("zoom_ins")
    private val askedButtonsKey = booleanPreferencesKey("asked_big_buttons")
    private val askedTextKey = booleanPreferencesKey("asked_big_text")
    private val routeChoicesKey = stringPreferencesKey("route_choices")
    private val arrivalKey = stringPreferencesKey("last_arrival")
    private val staysKey = stringPreferencesKey("stays")

    val usage: Flow<Usage> = context.settingsDataStore.data.map(::read)

    private fun read(p: Preferences) = Usage(
        adapt = p[adaptKey] ?: true,
        bigButtons = p[bigButtonsKey] ?: false,
        nearbyTaps = p[nearbyKey].orEmpty().split(',').mapNotNull { e ->
            e.split('=').takeIf { it.size == 2 }?.let { (k, v) -> v.toIntOrNull()?.let { k to it } }
        }.toMap(),
        retaps = p[retapsKey]?.toIntOrNull() ?: 0,
        zoomIns = p[zoomsKey]?.toIntOrNull() ?: 0,
        askedBigButtons = p[askedButtonsKey] ?: false,
        askedBigText = p[askedTextKey] ?: false,
        routeChoices = p[routeChoicesKey].orEmpty().split(',').mapNotNull { runCatching { RouteChoice.valueOf(it) }.getOrNull() },
        lastArrival = Arrival.decode(p[arrivalKey]),
        stays = p[staysKey].orEmpty().split(',').mapNotNull { it.toIntOrNull() },
    )

    private suspend fun change(block: (Preferences, androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { block(it, it) }
    }

    suspend fun tapNearby(key: String) = change { p, m ->
        val taps = read(p).nearbyTaps.toMutableMap()
        taps[key] = (taps[key] ?: 0) + 1
        m[nearbyKey] = taps.entries.joinToString(",") { "${it.key}=${it.value}" }
    }

    suspend fun retap() = change { p, m -> m[retapsKey] = (read(p).retaps + 1).toString() }

    suspend fun zoomIn() = change { p, m -> m[zoomsKey] = (read(p).zoomIns + 1).toString() }

    suspend fun answer(adaptation: Adaptation, yes: Boolean) = change { _, m ->
        when (adaptation) {
            Adaptation.BIG_BUTTONS -> { m[askedButtonsKey] = true; if (yes) m[bigButtonsKey] = true }
            Adaptation.BIG_TEXT -> m[askedTextKey] = true
        }
    }

    suspend fun chooseRoute(choice: RouteChoice) = change { p, m ->
        m[routeChoicesKey] = (read(p).routeChoices + choice).takeLast(ROUTE_CHOICES_KEPT).joinToString(",")
    }

    /** At the end of a guidance that arrived. */
    suspend fun arrived(place: Place, at: LocalDateTime) = change { _, m ->
        m[arrivalKey] = Arrival(place.name, place.lat, place.lon, at).encode()
    }

    /** At the start of a guidance: when it starts near the last arrival, the stay there is learned. */
    suspend fun leaving(from: LatLon?, at: LocalDateTime) = change { p, m ->
        val u = read(p)
        val arrival = u.lastArrival ?: return@change
        if (from == null || distanceMeters(from.lat, from.lon, arrival.lat, arrival.lon) > STILL_THERE_M) return@change
        val minutes = Duration.between(arrival.at, at).toMinutes()
        if (minutes in 5..(12 * 60)) m[staysKey] = (u.stays + minutes.toInt()).takeLast(10).joinToString(",")
        m.remove(arrivalKey)
    }

    suspend fun setAdapt(on: Boolean) = change { _, m -> m[adaptKey] = on }

    suspend fun setBigButtons(on: Boolean) = change { _, m -> m[bigButtonsKey] = on }

    /** Back to how the app first was: everything learned is forgotten (the switch stays as it is). */
    suspend fun reset() = change { _, m ->
        listOf(bigButtonsKey, askedButtonsKey, askedTextKey).forEach { m.remove(it) }
        listOf(nearbyKey, retapsKey, zoomsKey, routeChoicesKey, arrivalKey, staysKey).forEach { m.remove(it) }
    }

    suspend fun current(): Usage = usage.first()
}
