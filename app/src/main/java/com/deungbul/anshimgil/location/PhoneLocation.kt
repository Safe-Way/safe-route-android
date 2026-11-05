package com.deungbul.anshimgil.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.deungbul.anshimgil.data.LatLon
import com.deungbul.anshimgil.data.Origin
import com.deungbul.anshimgil.data.OriginIssue
import com.deungbul.anshimgil.data.chooseOrigin
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/*
 * The phone's position. It stays on the phone: it is sent only as a route's origin and never logged (NFR-07).
 */

val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

fun hasLocationPermission(context: Context) = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

/** Whether the phone's location setting is on. */
fun isLocationOn(context: Context): Boolean =
    LocationManagerCompat.isLocationEnabled(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)

/** Where a route starts from the phone, with the reason when the demo point is used instead. */
suspend fun phoneOrigin(context: Context, timeoutMs: Long = 8_000): Origin {
    val why = when {
        !hasLocationPermission(context) -> OriginIssue.NO_PERMISSION
        !isLocationOn(context) -> OriginIssue.LOCATION_OFF
        else -> OriginIssue.NO_FIX
    }
    return chooseOrigin(currentLocation(context, timeoutMs), why)
}

private const val RECENT_MS = 2 * 60_000L
private const val USABLE_MS = 10 * 60_000L

/**
 * One position: a new fix (waiting 2 s if there is one from the last 2 minutes, else [timeoutMs]),
 * otherwise the newest from the last 10 minutes. Null without permission or location turned off.
 */
@SuppressLint("MissingPermission")
suspend fun currentLocation(context: Context, timeoutMs: Long = 8_000): LatLon? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = enabledProviders(manager)
    fun lastKnown(maxAgeMs: Long): Location? = providers
        .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .filter { System.currentTimeMillis() - it.time < maxAgeMs }
        .maxByOrNull { it.time }

    if (providers.isEmpty()) return null
    // A recent fix may be from before the phone moved, so wait briefly for a new one first.
    val recent = lastKnown(RECENT_MS)
    val fresh = withTimeoutOrNull(if (recent != null) 2_000 else timeoutMs) {
        suspendCancellableCoroutine { cont ->
            lateinit var self: LocationListener
            self = listener { location ->
                // Stop listening once the first fix has arrived.
                manager.removeUpdates(self)
                if (cont.isActive) cont.resume(location.toLatLon())
            }
            providers.forEach { manager.requestLocationUpdates(it, 0L, 0f, self, Looper.getMainLooper()) }
            cont.invokeOnCancellation { manager.removeUpdates(self) }
        }
    }
    return fresh ?: (recent ?: lastKnown(USABLE_MS))?.toLatLon()
}

/** The newest position the phone already has, without waiting; null if none from the last [maxAgeMs]. */
@SuppressLint("MissingPermission")
fun lastKnownLocation(context: Context, maxAgeMs: Long = USABLE_MS): LatLon? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return enabledProviders(manager)
        .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .filter { System.currentTimeMillis() - it.time < maxAgeMs }
        .maxByOrNull { it.time }
        ?.toLatLon()
}

/**
 * Position updates about every second while the calling screen is shown, steadied by [PositionFilter]
 * so rough fixes and standing-still noise do not make the marker jump around.
 */
@SuppressLint("MissingPermission")
@Composable
fun LocationUpdates(context: Context, onFix: (LatLon) -> Unit) {
    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val filter = PositionFilter()
        val listener = listener { location ->
            filter.accept(location.toLatLon(), if (location.hasAccuracy()) location.accuracy else 0f, android.os.SystemClock.elapsedRealtime())
                ?.let(onFix)
        }
        enabledProviders(manager).forEach { manager.requestLocationUpdates(it, 1000L, 0f, listener) }
        onDispose { manager.removeUpdates(listener) }
    }
}

/**
 * "fused" (Android 12+) combines GPS, Wi-Fi and cell towers, so it answers indoors where GPS alone
 * may never get a fix. On phones without it, isProviderEnabled is false and it is skipped.
 */
private fun enabledProviders(manager: LocationManager) =
    listOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

/**
 * Before Android 11 the other three methods have no default body, so a lambda listener
 * would crash on older phones when a provider is switched on or off.
 */
private fun listener(onFix: (Location) -> Unit) = object : LocationListener {
    override fun onLocationChanged(location: Location) = onFix(location)
    @Deprecated("Deprecated in Android")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}

private fun Location.toLatLon() = LatLon(latitude, longitude)
