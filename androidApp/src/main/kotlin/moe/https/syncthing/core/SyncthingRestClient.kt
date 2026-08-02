package moe.https.syncthing.core

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class SyncthingRestClient(
    private val apiKey: String,
) {
    fun ping(): Boolean = runCatching {
        pingChecked()
    }.getOrDefault(false)

    fun pingChecked(): Boolean {
        val response = request("/rest/system/ping").optString("ping")
        if (response != "pong") {
            throw IOException("Syncthing REST ping 响应无效：${response.ifBlank { "响应中缺少 ping 字段" }}")
        }
        return true
    }

    fun status(): RestStatus {
        val json = request("/rest/system/status")
        return RestStatus(
            uptimeSeconds = json.optLongOrNull("uptime"),
            allocatedBytes = json.optLongOrNull("alloc"),
            systemBytes = json.optLongOrNull("sys"),
            goroutines = json.optIntOrNull("goroutines"),
        )
    }

    fun shutdown() {
        request("/rest/system/shutdown", method = "POST")
    }

    private fun request(path: String, method: String = "GET"): JSONObject {
        val connection = URL("$BASE_URL$path").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("X-API-Key", apiKey)
            connection.setRequestProperty("Accept", "application/json")
            if (method == "POST") {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(0)
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw SyncthingRestException(responseCode)
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            if (body.isBlank()) JSONObject() else JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    data class RestStatus(
        val uptimeSeconds: Long?,
        val allocatedBytes: Long?,
        val systemBytes: Long?,
        val goroutines: Int?,
    )

    companion object {
        private const val BASE_URL = "http://127.0.0.1:8384"
        private const val TIMEOUT_MILLIS = 1_500
    }
}

internal class SyncthingRestException(
    val responseCode: Int,
) : IOException("Syncthing REST 返回 HTTP $responseCode")

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null
