package com.deungbul.anshimgil

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.deungbul.anshimgil.data.Guidance
import com.deungbul.anshimgil.data.LatLon
import com.deungbul.anshimgil.location.PositionFilter
import com.deungbul.anshimgil.location.hasLocationPermission
import com.deungbul.anshimgil.ui.guide.GuideAnnouncer
import java.util.Locale

/*
 * 화면이 꺼져도 길안내 (11/13, 팀 요청 3번): while walking guidance is open, this foreground service keeps the
 * phone's position coming with the screen off, shows the next turn in a notification, and speaks it while the app
 * is not on screen (the guidance screen speaks while it is, also in the small window). Position stays on the phone.
 */

/** The guidance the service follows; set by the guidance screen (the same route, also after finding a new one). */
object GuideSession {
    @Volatile var guidance: Guidance? = null
    @Volatile var destinationName: String = ""
    @Volatile var voiceOn: Boolean = true
}

class GuideService : Service() {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var manager: LocationManager? = null
    private val filter = PositionFilter()
    private var announcer: GuideAnnouncer? = null
    private var following: Guidance? = null
    private var shown = "길 안내 중"

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val accuracy = if (location.hasAccuracy()) location.accuracy else 0f
            filter.accept(LatLon(location.latitude, location.longitude), accuracy, android.os.SystemClock.elapsedRealtime())
                ?.let { onFix(it) }
        }

        @Deprecated("Deprecated in Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "길 안내", NotificationManager.IMPORTANCE_LOW))
        }
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.KOREAN
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val started = runCatching {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification(shown),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        }.isSuccess
        if (!started || !hasLocationPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (manager == null) {
            manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            listOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { runCatching { manager!!.isProviderEnabled(it) }.getOrDefault(false) }
                .forEach { runCatching { manager!!.requestLocationUpdates(it, 1000L, 0f, listener) } }
        }
        return START_NOT_STICKY
    }

    private fun onFix(position: LatLon) {
        val g = GuideSession.guidance ?: return
        if (g !== following) {
            following = g
            announcer = GuideAnnouncer(g, GuideSession.destinationName)
        }
        val a = announcer!!.onFix(position)
        if (a.shown != shown) {
            shown = a.shown
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(shown))
        }
        // On screen (also the small window) the guidance screen speaks; here only while the app is not shown.
        if (a.speak != null && !appOnScreen && GuideSession.voiceOn && ttsReady) {
            tts?.speak(a.speak, TextToSpeech.QUEUE_FLUSH, null, "guide-bg")
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("세잎지도 길 안내 중 · ${GuideSession.destinationName}")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .build()
    }

    override fun onDestroy() {
        runCatching { manager?.removeUpdates(listener) }
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "guide"
        private const val NOTIFICATION_ID = 41

        /** From the guidance screen while it is in front (Android lets a location service start only then). */
        fun start(context: Context) {
            if (!hasLocationPermission(context)) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, GuideService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, GuideService::class.java)) }
        }
    }
}
