package com.deungbul.anshimgil.data.server

import com.deungbul.anshimgil.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/*
 * 앱 업데이트 안내: the server's `GET /api/app/version?versionCode=` says whether this build must be updated
 * ("required"), may be ("optional") or is fine ("none"; also whenever the server has no download address).
 * Asked once when the app starts; any failure or odd answer just means no window, never a crash.
 */

/** A newer build to tell about. [required] = the window cannot be closed. */
data class AppUpdate(
    val required: Boolean,
    val message: String,
    val downloadUrl: String,
    val versionName: String?,
)

private const val DEFAULT_MESSAGE = "새 버전이 나왔어요."

/** The server's answer read as a window to show, or null for none (also for anything unexpected). */
internal fun parseAppUpdate(json: JSONObject): AppUpdate? {
    val required = when (json.optString("update")) {
        "required" -> true
        "optional" -> false
        else -> return null
    }
    val url = json.optString("downloadUrl").trim()
    if (!url.startsWith("https://") && !url.startsWith("http://")) return null
    val message = json.optString("message").trim().ifEmpty { DEFAULT_MESSAGE }
    val name = json.optString("latestVersionName").trim().ifEmpty { null }
    return AppUpdate(required, message, url, name)
}

/** One check per app run, kept here so turning the phone or coming back does not ask again or bring back a closed window. */
object AppUpdateCheck {
    private val _update = MutableStateFlow<AppUpdate?>(null)
    val update: StateFlow<AppUpdate?> = _update.asStateFlow()
    private var checked = false

    suspend fun checkOnce() {
        if (checked || !ServerApi.isConfigured) return
        checked = true
        _update.value = runCatching {
            parseAppUpdate(
                ServerApi.get(
                    "/api/app/version",
                    mapOf("versionCode" to BuildConfig.VERSION_CODE),
                    connectTimeoutMs = 3_000,
                    readTimeoutMs = 3_000,
                ),
            )
        }.getOrNull()
    }

    /** [나중에] on an optional update. */
    fun later() {
        if (_update.value?.required == false) _update.value = null
    }
}
