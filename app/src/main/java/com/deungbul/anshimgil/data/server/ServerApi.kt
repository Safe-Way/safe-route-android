package com.deungbul.anshimgil.data.server

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

    suspend fun get(
        path: String,
        query: Map<String, Any?> = emptyMap(),
        connectTimeoutMs: Int = 5_000,
        readTimeoutMs: Int = 10_000,
    ): JSONObject = request("GET", path + queryString(query), body = null, connectTimeoutMs, readTimeoutMs)

    suspend fun post(path: String, body: JSONObject): JSONObject = request("POST", path, body)

    suspend fun patch(path: String, body: JSONObject): JSONObject = request("PATCH", path, body)

    suspend fun delete(path: String, query: Map<String, Any?> = emptyMap()): JSONObject =
        request("DELETE", path + queryString(query), body = null)

    /** A file sent in a multipart form, e.g. a photo. */
    class Part(val name: String, val fileName: String, val contentType: String, val bytes: ByteArray)

    /** POST as multipart/form-data: text [fields] plus [files] (used for 불편 신고 with photos). */
    suspend fun postMultipart(path: String, fields: Map<String, Any?>, files: List<Part>): JSONObject =
        withContext(Dispatchers.IO) {
            val boundary = "----seipjido" + System.nanoTime()
            val body = java.io.ByteArrayOutputStream().apply {
                fun line(text: String = "") = write((text + "\r\n").toByteArray())
                fields.forEach { (name, value) ->
                    if (value == null) return@forEach
                    line("--$boundary")
                    line("Content-Disposition: form-data; name=\"$name\"")
                    line()
                    line(value.toString())
                }
                files.forEach { f ->
                    line("--$boundary")
                    line("Content-Disposition: form-data; name=\"${f.name}\"; filename=\"${f.fileName}\"")
                    line("Content-Type: ${f.contentType}")
                    line()
                    write(f.bytes)
                    line()
                }
                line("--$boundary--")
            }.toByteArray()
            val connection = try {
                (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5_000
                    readTimeout = 30_000
                    doOutput = true
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    setFixedLengthStreamingMode(body.size)
                    outputStream.use { it.write(body) }
                }
            } catch (e: IOException) {
                throw ApiException("NETWORK", NETWORK_MESSAGE)
            }
            readResponse(connection)
        }

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject?,
        connectTimeoutMs: Int = 5_000,
        readTimeoutMs: Int = 10_000,
    ): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = try {
                (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    setRequestProperty("Accept", "application/json")
                    if (body != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        outputStream.use { it.write(body.toString().toByteArray()) }
                    }
                }
            } catch (e: IOException) {
                throw ApiException("NETWORK", NETWORK_MESSAGE)
            }
            readResponse(connection)
        }

    private fun readResponse(connection: HttpURLConnection): JSONObject =
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
                throw ApiException("NETWORK", NETWORK_MESSAGE)
            } finally {
                connection.disconnect()
            }

    private fun queryString(query: Map<String, Any?>): String {
        val parts = query.filterValues { it != null }
            .map { (key, value) -> "$key=" + URLEncoder.encode(value.toString(), "UTF-8") }
        return if (parts.isEmpty()) "" else "?" + parts.joinToString("&")
    }
}

/** In words anyone knows (사용성 점검표 19번): no "서버", and what to check. */
const val NETWORK_MESSAGE = "인터넷이 안 돼요. 와이파이나 데이터가 켜져 있는지 보고 다시 해 주세요."
