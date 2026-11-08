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
        if (!ServerApi.isConfigured || !ModeStatus.canShow(ctx)) return Result.success()
        val walk = WalkSpeedRepository(ctx)
        if (TripLogRepository(ctx) { walk.guestId() }.consent.first() != TripConsent.YES) return Result.success()
        if (!UsageRepository(ctx).current().adapt) return Result.success()
        val mode = ModeRepository(ctx).mode.first() ?: return Result.success()
        val json = runCatching {
            ServerApi.get("/api/suggestions/today", mapOf("guestId" to walk.guestId(), "mode" to mode.key))
        }.getOrNull() ?: return Result.success()
        val reminder = reminderFor(json, LocalTime.now(ZoneId.of("Asia/Seoul"))) ?: return Result.success()
        // Once per habit a day.
        if (ctx.settingsDataStore.data.first()[sentKey] == reminder.key) return Result.success()
        notify(ctx, reminder)
        ctx.settingsDataStore.edit { it[sentKey] = reminder.key }
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
