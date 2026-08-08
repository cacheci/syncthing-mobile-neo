package moe.https.syncthing.core

import at.favre.lib.crypto.bcrypt.BCrypt
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal class SyncthingConfigFile(
    private val file: File,
) {
    val exists: Boolean
        get() = file.isFile

    fun read(
        guiPortConflictBehavior: SettingConfiguration.GuiPortConflictBehavior,
        localDeviceId: String?,
    ): SettingConfiguration {
        val document = readDocument()
        val root = document.documentElement
            ?.takeIf { it.tagName == CONFIGURATION_TAG }
            ?: error("Syncthing 配置文件缺少 configuration 根节点")
        val gui = root.childElement(GUI_TAG)
            ?: error("Syncthing 配置文件缺少 gui 节点")
        val options = root.childElement(OPTIONS_TAG)
            ?: error("Syncthing 配置文件缺少 options 节点")
        val minHomeDiskFree = options.childElement(MIN_HOME_DISK_FREE_TAG)
        val guiPasswordConfigured = gui.childText(PASSWORD_TAG).isNotBlank()
        val (guiListenAddress, guiPort) = parseGuiAddress(gui.childText(ADDRESS_TAG))
        val localDevice = root.localDeviceElement(localDeviceId)

        return SettingConfiguration(
            deviceName = localDevice?.getAttribute(NAME_ATTRIBUTE)?.ifBlank { "Syncthing" } ?: "Syncthing",
            minHomeDiskFree = minHomeDiskFree?.textContent?.trim()?.toDoubleOrNull() ?: 1.0,
            minHomeDiskFreeUnit = SettingConfiguration.DiskSpaceUnit.entries
                .firstOrNull { it.apiValue == minHomeDiskFree?.getAttribute(UNIT_ATTRIBUTE) }
                ?: SettingConfiguration.DiskSpaceUnit.PERCENT,
            usageReportingEnabled = options.childInt(UR_ACCEPTED_TAG, 0) > 0,
            usageReportingVersion = maxOf(
                options.childInt(UR_ACCEPTED_TAG, 0),
                options.childInt(UR_SEEN_TAG, 0),
                1,
            ),
            guiListenAddress = guiListenAddress,
            guiPort = guiPort,
            guiPortConflictBehavior = guiPortConflictBehavior,
            guiAuthenticationEnabled = gui.childText(USER_TAG).isNotBlank() || guiPasswordConfigured,
            guiUser = gui.childText(USER_TAG),
            guiPasswordConfigured = guiPasswordConfigured,
            guiTheme = SettingConfiguration.GuiTheme.entries
                .firstOrNull { it.apiValue == gui.childText(THEME_TAG) }
                ?: SettingConfiguration.GuiTheme.DEFAULT,
            listenAddresses = options.childTexts(LISTEN_ADDRESS_TAG).ifEmpty { listOf("default") },
            maxSendKiBPerSecond = options.childInt(MAX_SEND_TAG, 0),
            maxReceiveKiBPerSecond = options.childInt(MAX_RECEIVE_TAG, 0),
            reconnectionIntervalSeconds = options.childInt(RECONNECTION_INTERVAL_TAG, 60),
            limitBandwidthInLan = options.childBoolean(LIMIT_BANDWIDTH_IN_LAN_TAG, false),
            globalDiscoveryEnabled = options.childBoolean(GLOBAL_DISCOVERY_ENABLED_TAG, true),
            globalDiscoveryServers = options.childTexts(GLOBAL_DISCOVERY_SERVER_TAG)
                .ifEmpty { listOf("default") },
            localDiscoveryEnabled = options.childBoolean(LOCAL_DISCOVERY_ENABLED_TAG, true),
            localDiscoveryPort = options.childInt(LOCAL_DISCOVERY_PORT_TAG, 21027),
            localDiscoveryMulticastAddress = options.childText(LOCAL_DISCOVERY_MULTICAST_TAG)
                .ifBlank { "[ff12::8384]:21027" },
            announceLanAddresses = options.childBoolean(ANNOUNCE_LAN_ADDRESSES_TAG, true),
            natEnabled = options.childBoolean(NAT_ENABLED_TAG, true),
            relaysEnabled = options.childBoolean(RELAYS_ENABLED_TAG, true),
            alwaysLocalNetworks = options.childTexts(ALWAYS_LOCAL_NETWORK_TAG),
            connectionLimitEnough = options.childInt(CONNECTION_LIMIT_ENOUGH_TAG, 0),
            connectionLimitMax = options.childInt(CONNECTION_LIMIT_MAX_TAG, 0),
            allowGuiListenNonLocal = options.childBoolean(GUI_LISTEN_NON_LOCAL_ALLOWED, false),
        )
    }

    fun write(
        configuration: SettingConfiguration,
        localDeviceId: String?,
    ) {
        val document = readDocument()
        val root = document.documentElement
            ?.takeIf { it.tagName == CONFIGURATION_TAG }
            ?: error("Syncthing 配置文件缺少 configuration 根节点")
        val gui = root.ensureChild(document, GUI_TAG)
        val options = root.ensureChild(document, OPTIONS_TAG)

        gui.setChildText(
            document,
            ADDRESS_TAG,
            formatGuiAddress(configuration.guiListenAddress, configuration.guiPort),
        )
        gui.setChildText(
            document,
            USER_TAG,
            if (configuration.guiAuthenticationEnabled) configuration.guiUser else "",
        )
        when {
            !configuration.guiAuthenticationEnabled -> {
                gui.setChildText(document, PASSWORD_TAG, "")
            }
            configuration.newGuiPassword.isNotBlank() -> {
                val password = configuration.newGuiPassword.toCharArray()
                try {
                    val passwordHash = BCrypt.withDefaults().hashToString(BCRYPT_COST, password)
                    gui.setChildText(document, PASSWORD_TAG, passwordHash)
                } finally {
                    password.fill('\u0000')
                }
            }
        }
        gui.setChildText(document, THEME_TAG, configuration.guiTheme.apiValue)

        root.localDeviceElement(localDeviceId)?.setAttribute(NAME_ATTRIBUTE, configuration.deviceName)
        val minHomeDiskFree = options.ensureChild(document, MIN_HOME_DISK_FREE_TAG)
        minHomeDiskFree.textContent = configuration.minHomeDiskFree.toPlainString()
        minHomeDiskFree.setAttribute(UNIT_ATTRIBUTE, configuration.minHomeDiskFreeUnit.apiValue)
        options.setChildText(
            document,
            UR_ACCEPTED_TAG,
            if (configuration.usageReportingEnabled) {
                maxOf(configuration.usageReportingVersion, 1).toString()
            } else {
                "-1"
            },
        )
        options.replaceChildren(document, LISTEN_ADDRESS_TAG, configuration.listenAddresses)
        options.setChildText(document, MAX_SEND_TAG, configuration.maxSendKiBPerSecond.toString())
        options.setChildText(document, MAX_RECEIVE_TAG, configuration.maxReceiveKiBPerSecond.toString())
        options.setChildText(
            document,
            RECONNECTION_INTERVAL_TAG,
            configuration.reconnectionIntervalSeconds.toString(),
        )
        options.setChildText(document, LIMIT_BANDWIDTH_IN_LAN_TAG, configuration.limitBandwidthInLan.toString())
        options.setChildText(document, GLOBAL_DISCOVERY_ENABLED_TAG, configuration.globalDiscoveryEnabled.toString())
        options.replaceChildren(
            document,
            GLOBAL_DISCOVERY_SERVER_TAG,
            configuration.globalDiscoveryServers,
        )
        options.setChildText(document, LOCAL_DISCOVERY_ENABLED_TAG, configuration.localDiscoveryEnabled.toString())
        options.setChildText(document, LOCAL_DISCOVERY_PORT_TAG, configuration.localDiscoveryPort.toString())
        options.setChildText(
            document,
            LOCAL_DISCOVERY_MULTICAST_TAG,
            configuration.localDiscoveryMulticastAddress,
        )
        options.setChildText(document, ANNOUNCE_LAN_ADDRESSES_TAG, configuration.announceLanAddresses.toString())
        options.setChildText(document, NAT_ENABLED_TAG, configuration.natEnabled.toString())
        options.setChildText(document, RELAYS_ENABLED_TAG, configuration.relaysEnabled.toString())
        options.replaceChildren(document, ALWAYS_LOCAL_NETWORK_TAG, configuration.alwaysLocalNetworks)
        options.setChildText(document, CONNECTION_LIMIT_ENOUGH_TAG, configuration.connectionLimitEnough.toString())
        options.setChildText(document, CONNECTION_LIMIT_MAX_TAG, configuration.connectionLimitMax.toString())

        writeDocumentAtomically(document)
    }

    private fun readDocument(): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { isXIncludeAware = false }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        return factory.newDocumentBuilder().parse(file)
    }

    private fun writeDocumentAtomically(document: Document) {
        val parent = file.parentFile ?: error("Syncthing 配置目录不存在")
        parent.mkdirs()
        val temporaryFile = File.createTempFile("config-", ".xml.tmp", parent)
        try {
            FileOutputStream(temporaryFile).use { output ->
                TransformerFactory.newInstance().newTransformer().apply {
                    setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                    setOutputProperty(OutputKeys.INDENT, "yes")
                }.transform(DOMSource(document), StreamResult(output))
                output.fd.sync()
            }
            try {
                Files.move(
                    temporaryFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporaryFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            temporaryFile.delete()
        }
    }

    private fun parseGuiAddress(address: String): Pair<String, Int> {
        val normalizedAddress = address.trim().ifBlank { DEFAULT_GUI_ADDRESS }
        if (normalizedAddress.startsWith("[")) {
            val closingBracket = normalizedAddress.indexOf(']')
            if (closingBracket > 1) {
                val port = normalizedAddress.substringAfter("]:", "").toIntOrNull() ?: DEFAULT_GUI_PORT
                return normalizedAddress.substring(1, closingBracket) to port
            }
        }
        val separatorIndex = normalizedAddress.lastIndexOf(':')
        if (separatorIndex > 0) {
            val port = normalizedAddress.substring(separatorIndex + 1).toIntOrNull()
            if (port != null) return normalizedAddress.substring(0, separatorIndex) to port
        }
        return normalizedAddress to DEFAULT_GUI_PORT
    }

    private fun formatGuiAddress(address: String, port: Int): String {
        val normalizedAddress = address.trim().removePrefix("[").removeSuffix("]")
        return if (':' in normalizedAddress) "[$normalizedAddress]:$port" else "$normalizedAddress:$port"
    }

    private fun Double.toPlainString(): String =
        if (this % 1.0 == 0.0) toLong().toString() else toString()

    companion object {
        private const val BCRYPT_COST = 10
        private const val DEFAULT_GUI_ADDRESS = "127.0.0.1:8384"
        private const val DEFAULT_GUI_PORT = 8384
        private const val CONFIGURATION_TAG = "configuration"
        private const val GUI_TAG = "gui"
        private const val OPTIONS_TAG = "options"
        private const val ADDRESS_TAG = "address"
        private const val USER_TAG = "user"
        private const val PASSWORD_TAG = "password"
        private const val THEME_TAG = "theme"
        private const val DEVICE_TAG = "device"
        private const val ID_ATTRIBUTE = "id"
        private const val NAME_ATTRIBUTE = "name"
        private const val MIN_HOME_DISK_FREE_TAG = "minHomeDiskFree"
        private const val UNIT_ATTRIBUTE = "unit"
        private const val UR_ACCEPTED_TAG = "urAccepted"
        private const val UR_SEEN_TAG = "urSeen"
        private const val LISTEN_ADDRESS_TAG = "listenAddress"
        private const val MAX_SEND_TAG = "maxSendKbps"
        private const val MAX_RECEIVE_TAG = "maxRecvKbps"
        private const val RECONNECTION_INTERVAL_TAG = "reconnectionIntervalS"
        private const val LIMIT_BANDWIDTH_IN_LAN_TAG = "limitBandwidthInLan"
        private const val GLOBAL_DISCOVERY_ENABLED_TAG = "globalAnnounceEnabled"
        private const val GLOBAL_DISCOVERY_SERVER_TAG = "globalAnnounceServer"
        private const val LOCAL_DISCOVERY_ENABLED_TAG = "localAnnounceEnabled"
        private const val LOCAL_DISCOVERY_PORT_TAG = "localAnnouncePort"
        private const val LOCAL_DISCOVERY_MULTICAST_TAG = "localAnnounceMCAddr"
        private const val ANNOUNCE_LAN_ADDRESSES_TAG = "announceLANAddresses"
        private const val NAT_ENABLED_TAG = "natEnabled"
        private const val RELAYS_ENABLED_TAG = "relaysEnabled"
        private const val ALWAYS_LOCAL_NETWORK_TAG = "alwaysLocalNet"
        private const val CONNECTION_LIMIT_ENOUGH_TAG = "connectionLimitEnough"
        private const val CONNECTION_LIMIT_MAX_TAG = "connectionLimitMax"
        private const val GUI_LISTEN_NON_LOCAL_ALLOWED = "allowGuiListenNonLocal"
    }

    private fun Element.localDeviceElement(localDeviceId: String?): Element? {
        val devices = buildList {
            val children = childNodes
            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child is Element && child.tagName == DEVICE_TAG) add(child)
            }
        }
        return localDeviceId
            ?.let { id -> devices.firstOrNull { it.getAttribute(ID_ATTRIBUTE) == id } }
            ?: devices.singleOrNull()
    }
}

