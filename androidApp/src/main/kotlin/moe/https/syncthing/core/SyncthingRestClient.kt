package moe.https.syncthing.core

import at.favre.lib.crypto.bcrypt.BCrypt
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime

internal class SyncthingRestClient(
    private val apiKey: String,
    private val baseUrl: () -> String = { DEFAULT_BASE_URL },
    private val onHttpError: (SyncthingRestException) -> Unit = {},
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
                        group = json.optString("group"),
                        introducer = json.optBoolean("introducer", false),
                        autoAcceptFolders = json.optBoolean("autoAcceptFolders", false),
                        compression = when (json.optString("compression")) {
                            "always" -> NewDeviceConfiguration.Compression.ALL
                            "never" -> NewDeviceConfiguration.Compression.OFF
                            else -> NewDeviceConfiguration.Compression.METADATA
                        },
                        numConnections = json.optInt("numConnections", 0),
                        maxSendKiBPerSecond = json.optInt("maxSendKbps", 0),
                        maxReceiveKiBPerSecond = json.optInt("maxRecvKbps", 0),
                        untrusted = json.optBoolean("untrusted", false),
                    ),
                )
            }
        }
    }

    fun pendingDevices(): List<SyncthingPendingDevice> {
        val json = request("/rest/cluster/pending/devices")
        return buildList {
            val deviceIds = json.keys()
            while (deviceIds.hasNext()) {
                val deviceId = deviceIds.next()
                val pendingDevice = json.optJSONObject(deviceId) ?: continue
                add(
                    SyncthingPendingDevice(
                        id = deviceId,
                        name = pendingDevice.optString("name").takeIf(String::isNotBlank),
                        address = pendingDevice.optString("address").takeIf(String::isNotBlank),
                        detectedAt = pendingDevice.optString("time").takeIf(String::isNotBlank),
                    ),
                )
            }
        }.sortedByDescending { it.detectedAt.orEmpty() }
    }

    fun pendingFolders(): List<SyncthingPendingFolder> {
        val json = request("/rest/cluster/pending/folders")
        val deviceNames = configuredDevices().associate { device ->
            device.id to (device.name ?: device.id)
        }
        return buildList {
            val folderIds = json.keys()
            while (folderIds.hasNext()) {
                val folderId = folderIds.next()
                val offeredBy = json.optJSONObject(folderId)
                    ?.optJSONObject("offeredBy")
                    ?: continue
                val sourceIds = offeredBy.keys()
                while (sourceIds.hasNext()) {
                    val sourceId = sourceIds.next()
                    val offer = offeredBy.optJSONObject(sourceId) ?: continue
                    add(
                        SyncthingPendingFolder(
                            id = folderId,
                            name = offer.optString("label").ifBlank { folderId },
                            source = sourceId,
                            sourceName = deviceNames[sourceId] ?: sourceId,
                            detectedAt = offer.optString("time").takeIf(String::isNotBlank),
                        ),
                    )
                }
            }
        }.sortedByDescending { it.detectedAt.orEmpty() }
    }

    fun configuredFolders(): List<RestFolder> {
        val array = requestArray("/rest/config/folders")
        return buildList {
            repeat(array.length()) { index ->
                val json = array.optJSONObject(index) ?: return@repeat
                val id = json.optString("id").takeIf(String::isNotBlank) ?: return@repeat
                add(
                    RestFolder(
                        id = id,
                        label = json.optString("label").takeIf(String::isNotBlank),
                        group = json.optString("group"),
                        path = json.optString("path"),
                        type = json.optString("type", "sendreceive"),
                        paused = json.optBoolean("paused", false),
                        fsWatcherEnabled = json.optBoolean("fsWatcherEnabled", true),
                        rescanIntervalSeconds = json.optInt("rescanIntervalS", 3600),
                        versioning = parseVersioning(json.optJSONObject("versioning")),
                        devices = parseFolderDevices(json.optJSONArray("devices")),
                    ),
                )
            }
        }
    }

    fun addFolder(configuration: NewFolderConfiguration) {
        val folder = request("/rest/config/defaults/folder")
        applyFolderConfiguration(folder, configuration)
        folder.put("path", configuration.path)
        requestBody(
            path = "/rest/config/folders",
            method = "POST",
            body = folder.toString(),
        )
    }

    fun updateFolder(configuration: NewFolderConfiguration) {
        val encodedFolderId = encodePathSegment(configuration.folderId)
        val folder = request("/rest/config/folders/$encodedFolderId")
        applyFolderConfiguration(folder, configuration)
        folder.put("path", configuration.path)
        requestBody(
            path = "/rest/config/folders/$encodedFolderId",
            method = "PUT",
            body = folder.toString(),
        )
    }

    fun folderStatus(folderId: String): RestFolderStatus {
        val encodedFolderId = URLEncoder.encode(folderId, Charsets.UTF_8.name())
        val json = request("/rest/db/status?folder=$encodedFolderId")
        return RestFolderStatus(
            state = json.optString("state"),
            localFiles = json.optLong("localFiles", 0L),
            localBytes = json.optLong("localBytes", 0L),
            needFiles = json.optLong("needFiles", 0L),
            needBytes = json.optLong("needBytes", 0L),
            pullErrors = json.optLong("pullErrors", 0L),
        )
    }

    fun folderIgnores(folderId: String): RestFolderIgnores {
        val encodedFolderId = URLEncoder.encode(folderId, Charsets.UTF_8.name())
        val response = request("/rest/db/ignores?folder=$encodedFolderId")
        val ignores = response.optJSONArray("ignore")
        val errorValue = response.opt("error")
        return RestFolderIgnores(
            patterns = if (ignores == null) {
                emptyList()
            } else {
                buildList {
                    repeat(ignores.length()) { index ->
                        add(ignores.optString(index))
                    }
                }
            },
            error = if (errorValue == null || errorValue == JSONObject.NULL) {
                null
            } else {
                errorValue.toString().takeIf(String::isNotBlank)
            },
        )
    }

    fun updateFolderIgnores(folderId: String, ignorePatterns: List<String>) {
        val encodedFolderId = URLEncoder.encode(folderId, Charsets.UTF_8.name())
        requestBody(
            path = "/rest/db/ignores?folder=$encodedFolderId",
            method = "POST",
            body = JSONObject()
                .put("ignore", ignorePatterns.toJsonArray())
                .toString(),
        )
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

    fun updateDevice(configuration: NewDeviceConfiguration) {
        val device = request("/rest/config/devices/${configuration.deviceId}")
        val addresses = JSONArray()
        configuration.addresses.forEach(addresses::put)
        device.put("name", configuration.name.ifBlank { configuration.deviceId })
        device.put("group", configuration.group)
        device.put("addresses", addresses)
        device.put("introducer", configuration.introducer)
        device.put("autoAcceptFolders", configuration.autoAcceptFolders)
        device.put("compression", when (configuration.compression) {
            NewDeviceConfiguration.Compression.ALL -> "always"
            NewDeviceConfiguration.Compression.METADATA -> "metadata"
            NewDeviceConfiguration.Compression.OFF -> "never"
        })
        device.put("numConnections", configuration.numConnections)
        device.put("maxSendKbps", configuration.maxSendKiBPerSecond)
        device.put("maxRecvKbps", configuration.maxReceiveKiBPerSecond)
        device.put("untrusted", configuration.untrusted)
        requestBody("/rest/config/devices/${configuration.deviceId}", method = "PUT", body = device.toString())
    }

    fun deleteDevice(deviceId: String) {
        val encodedDeviceId = encodePathSegment(deviceId)
        request("/rest/config/devices/$encodedDeviceId", method = "DELETE")
    }

    fun dismissPendingDevice(deviceId: String) {
        val encodedDeviceId = encodePathSegment(deviceId)
        request("/rest/cluster/pending/devices?device=$encodedDeviceId", method = "DELETE")
    }

    fun ignorePendingDevice(device: SyncthingPendingDevice) {
        val configuration = request("/rest/config")
        val ignoredDevices = configuration.optJSONArray("remoteIgnoredDevices") ?: JSONArray().also {
            configuration.put("remoteIgnoredDevices", it)
        }
        val alreadyIgnored = (0 until ignoredDevices.length()).any { index ->
            ignoredDevices.optJSONObject(index)?.optString("deviceID") == device.id
        }
        if (alreadyIgnored) {
            dismissPendingDevice(device.id)
            return
        }
        ignoredDevices.put(
            JSONObject()
                .put("deviceID", device.id)
                .put("name", device.name.orEmpty())
                .put("address", device.address.orEmpty())
                .put("time", Instant.now().toString()),
        )
        requestBody(
            path = "/rest/config",
            method = "PUT",
            body = configuration.toString(),
        )
    }

    fun dismissPendingFolder(folder: SyncthingPendingFolder) {
        val encodedFolderId = encodePathSegment(folder.id)
        val encodedSourceId = encodePathSegment(folder.source)
        request(
            "/rest/cluster/pending/folders?folder=$encodedFolderId&device=$encodedSourceId",
            method = "DELETE",
        )
    }

    fun ignorePendingFolder(folder: SyncthingPendingFolder) {
        val encodedSourceId = encodePathSegment(folder.source)
        val device = request("/rest/config/devices/$encodedSourceId")
        val ignoredFolders = device.optJSONArray("ignoredFolders") ?: JSONArray().also {
            device.put("ignoredFolders", it)
        }
        val alreadyIgnored = (0 until ignoredFolders.length()).any { index ->
            ignoredFolders.optJSONObject(index)?.optString("id") == folder.id
        }
        if (alreadyIgnored) {
            dismissPendingFolder(folder)
            return
        }
        ignoredFolders.put(
            JSONObject()
                .put("id", folder.id)
                .put("label", folder.name)
                .put("time", Instant.now().toString()),
        )
        requestBody(
            path = "/rest/config/devices/$encodedSourceId",
            method = "PUT",
            body = device.toString(),
        )
    }

    fun setting(
        guiPortConflictBehavior: SettingConfiguration.GuiPortConflictBehavior,
        localDeviceId: String,
    ): SettingConfiguration {
        val options = request("/rest/config/options")
        val gui = request("/rest/config/gui")
        val localDevice = request("/rest/config/devices/$localDeviceId")
        val minHomeDiskFree = options.optJSONObject("minHomeDiskFree")
        val (guiListenAddress, guiPort) = parseGuiAddress(gui.optString("address"))
        val guiPasswordConfigured = gui.optString("password").isNotBlank()

        return SettingConfiguration(
            deviceName = localDevice.optString("name").ifBlank { "Syncthing" },
            minHomeDiskFree = minHomeDiskFree?.optDouble("value", 1.0) ?: 1.0,
            minHomeDiskFreeUnit = SettingConfiguration.DiskSpaceUnit.entries
                .firstOrNull { it.apiValue == minHomeDiskFree?.optString("unit") }
                ?: SettingConfiguration.DiskSpaceUnit.PERCENT,
            usageReportingEnabled = options.optInt("urAccepted", 0) > 0,
            usageReportingVersion = maxOf(
                options.optInt("urAccepted", 0),
                options.optInt("urSeen", 0),
                1,
            ),
            guiListenAddress = guiListenAddress,
            guiPort = guiPort,
            guiPortConflictBehavior = guiPortConflictBehavior,
            guiAuthenticationEnabled = gui.optString("user").isNotBlank() || guiPasswordConfigured,
            guiUser = gui.optString("user"),
            guiPasswordConfigured = guiPasswordConfigured,
            guiTheme = SettingConfiguration.GuiTheme.entries
                .firstOrNull { it.apiValue == gui.optString("theme") }
                ?: SettingConfiguration.GuiTheme.DEFAULT,
            listenAddresses = readStringArray(options.optJSONArray("listenAddresses")),
            maxSendKiBPerSecond = options.optInt("maxSendKbps", 0),
            maxReceiveKiBPerSecond = options.optInt("maxRecvKbps", 0),
            reconnectionIntervalSeconds = options.optInt("reconnectionIntervalS", 60),
            limitBandwidthInLan = options.optBoolean("limitBandwidthInLan", false),
            globalDiscoveryEnabled = options.optBoolean("globalAnnounceEnabled", true),
            globalDiscoveryServers = readStringArray(options.optJSONArray("globalAnnounceServers")),
            localDiscoveryEnabled = options.optBoolean("localAnnounceEnabled", true),
            localDiscoveryPort = options.optInt("localAnnouncePort", 21027),
            localDiscoveryMulticastAddress = options.optString(
                "localAnnounceMCAddr",
                "[ff12::8384]:21027",
            ),
            announceLanAddresses = options.optBoolean("announceLANAddresses", true),
            natEnabled = options.optBoolean("natEnabled", true),
            relaysEnabled = options.optBoolean("relaysEnabled", true),
            alwaysLocalNetworks = readStringArray(options.optJSONArray("alwaysLocalNets")),
            connectionLimitEnough = options.optInt("connectionLimitEnough", 0),
            connectionLimitMax = options.optInt("connectionLimitMax", 0),
        )
    }

    fun updateSetting(
        configuration: SettingConfiguration,
        localDeviceId: String,
        managedGuiPassword: String,
    ): SettingSaveResult {
        val options = request("/rest/config/options")
            .put(
                "minHomeDiskFree",
                JSONObject()
                    .put("value", configuration.minHomeDiskFree)
                    .put("unit", configuration.minHomeDiskFreeUnit.apiValue),
            )
            .put(
                "urAccepted",
                if (configuration.usageReportingEnabled) {
                    maxOf(configuration.usageReportingVersion, 1)
                } else {
                    -1
                },
            )
            .put("listenAddresses", configuration.listenAddresses.toJsonArray())
            .put("maxSendKbps", configuration.maxSendKiBPerSecond)
            .put("maxRecvKbps", configuration.maxReceiveKiBPerSecond)
            .put("reconnectionIntervalS", configuration.reconnectionIntervalSeconds)
            .put("limitBandwidthInLan", configuration.limitBandwidthInLan)
            .put("globalAnnounceEnabled", configuration.globalDiscoveryEnabled)
            .put("globalAnnounceServers", configuration.globalDiscoveryServers.toJsonArray())
            .put("localAnnounceEnabled", configuration.localDiscoveryEnabled)
            .put("localAnnouncePort", configuration.localDiscoveryPort)
            .put("localAnnounceMCAddr", configuration.localDiscoveryMulticastAddress)
            .put("announceLANAddresses", configuration.announceLanAddresses)
            .put("natEnabled", configuration.natEnabled)
            .put("relaysEnabled", configuration.relaysEnabled)
            .put("alwaysLocalNets", configuration.alwaysLocalNetworks.toJsonArray())
            .put("connectionLimitEnough", configuration.connectionLimitEnough)
            .put("connectionLimitMax", configuration.connectionLimitMax)

        val gui = request("/rest/config/gui")
        val localDevice = request("/rest/config/devices/$localDeviceId")
            .put("name", configuration.deviceName)
        val currentGuiAddress = gui.optString("address")
        val currentGuiUser = gui.optString("user")
        val currentGuiTheme = gui.optString("theme")
        val currentGuiPassword = gui.optString("password")
        val currentGuiPasswordMatches = configuration.guiAuthenticationEnabled &&
            verifyPassword(managedGuiPassword, currentGuiPassword)
        val desiredGuiAddress = formatGuiAddress(configuration.guiListenAddress, configuration.guiPort)
        val desiredGuiUser = if (configuration.guiAuthenticationEnabled) configuration.guiUser else ""
        val desiredBasicAuthPrompt = configuration.guiAuthenticationEnabled
        val guiChanged = currentGuiAddress != desiredGuiAddress ||
            currentGuiUser != desiredGuiUser ||
            currentGuiTheme != configuration.guiTheme.apiValue ||
            (configuration.guiAuthenticationEnabled && !currentGuiPasswordMatches) ||
            (!configuration.guiAuthenticationEnabled && currentGuiPassword.isNotBlank()) ||
            gui.optBoolean("sendBasicAuthPrompt", false) != desiredBasicAuthPrompt
        gui
            .put("address", desiredGuiAddress)
            .put("user", desiredGuiUser)
            .put("theme", configuration.guiTheme.apiValue)
            .put("sendBasicAuthPrompt", desiredBasicAuthPrompt)
        if (!configuration.guiAuthenticationEnabled) {
            gui.put("password", "")
        } else if (!currentGuiPasswordMatches) {
            gui.put("password", managedGuiPassword)
        }

        requestBody(
            path = "/rest/config/options",
            method = "PUT",
            body = options.toString(),
        )
        val optionsRestartRequired = request("/rest/config/restart-required")
            .optBoolean("requiresRestart", false)
        requestBody(
            path = "/rest/config/devices/$localDeviceId",
            method = "PUT",
            body = localDevice.toString(),
        )
        requestBody(
            path = "/rest/config/gui",
            method = "PUT",
            body = gui.toString(),
        )
        return SettingSaveResult(
            restartRequired = optionsRestartRequired || guiChanged,
            accessMode = SettingAccessMode.REST,
        )
    }

    fun ensureGuiAuthentication(
        enabled: Boolean,
        username: String,
        password: String,
    ): Boolean {
        val gui = request("/rest/config/gui")
        val currentUsername = gui.optString("user")
        val currentPassword = gui.optString("password")
        val passwordMatches = enabled && verifyPassword(password, currentPassword)
        val currentPromptEnabled = gui.optBoolean("sendBasicAuthPrompt", false)
        val changed = if (enabled) {
            currentUsername != username || !passwordMatches || !currentPromptEnabled
        } else {
            currentUsername.isNotBlank() || currentPassword.isNotBlank() || currentPromptEnabled
        }
        if (!changed) return false

        gui
            .put("user", if (enabled) username else "")
            .put("sendBasicAuthPrompt", enabled)
        if (!enabled) {
            gui.put("password", "")
        } else if (!passwordMatches) {
            gui.put("password", password)
        }
        requestBody(
            path = "/rest/config/gui",
            method = "PUT",
            body = gui.toString(),
        )
        return true
    }

    fun shutdown() {
        request("/rest/system/shutdown", method = "POST")
    }

    private fun request(path: String, method: String = "GET"): JSONObject {
        val body = requestBody(path, method)
        return if (body.isBlank()) JSONObject() else JSONObject(body)
    }

    private fun verifyPassword(password: String, passwordHash: String): Boolean {
        if (passwordHash.isBlank()) return false
        val passwordChars = password.toCharArray()
        return try {
            runCatching {
                BCrypt.verifyer().verify(passwordChars, passwordHash.toCharArray()).verified
            }.getOrDefault(false)
        } finally {
            passwordChars.fill('\u0000')
        }
    }

    private fun requestArray(path: String): JSONArray = JSONArray(requestBody(path))

    private fun requestBody(
        path: String,
        method: String = "GET",
        body: String? = null,
    ): String {
        val connection = URL("${baseUrl()}$path").openConnection() as HttpURLConnection
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
                val responseBody = runCatching {
                    connection.errorStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                }
                    .getOrNull()
                    ?.trim()
                    ?.replace(apiKey, REDACTED_VALUE)
                    ?.take(MAX_ERROR_BODY_LENGTH)
                    ?.takeIf(String::isNotBlank)
                val error = SyncthingRestException(
                    method = method,
                    path = path,
                    responseCode = responseCode,
                    responseMessage = connection.responseMessage,
                    responseBody = responseBody,
                )
                runCatching { onHttpError(error) }
                throw error
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
        val group: String,
        val introducer: Boolean,
        val autoAcceptFolders: Boolean,
        val compression: NewDeviceConfiguration.Compression,
        val numConnections: Int,
        val maxSendKiBPerSecond: Int,
        val maxReceiveKiBPerSecond: Int,
        val untrusted: Boolean,
    )

    data class RestConnection(
        val connected: Boolean,
        val address: String?,
        val clientVersion: String?,
        val lastConnectionAt: LocalDateTime?,
    )

    data class RestFolder(
        val id: String,
        val label: String?,
        val group: String,
        val path: String,
        val type: String,
        val paused: Boolean,
        val fsWatcherEnabled: Boolean,
        val rescanIntervalSeconds: Int,
        val versioning: RestFolderVersioning,
        val devices: List<FolderDeviceConfiguration>,
    )

    data class RestFolderVersioning(
        val type: NewFolderConfiguration.Versioning,
        val supported: Boolean,
        val cleanoutDays: Int,
        val keep: Int,
        val cleanupIntervalSeconds: Int,
    )

    data class RestFolderStatus(
        val state: String,
        val localFiles: Long,
        val localBytes: Long,
        val needFiles: Long,
        val needBytes: Long,
        val pullErrors: Long,
    )

    data class RestFolderIgnores(
        val patterns: List<String>,
        val error: String?,
    )

    companion object {
        private const val DEFAULT_BASE_URL = "http://127.0.0.1:8384"
        private const val TIMEOUT_MILLIS = 1_500
        private const val MAX_ERROR_BODY_LENGTH = 8 * 1024
        private const val REDACTED_VALUE = "<redacted>"
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
                val errorValue = service?.opt("error")
                val error = if (errorValue == null || errorValue == JSONObject.NULL) {
                    null
                } else {
                    errorValue.toString().takeIf(String::isNotBlank)
                }
                add(RestListenAddress(serviceAddress, error))
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

    private fun parseVersioning(json: JSONObject?): RestFolderVersioning {
        val params = json?.optJSONObject("params")
        val rawType = json?.optString("type").orEmpty()
        val type = when (rawType) {
            "trashcan" -> NewFolderConfiguration.Versioning.TRASHCAN
            "simple" -> NewFolderConfiguration.Versioning.SIMPLE
            else -> NewFolderConfiguration.Versioning.NONE
        }
        return RestFolderVersioning(
            type = type,
            supported = rawType.isBlank() || rawType == "trashcan" || rawType == "simple",
            cleanoutDays = params?.optString("cleanoutDays")?.toIntOrNull() ?: 0,
            keep = params?.optString("keep")?.toIntOrNull() ?: 5,
            cleanupIntervalSeconds = json?.optInt("cleanupIntervalS", 3600) ?: 3600,
        )
    }

    private fun parseFolderDevices(array: JSONArray?): List<FolderDeviceConfiguration> {
        if (array == null) return emptyList()
        return buildList {
            repeat(array.length()) { index ->
                val device = array.optJSONObject(index) ?: return@repeat
                val deviceId = device.optString("deviceID").takeIf(String::isNotBlank)
                    ?: return@repeat
                add(
                    FolderDeviceConfiguration(
                        deviceId = deviceId,
                        encryptionPassword = device.optString("encryptionPassword"),
                    ),
                )
            }
        }
    }

    private fun applyFolderConfiguration(
        folder: JSONObject,
        configuration: NewFolderConfiguration,
    ) {
        folder.put("id", configuration.folderId)
        folder.put("label", configuration.label)
        folder.put("group", configuration.group)
        folder.put("fsWatcherEnabled", configuration.fsWatcherEnabled)
        folder.put("rescanIntervalS", configuration.rescanIntervalSeconds)
        folder.put(
            "type",
            when (configuration.type) {
                NewFolderConfiguration.Type.SEND_RECEIVE -> "sendreceive"
                NewFolderConfiguration.Type.RECEIVE_ONLY -> "receiveonly"
                NewFolderConfiguration.Type.SEND_ONLY -> "sendonly"
            },
        )

        val currentDevices = folder.optJSONArray("devices")
        val updatedDevices = JSONArray()
        val editableCurrentDevices = mutableMapOf<String, JSONObject>()
        if (currentDevices != null) {
            repeat(currentDevices.length()) { index ->
                val device = currentDevices.optJSONObject(index) ?: return@repeat
                val deviceId = device.optString("deviceID")
                if (deviceId !in configuration.availableDeviceIds) {
                    updatedDevices.put(device)
                } else {
                    editableCurrentDevices[deviceId] = device
                }
            }
        }
        configuration.devices.forEach { configuredDevice ->
            val device = editableCurrentDevices[configuredDevice.deviceId] ?: JSONObject()
            updatedDevices.put(
                device
                    .put("deviceID", configuredDevice.deviceId)
                    .put("encryptionPassword", configuredDevice.encryptionPassword),
            )
        }
        folder.put("devices", updatedDevices)

        if (configuration.updateVersioning) {
            val versioning = folder.optJSONObject("versioning") ?: JSONObject()
            val params = versioning.optJSONObject("params") ?: JSONObject()
            when (configuration.versioning) {
                NewFolderConfiguration.Versioning.NONE -> {
                    versioning.put("type", "")
                    versioning.put("cleanupIntervalS", 0)
                    params.remove("cleanoutDays")
                    params.remove("keep")
                }

                NewFolderConfiguration.Versioning.TRASHCAN -> {
                    versioning.put("type", "trashcan")
                    versioning.put("cleanupIntervalS", configuration.versioningCleanupIntervalSeconds)
                    params.put("cleanoutDays", configuration.versioningCleanoutDays.toString())
                    params.remove("keep")
                }

                NewFolderConfiguration.Versioning.SIMPLE -> {
                    versioning.put("type", "simple")
                    versioning.put("cleanupIntervalS", configuration.versioningCleanupIntervalSeconds)
                    params.put("cleanoutDays", configuration.versioningCleanoutDays.toString())
                    params.put("keep", configuration.versioningKeep.toString())
                }
            }
            versioning.put("params", params)
            folder.put("versioning", versioning)
        }
    }

    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun parseGuiAddress(address: String): Pair<String, Int> {
        val normalizedAddress = address.trim().ifBlank { "127.0.0.1:8384" }
        if (normalizedAddress.startsWith("[")) {
            val closingBracket = normalizedAddress.indexOf(']')
            val port = normalizedAddress
                .substringAfter("]:", "")
                .toIntOrNull()
                ?: 8384
            if (closingBracket > 1) {
                return normalizedAddress.substring(1, closingBracket) to port
            }
        }
        val separatorIndex = normalizedAddress.lastIndexOf(':')
        if (separatorIndex > 0) {
            val port = normalizedAddress.substring(separatorIndex + 1).toIntOrNull()
            if (port != null) {
                return normalizedAddress.substring(0, separatorIndex) to port
            }
        }
        return normalizedAddress to 8384
    }

    private fun formatGuiAddress(address: String, port: Int): String {
        val normalizedAddress = address.trim().removePrefix("[").removeSuffix("]")
        return if (':' in normalizedAddress) "[$normalizedAddress]:$port" else "$normalizedAddress:$port"
    }
}

internal class SyncthingRestException(
    val method: String,
    val path: String,
    val responseCode: Int,
    val responseMessage: String?,
    val responseBody: String?,
) : IOException(
    buildString {
        append("Syncthing REST $method $path 返回 HTTP $responseCode")
        responseMessage?.takeIf(String::isNotBlank)?.let { append(" $it") }
        responseBody?.takeIf(String::isNotBlank)?.let { append("：$it") }
    },
)

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private fun List<String>.toJsonArray(): JSONArray = JSONArray().also { array ->
    forEach(array::put)
}
