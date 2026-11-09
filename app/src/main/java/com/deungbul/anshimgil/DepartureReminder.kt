package com.deungbul.anshimgil

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.deungbul.anshimgil.data.ModeRepository
import com.deungbul.anshimgil.data.ServerApi
import com.deungbul.anshimgil.data.TripConsent
import com.deungbul.anshimgil.data.TripLogRepository
import com.deungbul.anshimgil.data.UsageRepository
import com.deungbul.anshimgil.data.WalkSpeedRepository
import com.deungbul.anshimgil.data.STILL_THERE_M
import com.deungbul.anshimgil.data.distanceMeters
import com.deungbul.anshimgil.data.habitNow
import com.deungbul.anshimgil.data.sayTime
import com.deungbul.anshimgil.location.lastKnownLocation
import com.deungbul.anshimgil.data.settingsDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/*
 * 출발 시간 알림 (11/8): every 15 minutes, also with the app closed, ask the server's today suggestion; when the
 * walker usually leaves for somewhere within the next 20 minutes (the same weekday and half hour, 3 times or
 * more), send one notification: "8시 30분에 국립아시아문화전당 가시죠? 눌러서 길 보기". Only with the trip-record
 * yes and 쓰는 대로 맞추기 on; the position is not sent (the server suggests from the habit alone).
 * Before that, the habit learned on this phone ([habitNow], from the first week, weekdays together). Not sent
 * while the walker is already there.
 */

/** How long before the usual time the reminder comes. */
const val REMIND_BEFORE_MIN = 20L

/** Intent extras that open the route to the reminded place. */
const val EXTRA_GO_NAME = "go_name"
const val EXTRA_GO_LAT = "go_lat"
const val EXTRA_GO_LON = "go_lon"
const val EXTRA_GO_TRANSIT = "go_transit"

/**
 * The reminder to send now from a today-suggestion answer, or null: a habit whose half hour starts within the
 * next [REMIND_BEFORE_MIN] minutes (or started up to 5 minutes ago).
 */
internal fun reminderFor(json: JSONObject, now: LocalTime): Reminder? {
    val habit = json.optJSONObject("habit") ?: return null
    val slot = runCatching { LocalTime.parse(habit.optString("slot")) }.getOrNull() ?: return null
    val until = java.time.Duration.between(now, slot).toMinutes()
    if (until !in -5..REMIND_BEFORE_MIN) return null
    val name = habit.optString("toName").takeIf { it.isNotBlank() } ?: return null
    val dest = json.optJSONObject("pick")?.optJSONObject("request")?.optJSONObject("destination")
    val time = "%d시${if (slot.minute > 0) " %d분" else ""}".format(if (slot.hour > 12) slot.hour - 12 else slot.hour, slot.minute)
        .replace(" 30분", " 반")
    val say = json.optString("say").takeIf { it.isNotBlank() && it.contains(name) } ?: "${time}에 ${name} 가시죠?"
    return Reminder(
        key = "${java.time.LocalDate.now()}-$slot-$name",
        text = say,
        name = name,
        lat = dest?.optDouble("lat") ?: habit.getDouble("lat"),
        lon = dest?.optDouble("lon") ?: habit.getDouble("lon"),
        transit = json.optJSONObject("pick")?.optString("type") == "transit",
    )
}

data class Reminder(val key: String, val text: String, val name: String, val lat: Double, val lon: Double, val transit: Boolean)

class DepartureWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val sentKey = stringPreferencesKey("reminder_sent")

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!ModeStatus.canShow(ctx)) return Result.success()
        val usage = UsageRepository(ctx).current()
        if (!usage.adapt) return Result.success()
        val now = java.time.LocalDateTime.now(ZoneId.of("Asia/Seoul"))
        val here = runCatching { lastKnownLocation(ctx, maxAgeMs = 30 * 60_000L) }.getOrNull()
        // First the habit learned on this phone (이동 패턴): from the first week, nothing sent. With a recent position
        // it is not sent while already there; without one (the phone keeps no fresh fix while the app is closed, so
        // waiting for one meant the reminder never came, 11/9 on the phone) it is still sent, once a day, unless
        // that trip was already started today.
        habitNow(usage.trips, now, here, before = REMIND_BEFORE_MIN.toInt() + 5, after = 5)?.let { h ->
            val reminder = Reminder(
                key = "${now.toLocalDate()}-habit-${h.place.name}",
                text = "보통 ${sayTime(h.time)}쯤 ${h.place.name}에 가셨어요. 길 안내할까요?",
                name = h.place.name, lat = h.place.lat, lon = h.place.lon, transit = h.transit,
            )
            return send(ctx, reminder)
        }
        if (!ServerApi.isConfigured) return Result.success()
        val walk = WalkSpeedRepository(ctx)
        if (TripLogRepository(ctx) { walk.guestId() }.consent.first() != TripConsent.YES) return Result.success()
        val mode = ModeRepository(ctx).mode.first() ?: return Result.success()
        val json = runCatching {
            ServerApi.get("/api/suggestions/today", mapOf("guestId" to walk.guestId(), "mode" to mode.key))
        }.getOrNull() ?: return Result.success()
        val reminder = reminderFor(json, now.toLocalTime()) ?: return Result.success()
        // Not while already there.
        if (here != null && distanceMeters(here.lat, here.lon, reminder.lat, reminder.lon) <= STILL_THERE_M) return Result.success()
        return send(ctx, reminder)
    }

    /** Sends [reminder] unless the same place was already reminded today (by the habit here or the server). */
    private suspend fun send(ctx: Context, reminder: Reminder): Result {
        val today = reminder.key.take(10)
        val sent = ctx.settingsDataStore.data.first()[sentKey].orEmpty().split('\n')
        if ("$today|${reminder.name}" in sent) return Result.success()
        notify(ctx, reminder)
        ctx.settingsDataStore.edit { p -> p[sentKey] = (sent.filter { it.startsWith(today) } + "$today|${reminder.name}").joinToString("\n") }
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "departure"
        private const val ID = 2001
        private const val WORK = "departure-reminder"

        /** Starts the 15-minute check (kept if already running). */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DepartureWorker>(15, TimeUnit.MINUTES).build(),
            )
        }

        fun notify(context: Context, reminder: Reminder) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, "출발 시간 알림", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "자주 가는 곳에 갈 시간이 되면 먼저 길을 알려 줘요"
                    },
                )
            }
            val open = PendingIntent.getActivity(
                context, reminder.key.hashCode(),
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_GO_NAME, reminder.name)
                    .putExtra(EXTRA_GO_LAT, reminder.lat)
                    .putExtra(EXTRA_GO_LON, reminder.lon)
                    .putExtra(EXTRA_GO_TRANSIT, reminder.transit),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_mode_adult)
                .setContentTitle("세잎지도 AI 비서")
                .setContentText(reminder.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText("${reminder.text}\n눌러서 길 보기"))
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(ID, notification)
            } catch (e: SecurityException) {
                // Notifications switched off meanwhile.
            }
        }
    }
}
