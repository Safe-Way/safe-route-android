package com.deungbul.anshimgil

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.deungbul.anshimgil.ui.mode.style
import com.deungbul.anshimgil.data.user.UserMode

/**
 * The mode as a small icon next to the clock while the app is open (team, 11/6): a quiet notification that
 * is shown when the app comes to the front and taken away when it goes to the background.
 */
object ModeStatus {
    // "mode" was a quiet (low) channel, whose icons Android keeps out of the status bar; a channel's level
    // cannot be raised, so this is a new one.
    private const val CHANNEL = "mode_icon"
    private const val ID = 1001

    fun icon(mode: UserMode): Int = when (mode) {
        UserMode.CHILD -> R.drawable.ic_mode_child
        UserMode.SENIOR -> R.drawable.ic_mode_senior
        UserMode.DISABLED -> R.drawable.ic_mode_wheelchair
        UserMode.GENERAL -> R.drawable.ic_mode_adult
    }

    fun canShow(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, mode: UserMode) {
        if (!canShow(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.deleteNotificationChannel("mode")
            // Default level so the icon shows next to the clock, but with no sound or vibration.
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "지금 모드", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "앱을 쓰는 동안 시계 옆에 지금 모드를 보여 줘요"
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(icon(mode))
            .setContentTitle("${mode.style().label} 모드")
            .setContentText("세잎지도가 ${mode.style().label} 모드로 길을 찾아요")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID, notification)
        } catch (e: SecurityException) {
            // Permission taken away meanwhile.
        }
    }

    fun hide(context: Context) = NotificationManagerCompat.from(context).cancel(ID)
}
