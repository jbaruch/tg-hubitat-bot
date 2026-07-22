package jbaru.ch.telegram.hubitat

import jbaru.ch.telegram.hubitat.model.CatalogEntry
import jbaru.ch.telegram.hubitat.model.Device
import jbaru.ch.telegram.hubitat.model.FirmwareCatalog
import jbaru.ch.telegram.hubitat.model.FirmwareLine
import jbaru.ch.telegram.hubitat.model.firmwareMajor
import jbaru.ch.telegram.hubitat.model.parseFirmwareVersion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

data class ZwaveDeviceInfo(
    val hubLabel: String,
    val deviceId: Int,
    val dni: String,
    val name: String,
    val driverType: String,
    val deviceModel: String? = null,
    val firmwareVersion: String? = null,
    val manufacturer: Int? = null,
    val deviceType: Int? = null,
    val productId: Int? = null,
    val readError: String? = null
)

enum class FirmwareStatus {
    UPDATE_AVAILABLE,
    UP_TO_DATE,
    WARN,
    SKIP,
    CHECK_MANUALLY,
    NOT_IN_CATALOG,
    UNREADABLE
}

data class FirmwareFinding(
    val device: ZwaveDeviceInfo,
    val status: FirmwareStatus,
    val entry: CatalogEntry? = null,
    val line: FirmwareLine? = null,
    val detail: String? = null
)

object FirmwareOperations {

