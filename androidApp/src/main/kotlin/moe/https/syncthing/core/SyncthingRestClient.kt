package moe.https.syncthing.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime

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
            myId = json.optString("myID").takeIf(String::isNotBlank),
            discoveryEnabled = json.optBoolean("discoveryEnabled", false),
            discoveryStatus = parseDiscoveryStatus(json.optJSONObject("discoveryStatus")),
            listenAddresses = parseListenAddresses(json.optJSONObject("connectionServiceStatus")),
        )
    }

    fun discoveryCache(): Map<String, List<String>> {
        val json = request("/rest/system/discovery")
        return buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val deviceId = keys.next()
                val addresses = readStringArray(json.optJSONArray(deviceId))
                if (addresses.isNotEmpty()) {
                    put(deviceId, addresses)
                }
            }
        }
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
                            .takeIf { it.isNotBlank() }
                            ?.let { raw ->
                                runCatching { LocalDateTime.parse(raw) }.getOrNull()
                            }
                            ?.takeIf { localDateTime ->
                                localDateTime.year > 1970
                            }
                        ,
                    ),
                )
            }
        }
    }

    fun addDevice(configuration: NewDeviceConfiguration) {
        val device = request("/rest/config/defaults/device")
        val addressArray = JSONArray()
        configuration.addresses.forEach(addressArray::put)
        device.put("deviceID", configuration.deviceId)
        device.put("name", configuration.name.ifBlank { configuration.deviceId })
        device.put("group", configuration.group)
        device.put("introducer", configuration.introducer)
        device.put("autoAcceptFolders", configuration.autoAcceptFolders)
        device.put(
            "compression",
            when (configuration.compression) {
                NewDeviceConfiguration.Compression.ALL -> "always"
                NewDeviceConfiguration.Compression.METADATA -> "metadata"
                NewDeviceConfiguration.Compression.OFF -> "never"
            },
        )
        device.put("numConnections", configuration.numConnections)
        device.put("maxSendKbps", configuration.maxSendKiBPerSecond)
        device.put("maxRecvKbps", configuration.maxReceiveKiBPerSecond)
        device.put("untrusted", configuration.untrusted)
        if (configuration.addresses.isNotEmpty()) {
            device.put("addresses", addressArray)
        }
        requestBody(
            path = "/rest/config/devices",
            method = "POST",
            body = device.toString(),
        )
    }

    fun shutdown() {
        request("/rest/system/shutdown", method = "POST")
    }

    private fun request(path: String, method: String = "GET"): JSONObject {
        val body = requestBody(path, method)
        return if (body.isBlank()) JSONObject() else JSONObject(body)
    }

    private fun requestArray(path: String): JSONArray = JSONArray(requestBody(path))

    private fun requestBody(
        path: String,
        method: String = "GET",
        body: String? = null,
    ): String {
        val connection = URL("$BASE_URL$path").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("X-API-Key", apiKey)
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                val bodyBytes = body.toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bodyBytes.size)
                connection.outputStream.use { output ->
                    output.write(bodyBytes)
                }
            } else if (method == "POST") {
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
        val myId: String?,
        val discoveryEnabled: Boolean,
        val discoveryStatus: List<RestDiscoveryStatus>,
        val listenAddresses: List<RestListenAddress>,
    )

    data class RestDiscoveryStatus(
        val method: String,
        val error: String?,
    )

    data class RestListenAddress(
        val address: String,
        val error: String?,
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
        val lastConnectionAt: LocalDateTime?,
    )

    companion object {
        private const val BASE_URL = "http://127.0.0.1:8384"
        private const val TIMEOUT_MILLIS = 1_500
    }

    private fun parseDiscoveryStatus(json: JSONObject?): List<RestDiscoveryStatus> {
        if (json == null) return emptyList()
        return buildList {
            val keys = json.keys()
            while (keys.hasNext()) {
                val method = keys.next()
                val status = json.optJSONObject(method)
                val errorValue = status?.opt("error")
                val error = if (errorValue == null || errorValue == JSONObject.NULL) {
                    null
                } else {
                    errorValue.toString().takeIf(String::isNotBlank)
                }
                add(RestDiscoveryStatus(method = method, error = error))
            }
        }
    }

    private fun parseListenAddresses(json: JSONObject?): List<RestListenAddress> {
        if (json == null) return emptyList()
        return buildList {
            val keys = json.keys()
            while (keys.hasNext()) {
                val serviceAddress = keys.next()
                val service = json.optJSONObject(serviceAddress)
                val addresses = if (service == null) {
                    emptyList()
                } else {
                    readStringArray(service.optJSONArray("lanAddresses")) +
                        readStringArray(service.optJSONArray("wanAddresses"))
                }
                val errorValue = service?.opt("error")
                val error = if (errorValue == null || errorValue == JSONObject.NULL) {
                    null
                } else {
                    errorValue.toString().takeIf(String::isNotBlank)
                }
                (addresses.ifEmpty { listOf(serviceAddress) })
                    .forEach { add(RestListenAddress(it, error)) }
            }
        }.distinct()
    }

    private fun readStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            repeat(array.length()) { index ->
                array.optString(index)
                    .takeIf(String::isNotBlank)
                    ?.let(::add)
            }
        }
    }
}

internal class SyncthingRestException(
    val responseCode: Int,
) : IOException("Syncthing REST 返回 HTTP $responseCode")

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null
