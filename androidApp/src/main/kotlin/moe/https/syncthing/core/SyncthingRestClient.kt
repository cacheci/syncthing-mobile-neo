package moe.https.syncthing.core

import org.json.JSONArray
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

    fun configuredDevices(): List<RestDevice> {
        val array = requestArray("/rest/config/devices")
        return buildList {
            repeat(array.length()) { index ->
                val json = array.optJSONObject(index) ?: return@repeat
                val id = json.optString("deviceID").takeIf(String::isNotBlank) ?: return@repeat
                val addresses = json.optJSONArray("addresses")?.let { addressArray ->
                    buildList {
                        repeat(addressArray.length()) { addressIndex ->
                            addressArray.optString(addressIndex)
                                .takeIf(String::isNotBlank)
                                ?.let(::add)
                        }
                    }
                }.orEmpty()
                add(
                    RestDevice(
                        id = id,
                        name = json.optString("name").takeIf(String::isNotBlank),
                        addresses = addresses,
                        paused = json.optBoolean("paused", false),
                    ),
                )
            }
        }
    }

    fun connections(): Map<String, RestConnection> {
        val json = request("/rest/system/connections")
        val connections = json.optJSONObject("connections") ?: return emptyMap()
        return buildMap {
            val keys = connections.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val connection = connections.optJSONObject(id) ?: continue
                put(
                    id,
                    RestConnection(
                        connected = connection.optBoolean("connected", false),
                        address = connection.optString("address").takeIf(String::isNotBlank),
                        clientVersion = connection.optString("clientVersion")
                            .takeIf(String::isNotBlank),
                        lastConnectionAt = connection.optString("at")
                            .takeIf(String::isNotBlank),
                    ),
                )
            }
        }
    }

    fun shutdown() {
        request("/rest/system/shutdown", method = "POST")
    }

    private fun request(path: String, method: String = "GET"): JSONObject {
        val body = requestBody(path, method)
        return if (body.isBlank()) JSONObject() else JSONObject(body)
    }

    private fun requestArray(path: String): JSONArray = JSONArray(requestBody(path))

    private fun requestBody(path: String, method: String = "GET"): String {
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
            connection.inputStream.bufferedReader().use { it.readText() }
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

    data class RestDevice(
        val id: String,
        val name: String?,
        val addresses: List<String>,
        val paused: Boolean,
    )

    data class RestConnection(
        val connected: Boolean,
        val address: String?,
        val clientVersion: String?,
        val lastConnectionAt: String?,
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