    private val logger = LoggerFactory.getLogger(FirmwareOperations::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private const val CATALOG_RESOURCE = "/zwave-firmware-catalog.json"
    private const val MAX_MESSAGE_LENGTH = 3900

    fun loadCatalog(): FirmwareCatalog {
        val resource = FirmwareOperations::class.java.getResourceAsStream(CATALOG_RESOURCE)
            ?: throw IllegalStateException("Firmware catalog resource $CATALOG_RESOURCE not found")
        return json.decodeFromString<FirmwareCatalog>(resource.bufferedReader().readText())
    }

    suspend fun checkFirmware(
        hubs: List<Device.Hub>,
        networkClient: NetworkClient
    ): List<String> {
        val catalog = loadCatalog()
        val findings = mutableListOf<FirmwareFinding>()
        val hubErrors = mutableListOf<String>()

        for (hub in hubs.filter { it.ip.isNotBlank() }) {
            try {
                val devices = collectZwaveDevices(hub, networkClient)
                findings.addAll(devices.map { classify(it, catalog) })
            } catch (e: Exception) {
                logger.error("Failed to collect Z-Wave devices from hub ${hub.label}", e)
                hubErrors.add("${hub.label}: ${e.message ?: e.toString()}")
            }
        }

        return formatReport(findings, catalog, hubErrors)
    }

    suspend fun collectZwaveDevices(
        hub: Device.Hub,
        networkClient: NetworkClient
    ): List<ZwaveDeviceInfo> {
        val listJson = Json.parseToJsonElement(
            networkClient.getBody("http://${hub.ip}/hub2/devicesList")
        ).jsonObject

        val zwaveEntries = (listJson["devices"] as? JsonArray ?: JsonArray(emptyList()))
            .mapNotNull { it.jsonObject["data"] as? JsonObject }
            .filter { it["isZwave"]?.jsonPrimitive?.booleanOrNull == true }

        return zwaveEntries.map { data ->
            val id = data["id"]!!.jsonPrimitive.content.toInt()
            val base = ZwaveDeviceInfo(
                hubLabel = hub.label,
                deviceId = id,
                dni = data["dni"]?.jsonPrimitive?.content ?: "",
                name = data["name"]?.jsonPrimitive?.content ?: "device $id",
                driverType = data["type"]?.jsonPrimitive?.content ?: ""
            )
            try {
                readDeviceFirmware(base, hub, networkClient)
            } catch (e: Exception) {
                logger.warn("Failed to read firmware info for '{}' on {}: {}", base.name, hub.label, e.message)
                base.copy(readError = e.message ?: e.toString())
            }
        }
    }

    private suspend fun readDeviceFirmware(
        base: ZwaveDeviceInfo,
        hub: Device.Hub,
        networkClient: NetworkClient
    ): ZwaveDeviceInfo {
        val full = Json.parseToJsonElement(
            networkClient.getBody("http://${hub.ip}/device/fullJson/${base.deviceId}")
        ).jsonObject
        // Driver-data values arrive as either Int or String depending on the
        // driver, and generic drivers (Springs, locks) omit the firmware fields
        // entirely — hence the zwaveJS fallback below.
        val data = full["device"]?.jsonObject?.get("data") as? JsonObject

        var info = base.copy(
            deviceModel = data?.get("deviceModel")?.jsonPrimitive?.content,
            firmwareVersion = data?.get("firmwareVersion")?.jsonPrimitive?.content,
            manufacturer = data?.get("manufacturer")?.jsonPrimitive?.content?.toIntOrNull(),
            deviceType = data?.get("deviceType")?.jsonPrimitive?.content?.toIntOrNull(),
            productId = data?.get("deviceId")?.jsonPrimitive?.content?.toIntOrNull()
        )

        if (info.firmwareVersion == null) {
            val nodeId = base.dni.toIntOrNull(16)
                ?: throw IllegalStateException("driver reports no firmware version and DNI '${base.dni}' is not a Z-Wave node id")
            val details = Json.parseToJsonElement(
                networkClient.getBody("http://${hub.ip}/hub/zwave/deviceFirmware/details?nodeId=$nodeId")
            ).jsonObject
            if (details["success"]?.jsonPrimitive?.booleanOrNull != true) {
                throw IllegalStateException("deviceFirmware/details failed for node $nodeId")
            }
            val version = details["targets"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("version")?.jsonPrimitive?.content
                ?: throw IllegalStateException("deviceFirmware/details returned no targets for node $nodeId")
            info = info.copy(firmwareVersion = version)
        }
        return info
    }

    fun classify(device: ZwaveDeviceInfo, catalog: FirmwareCatalog): FirmwareFinding {
        val matches = catalog.entries.filter { entry ->
            device.deviceModel != null && device.deviceModel in entry.match.deviceModels ||
                device.driverType.isNotEmpty() && device.driverType in entry.match.driverTypes ||
                entry.match.ids.any {
                    it.manufacturer == device.manufacturer &&
                        it.deviceType == device.deviceType &&
                        it.deviceId == device.productId
                }
        }

        val entry = when (matches.size) {
            0 -> return FirmwareFinding(device, FirmwareStatus.NOT_IN_CATALOG)
            1 -> matches.single()
            else -> return FirmwareFinding(
                device, FirmwareStatus.CHECK_MANUALLY,
                detail = "matches multiple catalog entries: ${matches.joinToString { it.model }}"
            )
        }

        if (entry.recommendation == "skip") {
            return FirmwareFinding(device, FirmwareStatus.SKIP, entry)
        }

        val versionString = device.firmwareVersion
            ?: return FirmwareFinding(
                device, FirmwareStatus.UNREADABLE, entry,
                detail = device.readError ?: "no firmware version reported"
            )
        val installed = parseFirmwareVersion(versionString)
            ?: return FirmwareFinding(
                device, FirmwareStatus.UNREADABLE, entry,
                detail = "unparseable firmware version '$versionString'"
            )
        val major = installed.toInt()

        // Line selection is the brick-avoidance gate: exactly one line may
        // claim the installed major, otherwise we refuse to recommend a file.
        val lines = entry.lines.filter { major in it.installedMajor }
        val line = when (lines.size) {
            1 -> lines.single()
            0 -> return FirmwareFinding(
                device, FirmwareStatus.CHECK_MANUALLY, entry,
                detail = "no known firmware line for installed version $versionString"
            )
            else -> return FirmwareFinding(
                device, FirmwareStatus.CHECK_MANUALLY, entry,
                detail = "installed version $versionString matches multiple lines: ${lines.joinToString { it.line }}"
            )
        }

        val latest = parseFirmwareVersion(line.latest)
            ?: return FirmwareFinding(
                device, FirmwareStatus.CHECK_MANUALLY, entry, line,
                detail = "catalog latest '${line.latest}' is unparseable"
            )

        // A line can span majors with a version-scheme reset (ZEN76 10.x
        // updates to 2.30), where numeric comparison is meaningless. Decimal
        // compare only holds within a major; an installed version numerically
        // ahead of a different-major latest is a scheme mismatch, not currency.
        if (installed >= latest && major != latest.toInt()) {
            return FirmwareFinding(
                device, FirmwareStatus.CHECK_MANUALLY, entry, line,
                detail = "installed $versionString vs line latest ${line.latest} — version scheme mismatch"
            )
        }

        return when {
            installed >= latest -> FirmwareFinding(device, FirmwareStatus.UP_TO_DATE, entry, line)
            entry.recommendation == "warn" -> FirmwareFinding(
                device, FirmwareStatus.WARN, entry, line, detail = entry.warning
            )
            else -> FirmwareFinding(device, FirmwareStatus.UPDATE_AVAILABLE, entry, line)
        }
    }

    fun formatReport(
        findings: List<FirmwareFinding>,
        catalog: FirmwareCatalog,
        hubErrors: List<String> = emptyList()
    ): List<String> {
        val byStatus = findings.groupBy { it.status }
        val sections = mutableListOf<String>()

        val hubCount = findings.map { it.device.hubLabel }.distinct().size
        sections.add(
            "Z-Wave firmware report: ${findings.size} devices on $hubCount hub(s), catalog ${catalog.catalogVersion}"
        )

        byStatus[FirmwareStatus.UPDATE_AVAILABLE]?.let { updates ->
            val section = StringBuilder("⬆️ Updates available (${updates.size}):\n")
            updates.groupBy { it.entry!! to it.line!! }.forEach { (key, group) ->
                val (entry, line) = key
                section.append("\n${entry.model} → ${line.latest} (${line.line})")
                if (entry.battery || entry.flirs) section.append(" 🔋 wake device for OTA")
                section.append("\n${line.url}\n")
                line.note?.let { section.append("note: $it\n") }
                group.forEach {
                    section.append("• ${it.device.name} [${it.device.hubLabel}] ${it.device.firmwareVersion}\n")
                }
            }
            sections.add(section.toString().trimEnd())
        }

        byStatus[FirmwareStatus.WARN]?.let { warns ->
            val section = StringBuilder("⚠️ Behind, but update NOT recommended (${warns.size}):\n")
            warns.groupBy { it.entry!! }.forEach { (entry, group) ->
                val line = group.first().line
                section.append("\n${entry.model}: ${line?.latest} available\n")
                group.first().detail?.let { section.append("$it\n") }
                group.forEach {
                    section.append("• ${it.device.name} [${it.device.hubLabel}] ${it.device.firmwareVersion}\n")
                }
            }
            sections.add(section.toString().trimEnd())
        }

        byStatus[FirmwareStatus.CHECK_MANUALLY]?.let { manual ->
            val section = StringBuilder("❓ Check manually (${manual.size}):\n")
            manual.forEach {
                section.append("• ${it.device.name} [${it.device.hubLabel}] ${it.entry?.model ?: it.device.driverType}: ${it.detail}\n")
            }
            sections.add(section.toString().trimEnd())
        }

        byStatus[FirmwareStatus.UNREADABLE]?.let { unreadable ->
            val section = StringBuilder("🚫 Could not read firmware (${unreadable.size}):\n")
            unreadable.forEach {
                section.append("• ${it.device.name} [${it.device.hubLabel}]: ${it.detail}\n")
            }
            sections.add(section.toString().trimEnd())
        }

        byStatus[FirmwareStatus.SKIP]?.let { skipped ->
            val section = StringBuilder("⏭️ Skipped by policy (${skipped.size}):\n")
            skipped.groupBy { it.entry!! }.forEach { (entry, group) ->
                section.append("• ${entry.model} (${entry.vendor}) ×${group.size}: ${entry.note}\n")
            }
            sections.add(section.toString().trimEnd())
        }

        byStatus[FirmwareStatus.NOT_IN_CATALOG]?.let { unknown ->
            val section = StringBuilder("📋 Not in catalog (${unknown.size}):\n")
            unknown.groupBy { it.device.deviceModel ?: it.device.driverType }.forEach { (key, group) ->
                val versions = group.mapNotNull { it.device.firmwareVersion }.distinct().sorted()
                val versionInfo = if (versions.isEmpty()) "" else " @ ${versions.joinToString("/")}"
                section.append("• $key ×${group.size}$versionInfo\n")
            }
            sections.add(section.toString().trimEnd())
        }

        byStatus[FirmwareStatus.UP_TO_DATE]?.let { current ->
            sections.add("✅ Up to date: ${current.size}")
        }

        if (hubErrors.isNotEmpty()) {
            sections.add("💥 Hubs that could not be checked:\n" + hubErrors.joinToString("\n") { "• $it" })
        }

        return chunk(sections)
    }

    // Telegram caps messages at 4096 chars; split on section boundaries, and
    // within a section on line boundaries if a single section is oversized.
    private fun chunk(sections: List<String>): List<String> {
        val messages = mutableListOf<String>()
        val current = StringBuilder()
        for (section in sections) {
            val pieces = if (section.length > MAX_MESSAGE_LENGTH) {
                splitOversized(section)
            } else {
                listOf(section)
            }
            for (piece in pieces) {
                if (current.isNotEmpty() && current.length + piece.length + 2 > MAX_MESSAGE_LENGTH) {
                    messages.add(current.toString().trimEnd())
                    current.clear()
                }
                if (current.isNotEmpty()) current.append("\n\n")
                current.append(piece)
            }
        }
        if (current.isNotEmpty()) messages.add(current.toString().trimEnd())
        return messages
    }

    private fun splitOversized(section: String): List<String> {
        val pieces = mutableListOf<String>()
        val current = StringBuilder()
        for (lineText in section.lineSequence()) {
            if (current.isNotEmpty() && current.length + lineText.length + 1 > MAX_MESSAGE_LENGTH) {
                pieces.add(current.toString().trimEnd())
                current.clear()
            }
            if (current.isNotEmpty()) current.append("\n")
            current.append(lineText)
        }
        if (current.isNotEmpty()) pieces.add(current.toString().trimEnd())
        return pieces
    }
}