private fun Element.childElement(tagName: String): Element? {
    val children = childNodes
    for (index in 0 until children.length) {
        val child = children.item(index)
        if (child is Element && child.tagName == tagName) return child
    }
    return null
}

private fun Element.ensureChild(document: Document, tagName: String): Element =
    childElement(tagName) ?: document.createElement(tagName).also { appendChild(it) }

private fun Element.childText(tagName: String): String =
    childElement(tagName)?.textContent?.trim().orEmpty()

private fun Element.childTexts(tagName: String): List<String> = buildList {
    val children = childNodes
    for (index in 0 until children.length) {
        val child = children.item(index)
        if (child is Element && child.tagName == tagName) {
            child.textContent.trim().takeIf(String::isNotBlank)?.let(::add)
        }
    }
}

private fun Element.childInt(tagName: String, defaultValue: Int): Int =
    childText(tagName).toIntOrNull() ?: defaultValue

private fun Element.childBoolean(tagName: String, defaultValue: Boolean): Boolean =
    childText(tagName).toBooleanStrictOrNull() ?: defaultValue

private fun Element.setChildText(document: Document, tagName: String, value: String) {
    ensureChild(document, tagName).textContent = value
}

private fun Element.replaceChildren(document: Document, tagName: String, values: List<String>) {
    val matchingChildren = buildList {
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element && child.tagName == tagName) add(child)
        }
    }
    matchingChildren.forEach { removeChild(it) }
    values.forEach { value ->
        appendChild(document.createElement(tagName).apply { textContent = value })
    }
}
