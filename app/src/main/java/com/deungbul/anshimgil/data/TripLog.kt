package com.deungbul.anshimgil.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import kotlin.math.roundToInt

/*
 * 이동 기록: where the walker goes and when, so AI 비서 can offer the usual trip ("8시 반에 문화전당
 * 가시죠?") and learn bus or walk by weather. Only with the walker's yes, asked once in AI 비서's live
 * bubble after the first finished guidance; switched off or wiped in settings.
 *
 * Server (팀 할 일 5번, safe-route me/): the yes or no goes with `PATCH /api/me` (consentTrips; no also
 * deletes what was kept), each guidance starts a record with `POST /api/me/trips` and ends it with
 * `PATCH /api/me/trips/{id}`. The server sets the day and the 30-minute slot, takes no start position, and
 * keeps the destination rounded to about 100 m; the app rounds it too before sending.
 */

/** The walker's answer about keeping trips: not asked yet, yes, or no. */
enum class TripConsent { UNASKED, YES, NO }

/** About 100 m: three decimals of a degree (111 m north–south, about 90 m east–west here). */
fun roundTo100m(value: Double): Double = (value * 1000).roundToInt() / 1000.0

/** The weather word for a route: rain, heat or cold when the server said so, otherwise clear. */
fun tripWeather(result: RouteResult?): String = when {
    result == null -> "clear"
    result.raining -> "rain"
    "폭염" in result.weather -> "heat"
    "한파" in result.weather -> "cold"
    else -> "clear"
}

/** The body for `POST /api/me/trips`: the destination rounded, never the start or the path. */
internal fun tripStartJson(
    guestId: String,
    destination: Place,
    mode: UserMode,
    transit: Boolean,
    routeKind: String?,
    weather: String,
    night: Boolean,
    plannedMin: Int?,
): JSONObject = JSONObject()
    .put("guestId", guestId)
    .put("toName", destination.name.take(100))
    .put("toLat", roundTo100m(destination.lat))
    .put("toLon", roundTo100m(destination.lon))
    .put("mode", mode.key)
    .put("travel", if (transit) "transit" else "walk")
    .put("routeKind", routeKind ?: JSONObject.NULL)
    .put("weather", weather)
    .put("night", night)
    .put("plannedMin", plannedMin ?: JSONObject.NULL)

class TripLogRepository(private val context: Context, private val guestId: suspend () -> String) {
    private val consentKey = stringPreferencesKey("trip_consent")

    val consent: Flow<TripConsent> = context.settingsDataStore.data.map { prefs ->
        runCatching { TripConsent.valueOf(prefs[consentKey] ?: "UNASKED") }.getOrDefault(TripConsent.UNASKED)
    }

    /**
     * Keeps the answer on the phone and tells the server (`PATCH /api/me`). No also deletes the trips
     * the server kept. Returns false when the server could not be told (the phone keeps the answer).
     */
    suspend fun setConsent(value: TripConsent, mode: UserMode): Boolean {
        context.settingsDataStore.edit { it[consentKey] = value.name }
        if (value == TripConsent.UNASKED || !ServerApi.isConfigured) return true
        return try {
            ServerApi.patch("/api/me", JSONObject().put("guestId", guestId()).put("mode", mode.key).put("consentTrips", value == TripConsent.YES))
            true
        } catch (e: ApiException) {
            false
        }
    }

    /** At the start of a guidance (when the walker said yes): the record's id, or null when it could not be kept. */
    suspend fun start(destination: Place, mode: UserMode, transit: Boolean, routeKind: String?, weather: String, night: Boolean, plannedMin: Int?): Long? {
        if (!ServerApi.isConfigured || consent.first() != TripConsent.YES) return null
        return try {
            ServerApi.post("/api/me/trips", tripStartJson(guestId(), destination, mode, transit, routeKind, weather, night, plannedMin))
                .optLong("id").takeIf { it > 0 }
        } catch (e: ApiException) {
            // NO_CONSENT: the server never heard the yes (offline then); tell it again for next time.
            if (e.code == "NO_CONSENT") runCatching { setConsent(TripConsent.YES, mode) }
            null
        }
    }

    /** At the end: arrived or stopped, and how long it took. */
    suspend fun end(id: Long, arrived: Boolean, actualMin: Int?) {
        if (!ServerApi.isConfigured) return
        runCatching {
            ServerApi.patch(
                "/api/me/trips/$id",
                JSONObject().put("guestId", guestId()).put("result", if (arrived) "arrived" else "stopped")
                    .put("actualMin", actualMin ?: JSONObject.NULL),
            )
        }
    }

    /**
     * The walker's word after arriving (good, or bad and why), sent with the trip again; what the server learned
     * from it, in its own words ("앞으로 계단이 적은 길로 안내할게요"), empty when nothing changed or offline.
     */
    suspend fun feedback(id: Long, arrived: Boolean, actualMin: Int?, feedback: String, reason: String?): List<String> {
        if (!ServerApi.isConfigured) return emptyList()
        return runCatching {
            val json = ServerApi.patch(
                "/api/me/trips/$id",
                JSONObject().put("guestId", guestId()).put("result", if (arrived) "arrived" else "stopped")
                    .put("actualMin", actualMin ?: JSONObject.NULL)
                    .put("feedback", feedback).put("feedbackReason", reason ?: JSONObject.NULL),
            )
            learnedSays(json)
        }.getOrDefault(emptyList())
    }

    /** How many trips the server keeps for this phone; null when it could not be asked. */
    suspend fun count(): Int? {
        if (!ServerApi.isConfigured) return null
        return runCatching { ServerApi.get("/api/me", mapOf("guestId" to guestId())).optInt("trips") }.getOrNull()
    }

    /**
     * Wipes the trips on the server: consent off deletes them, and on again when the walker still wants
     * them kept. Returns false when the server could not be reached.
     */
    suspend fun clearAll(mode: UserMode): Boolean {
        val keep = consent.first() == TripConsent.YES
        if (!setConsent(TripConsent.NO, mode)) {
            if (keep) context.settingsDataStore.edit { it[consentKey] = TripConsent.YES.name }
            return false
        }
        return if (keep) setConsent(TripConsent.YES, mode) else true
    }
}

/** AI 비서's question in its live bubble on the map, after the first finished guidance. */
// The team's words (docs/assistant/consent.md §1), after the first finished guidance.
const val TRIP_CONSENT_QUESTION =
    "길 안내를 마쳤어요. 다닌 기록을 남겨도 될까요? 기록을 남기면 비서가 더 잘 도와드려요. " +
        "남기는 것: 간 곳의 이름, 나간 요일과 시간대(30분 단위), 걸어갔는지 버스를 탔는지, 그날 날씨, 다녀와서 남긴 한마디. " +
        "남기지 않는 것: 이름, 전화번호, 집 주소, 지나온 길, 정확한 시각."

const val TRIP_CONSENT_YES = CONSENT_YES
const val TRIP_CONSENT_NO = CONSENT_NO

/** The sentences in the server's answer to a finished trip (learned[].say). */
internal fun learnedSays(json: JSONObject): List<String> {
    val a = json.optJSONArray("learned") ?: return emptyList()
    return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("say")?.takeIf { s -> s.isNotBlank() } }
}
