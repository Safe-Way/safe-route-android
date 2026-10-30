package com.deungbul.anshimgil.data

import com.deungbul.anshimgil.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A failed server call. [message] is meant to be shown to the user as is. */
class ApiException(val code: String, override val message: String) : Exception(message)

/**
 * Minimal client for the safe-route server. Errors come back as `{"code", "message"}`.
 * The address comes from `SERVER_BASE_URL` in local.properties; blank means "use example data".
 */
object ServerApi {
    val baseUrl: String = BuildConfig.SERVER_BASE_URL.trimEnd('/')
    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    suspend fun get(path: String, query: Map<String, Any?> = emptyMap()): JSONObject =
        request("GET", path + queryString(query), body = null)

    suspend fun post(path: String, body: JSONObject): JSONObject = request("POST", path, body)

    private suspend fun request(method: String, path: String, body: JSONObject?): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = try {
                (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 5_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/json")
                    if (body != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        outputStream.use { it.write(body.toString().toByteArray()) }
                    }
                }
            } catch (e: IOException) {
                throw ApiException("NETWORK", "서버에 연결하지 못했어요. 인터넷 연결을 확인해 주세요.")
            }
            try {
                val ok = connection.responseCode in 200..299
                val text = (if (ok) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = if (text.isBlank()) JSONObject() else JSONObject(text)
                if (!ok) {
                    throw ApiException(
                        json.optString("code", "HTTP_${connection.responseCode}"),
                        json.optString("message", "잠시 후 다시 시도해 주세요."),
                    )
                }
                json
            } catch (e: IOException) {
                throw ApiException("NETWORK", "서버에 연결하지 못했어요. 인터넷 연결을 확인해 주세요.")
            } finally {
                connection.disconnect()
            }
        }

    private fun queryString(query: Map<String, Any?>): String {
        val parts = query.filterValues { it != null }
            .map { (key, value) -> "$key=" + URLEncoder.encode(value.toString(), "UTF-8") }
        return if (parts.isEmpty()) "" else "?" + parts.joinToString("&")
    }
}
