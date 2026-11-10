package com.deungbul.anshimgil

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.deungbul.anshimgil.data.TripBooking
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId

/*
 * 길 안내 예약 (11/10): the booked guidances, kept on this phone only (SharedPreferences "trip_bookings"; nothing goes
 * to the server), an alarm for each, and what happens at that time: with 세잎지도 on screen the guidance starts by
 * itself; otherwise a notification "지금 문화전당 길 안내를 시작해요" opens it and starts it with one tap (Android does
 * not let an app jump in front of what the walker is doing).
 */

/** Intent extra: start the guidance as soon as the route is found. */
const val EXTRA_GO_START = "go_start"

/** Intent extra: the booking that fired, so it is taken off the list. */
const val EXTRA_BOOKING_ID = "booking_id"

private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

object TripBookings {
    private const val PREFS = "trip_bookings"
    private const val KEY = "list"

    fun all(context: Context): List<TripBooking> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TripBooking(
                    o.getLong("id"), LocalDateTime.parse(o.getString("at")), o.getString("name"),
                    o.getDouble("lat"), o.getDouble("lon"), o.optBoolean("transit"),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** The bookings still to come. */
    fun upcoming(context: Context): List<TripBooking> {
        val now = LocalDateTime.now(SEOUL)
        return all(context).filter { it.at.isAfter(now.minusMinutes(1)) }.sortedBy { it.at }
    }

    private fun save(context: Context, list: List<TripBooking>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("at", it.at.toString()).put("name", it.name)
                .put("lat", it.lat).put("lon", it.lon).put("transit", it.transit))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).commit()
    }

    fun add(context: Context, booking: TripBooking) {
        save(context, upcoming(context) + booking)
        arm(context, booking)
    }

    fun remove(context: Context, id: Long) {
        all(context).firstOrNull { it.id == id }?.let { disarm(context, it) }
        save(context, all(context).filter { it.id != id })
    }

    /** Cancels every booking still to come; returns what was cancelled. */
    fun cancelAll(context: Context): List<TripBooking> {
        val list = upcoming(context)
        list.forEach { disarm(context, it) }
        save(context, emptyList())
        return list
    }

    /** Sets the alarms again (after the phone restarts, alarms are gone). */
    fun rearm(context: Context) = upcoming(context).forEach { arm(context, it) }

    private fun pending(context: Context, b: TripBooking, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context, b.id.toInt(),
            Intent(context, TripAlarmReceiver::class.java).putExtra(EXTRA_BOOKING_ID, b.id),
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun arm(context: Context, b: TripBooking) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context, b, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val whenMs = b.at.atZone(SEOUL).toInstant().toEpochMilli()
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
            // Without the exact-alarm grant Android may move it by a few minutes.
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
        }
    }

    private fun disarm(context: Context, b: TripBooking) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        pending(context, b, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
    }
}

/** True while 세잎지도 is on screen (set by MainActivity), so a booking can start the guidance right away. */
@Volatile
var appOnScreen = false

class TripAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            TripBookings.rearm(context)
            return
        }
        val id = intent.getLongExtra(EXTRA_BOOKING_ID, -1)
        val booking = TripBookings.all(context).firstOrNull { it.id == id } ?: return
        TripBookings.remove(context, id)
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_GO_NAME, booking.name)
            .putExtra(EXTRA_GO_LAT, booking.lat)
            .putExtra(EXTRA_GO_LON, booking.lon)
            .putExtra(EXTRA_GO_TRANSIT, booking.transit)
            .putExtra(EXTRA_GO_START, true)
        // On screen: straight into the guidance.
        if (appOnScreen) {
            runCatching { context.startActivity(open) }.onSuccess { return }
        }
        notify(context, booking, open)
    }

    private fun notify(context: Context, b: TripBooking, open: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "예약한 길 안내", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "AI 비서에게 예약한 시간이 되면 길 안내를 시작해요"
                },
            )
        }
        val pi = PendingIntent.getActivity(
            context, b.id.toInt(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = "예약하신 시간이에요. 지금 ${b.name} 길 안내를 시작해요."
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_mode_adult)
            .setContentTitle("세잎지도 길 안내")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n눌러서 안내 시작"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(BOOK_ID_BASE + (b.id % 1000).toInt(), n)
        } catch (e: SecurityException) {
            // Notifications are off.
        }
    }

    companion object {
        private const val CHANNEL = "trip-booking"
        private const val BOOK_ID_BASE = 3000
    }
}
