package com.deungbul.anshimgil.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

/*
 * 불편 신고 (FR-49): a walker reports a problem on the street (steep slope, works, curb, broken
 * pavement, something in the way) with up to 3 photos and their position. Server `POST /api/reports`.
 */

/** Report kinds, in the server's codes and words. */
enum class ReportType(val code: String, val label: String) {
    STEEP("steep", "급경사"),
    CONSTRUCTION("construction", "공사"),
    CURB("curb", "턱"),
    BROKEN("broken", "보도 파손"),
    OBSTACLE("obstacle", "막는 물건"),
    OTHER("other", "기타"),
}

const val REPORT_MAX_PHOTOS = 3
const val REPORT_MAX_MEMO = 300

object ReportRepository {
    /** Sends a report; returns its id. Throws [ApiException]. */
    suspend fun send(
        type: ReportType,
        at: LatLon,
        accuracyM: Int?,
        mode: UserMode,
        memo: String,
        photos: List<ByteArray>,
    ): Long {
        val json = ServerApi.postMultipart(
            "/api/reports",
            reportFields(type, at, accuracyM, mode, memo),
            photos.take(REPORT_MAX_PHOTOS).mapIndexed { i, bytes -> ServerApi.Part("photos", "photo${i + 1}.jpg", "image/jpeg", bytes) },
        )
        return json.optLong("id")
    }
}

/** The form fields (no photos). */
internal fun reportFields(type: ReportType, at: LatLon, accuracyM: Int?, mode: UserMode, memo: String): Map<String, Any?> = mapOf(
    "type" to type.code,
    "lat" to "%.6f".format(java.util.Locale.US, at.lat),
    "lon" to "%.6f".format(java.util.Locale.US, at.lon),
    "accuracyM" to accuracyM,
    "mode" to mode.key,
    "memo" to memo.trim().take(REPORT_MAX_MEMO).ifBlank { null },
)

/**
 * A picked or taken photo made small enough to send: the long side at most [maxSide] px as JPEG,
 * well under the server's 5 MB a photo. Null when the picture cannot be read.
 */
fun photoForUpload(context: Context, uri: Uri, maxSide: Int = 1600): ByteArray? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
    val sized = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
    ByteArrayOutputStream().use { out ->
        sized.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray()
    }
}.getOrNull()
