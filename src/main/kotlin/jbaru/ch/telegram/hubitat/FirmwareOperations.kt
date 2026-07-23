package jbaru.ch.telegram.hubitat

import jbaru.ch.telegram.hubitat.model.CatalogEntry
import jbaru.ch.telegram.hubitat.model.Device
import jbaru.ch.telegram.hubitat.model.FirmwareCatalog
import jbaru.ch.telegram.hubitat.model.FirmwareLine
import jbaru.ch.telegram.hubitat.model.firmwareMajor
import jbaru.ch.telegram.hubitat.model.parseFirmwareVersion
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerializationException
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
    internal const val CATALOG_URL =
        "https://raw.githubusercontent.com/jbaruch/tg-hubitat-bot/main/src/main/resources/zwave-firmware-catalog.json"
    private const val ZWAVE_NODE_ID_RADIX = 16

    fun loadCatalog(): FirmwareCatalog {
        val resource = FirmwareOperations::class.java.getResourceAsStream(CATALOG_RESOURCE)
            ?: throw IllegalStateException("Firmware catalog resource $CATALOG_RESOURCE not found")
        return json.decodeFromString<FirmwareCatalog>(resource.bufferedReader().readText())
    }

    /**
     * The catalog is read from the repo's main branch so that merging a
     * catalog-refresh PR is enough for /firmware to know the new versions — no
     * bot redeploy. The bundled copy is the offline fallback, and the report
     * header says so when it is used.
     */
    // Any failure here - network, HTTP, malformed JSON - must fall back to the
    // bundled catalog rather than kill /firmware; the note in the report says so.
    @Suppress("TooGenericExceptionCaught")
    suspend fun fetchCatalog(networkClient: NetworkClient): Pair<FirmwareCatalog, String?> =
        try {
            json.decodeFromString<FirmwareCatalog>(networkClient.getBody(CATALOG_URL)) to null
        } catch (e: Exception) {
            logger.warn("Could not fetch live firmware catalog, using bundled copy: {}", e.message)
            loadCatalog() to "bundled fallback — live catalog unreachable"
        }

    // Per-hub resilience: one unreachable hub becomes a report line, not a
    // dead /firmware command - whatever it threw.
    @Suppress("TooGenericExceptionCaught")
    suspend fun checkFirmware(
        hubs: List<Device.Hub>,
        networkClient: NetworkClient
    ): List<String> {
        val (catalog, catalogNote) = fetchCatalog(networkClient)
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

        return formatReport(findings, catalog, hubErrors, catalogNote)
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

        // Two HTTP calls per device add up on a large mesh - fetch concurrently,
        // capped so the hub isn't stampeded.
        val semaphore = Semaphore(8)
        return coroutineScope {
            zwaveEntries.map { data ->
                async {
                    val id = data["id"]!!.jsonPrimitive.content.toInt()
                    val base = ZwaveDeviceInfo(
                        hubLabel = hub.label,
                        deviceId = id,
                        dni = data["dni"]?.jsonPrimitive?.content ?: "",
                        name = data["name"]?.jsonPrimitive?.content ?: "device $id",
                        driverType = data["type"]?.jsonPrimitive?.content ?: ""
                    )
                    semaphore.withPermit {
                        try {
                            readDeviceFirmware(base, hub, networkClient)
                        } catch (e: CancellationException) {
                            // Never swallow structured-concurrency cancellation.
                            throw e
                        }
                        // The expected per-device failures - network (timeouts
                        // included: ktor's HttpRequestTimeoutException is an
                        // IOException), the explicit IllegalStateExceptions from
                        // readDeviceFirmware's error paths, and malformed JSON -
                        // mark the device unreadable instead of aborting the
                        // whole report.
                        catch (e: IOException) {
                            unreadable(base, hub, e)
                        } catch (e: IllegalStateException) {
                            unreadable(base, hub, e)
                        } catch (e: SerializationException) {
                            unreadable(base, hub, e)
                        } catch (e: IllegalArgumentException) {
                            unreadable(base, hub, e)
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private fun unreadable(base: ZwaveDeviceInfo, hub: Device.Hub, e: Exception): ZwaveDeviceInfo {
        val reason = KtorNetworkClient.redactSecrets(e.message ?: e.toString())
        logger.warn("Failed to read firmware info for '{}' on {}: {}", base.name, hub.label, reason)
        return base.copy(readError = reason)
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
            info = info.copy(firmwareVersion = fetchZwaveJsVersion(zwaveJsNodeId(base), hub, networkClient))
        }
        return info
    }

    private fun zwaveJsNodeId(base: ZwaveDeviceInfo): Int =
        base.dni.toIntOrNull(ZWAVE_NODE_ID_RADIX)
            ?: throw IllegalStateException(
                "driver reports no firmware version and DNI '${base.dni}' is not a Z-Wave node id"
            )

    private suspend fun fetchZwaveJsVersion(nodeId: Int, hub: Device.Hub, networkClient: NetworkClient): String {
        val details = Json.parseToJsonElement(
            networkClient.getBody("http://${hub.ip}/hub/zwave/deviceFirmware/details?nodeId=$nodeId")
        ).jsonObject
        if (details["success"]?.jsonPrimitive?.booleanOrNull != true) {
            throw IllegalStateException("deviceFirmware/details failed for node $nodeId")
        }
        return details["targets"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("version")?.jsonPrimitive?.content
            ?: throw IllegalStateException("deviceFirmware/details returned no targets for node $nodeId")
    }

    fun classify(device: ZwaveDeviceInfo, catalog: FirmwareCatalog): FirmwareFinding =
        FirmwareClassifier.classify(device, catalog)

    fun formatReport(
        findings: List<FirmwareFinding>,
        catalog: FirmwareCatalog,
        hubErrors: List<String> = emptyList(),
        catalogNote: String? = null
    ): List<String> = FirmwareReportFormatter.format(findings, catalog, hubErrors, catalogNote)
}
