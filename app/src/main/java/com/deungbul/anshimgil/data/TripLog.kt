package com.deungbul.anshimgil.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.time.LocalDateTime
import kotlin.math.roundToInt

/*
 * 이동 기록: where and when the walker went, so AI 비서 can offer the usual trip ("8시 반에 문화전당
 * 가시죠?", the server's trip_habit) and learn bus or walk by weather (trip_weather_choice). Only with
 * the walker's yes, asked once after the first finished guidance; switched off or wiped in settings.
 * The destination goes rounded to about 100 m, never the start, never the path.
 *
 * The server table exists (trip_log, 027) but not yet a way in: trips wait on the phone and go as soon
 * as `POST /api/trips` answers (see [TripLogRepository.uploadPending]).
 */

/** The walker's answer about keeping trips: not asked yet, yes, or no. */
enum class TripConsent { UNASKED, YES, NO }

/** One finished trip, as it is sent. [destLat]/[destLon] are already rounded ([roundTo100m]). */
data class TripRecord(
    val destName: String,
    val destLat: Double,
    val destLon: Double,
    /** "2026-11-06T08:25", Korean time. */
    val startedAt: String,
    /** walk or transit */
    val by: String,
    val mode: UserMode,
    /** rain, heat, cold or clear (as far as the route knew). */
    val weather: String,
) {
    fun encode(): String = JSONObject()
        .put("destName", destName).put("destLat", destLat).put("destLon", destLon)
        .put("startedAt", startedAt).put("by", by).put("mode", mode.key).put("weather", weather)
        .toString()

    companion object {
        fun decode(line: String): TripRecord? = runCatching {
            val j = JSONObject(line)
            TripRecord(
                j.getString("destName"), j.getDouble("destLat"), j.getDouble("destLon"), j.getString("startedAt"),
                j.getString("by"), UserMode.entries.first { it.key == j.getString("mode") }, j.getString("weather"),
            )
        }.getOrNull()

        /** Kept on the phone while the server is not ready. */
        const val KEEP = 200
    }
}

/** About 100 m: three decimals of a degree (111 m north–south, about 90 m east–west here). */
fun roundTo100m(value: Double): Double = (value * 1000).roundToInt() / 1000.0

/** The record for a finished guidance to [destination], started at [startedAt]. */
fun tripRecord(destination: Place, startedAt: LocalDateTime, transit: Boolean, mode: UserMode, weather: String): TripRecord =
    TripRecord(
        destName = destination.name.take(40),
        destLat = roundTo100m(destination.lat),
        destLon = roundTo100m(destination.lon),
        startedAt = startedAt.withSecond(0).withNano(0).toString().take(16),
        by = if (transit) "transit" else "walk",
        mode = mode,
        weather = weather,
    )

/** The weather word for a route: rain, heat or cold when the server said so, otherwise clear. */
fun tripWeather(result: RouteResult?): String = when {
    result == null -> "clear"
    result.raining -> "rain"
    "폭염" in result.weather -> "heat"
    "한파" in result.weather -> "cold"
    else -> "clear"
}

/** The body for `POST /api/trips`. */
internal fun tripJson(guestId: String, trip: TripRecord): JSONObject = JSONObject(trip.encode()).put("guestId", guestId)

class TripLogRepository(private val context: Context, private val guestId: suspend () -> String) {
    private val consentKey = stringPreferencesKey("trip_consent")
    private val pendingKey = stringPreferencesKey("trips_pending")

    val consent: Flow<TripConsent> = context.settingsDataStore.data.map { prefs ->
        runCatching { TripConsent.valueOf(prefs[consentKey] ?: "UNASKED") }.getOrDefault(TripConsent.UNASKED)
    }

    /** Trips waiting to go to the server. */
    val pendingCount: Flow<Int> = context.settingsDataStore.data.map { prefs -> prefs[pendingKey].orEmpty().lines().count { it.isNotBlank() } }

    suspend fun setConsent(value: TripConsent) {
        context.settingsDataStore.edit { it[consentKey] = value.name }
        // Saying no also drops what was waiting to go.
        if (value == TripConsent.NO) context.settingsDataStore.edit { it.remove(pendingKey) }
    }

    /** Keeps [trip] when the walker said yes, then tries to send. */
    suspend fun record(trip: TripRecord) {
        if (consent.first() != TripConsent.YES) return
        context.settingsDataStore.edit {
            it[pendingKey] = (it[pendingKey].orEmpty().lines().filter { l -> l.isNotBlank() } + trip.encode())
                .takeLast(TripRecord.KEEP).joinToString("\n")
        }
        uploadPending()
    }

    /**
     * Sends waiting trips (`POST /api/trips`). While the server has no such door yet (NOT_FOUND) or the
     * phone is offline they stay; a trip the server refuses (BAD_PARAM) is dropped so it does not block the rest.
     */
    suspend fun uploadPending() {
        if (!ServerApi.isConfigured || consent.first() != TripConsent.YES) return
        val lines = context.settingsDataStore.data.first()[pendingKey].orEmpty().lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return
        val guest = guestId()
        val done = mutableListOf<String>()
        for (line in lines) {
            val trip = TripRecord.decode(line) ?: run { done += line; null } ?: continue
            try {
                ServerApi.post("/api/trips", tripJson(guest, trip))
                done += line
            } catch (e: ApiException) {
                if (e.code == "BAD_PARAM") done += line else break
            }
        }
        if (done.isNotEmpty()) {
            context.settingsDataStore.edit { prefs ->
                prefs[pendingKey] = prefs[pendingKey].orEmpty().lines().filter { it.isNotBlank() && it !in done }.joinToString("\n")
            }
        }
    }

    /**
     * Wipes the trips: those waiting on the phone at once, and asks the server to delete this guest's
     * (`DELETE /api/trips`). Returns false when the server could not be reached or has no such door yet.
     */
    suspend fun clearAll(): Boolean {
        context.settingsDataStore.edit { it.remove(pendingKey) }
        if (!ServerApi.isConfigured) return true
        return try {
            ServerApi.delete("/api/trips", mapOf("guestId" to guestId()))
            true
        } catch (e: ApiException) {
            false
        }
    }
}
