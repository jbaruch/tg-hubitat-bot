package jbaru.ch.telegram.hubitat

import jbaru.ch.telegram.hubitat.model.Device
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

class DeviceManager(deviceListJson: String) {

    private val logger = LoggerFactory.getLogger(DeviceManager::class.java)

    // One snapshot behind a single volatile reference: telegram-bot dispatch
    // is multi-threaded, so a /refresh racing a concurrently handled command
    // must never expose a half-built cache or a mixed epoch (new device list
    // with the old cache). Readers grab the reference once and see one
    // consistent state. The guarantee is the atomic swap - nothing mutates a
    // Snapshot's collections after publication, but the contained Device
    // objects themselves (Hub's var ip/managementToken) are not deeply
    // immutable.
    private class Snapshot(
        val devices: List<Device>,
        val deviceCache: Map<String, Device>,
        val allDeviceCommands: Set<String>
    )

    @Volatile private var snapshot: Snapshot = Snapshot(emptyList(), emptyMap(), emptySet())

    init {
        refreshDevices(deviceListJson)
    }

    // Per-device resilience: any decode failure - unknown type, missing field,
    // serializer quirk - skips that device with a warning instead of taking
    // the whole list (and the bot) down at boot.
    @Suppress("TooGenericExceptionCaught")
    @Synchronized
    fun refreshDevices(deviceListJson: String): Pair<Int, List<String>> {
        val format = Json { ignoreUnknownKeys = true }
        val warnings: MutableList<String> = ArrayList()

        // Decode device-by-device so a single unknown or malformed device (e.g. a
        // newly-installed driver with no @Serializable subclass yet) is skipped
        // with a visible warning instead of aborting the whole list and crashing
        // the bot at boot.
        val newDevices = format.parseToJsonElement(deviceListJson).jsonArray.mapNotNull { element ->
            try {
                format.decodeFromJsonElement<Device>(element)
            } catch (e: Exception) {
                val type = element.jsonObject["type"]?.jsonPrimitive?.content ?: "unknown"
                val label = element.jsonObject["label"]?.jsonPrimitive?.content ?: "unknown"
                val message = "WARNING Skipping unsupported device (type='$type', label='$label'): " +
                    (e.message?.substringBefore('\n') ?: e.toString())
                warnings.add(message)
                logger.warn(message.removePrefix("WARNING "))
                null
            }
        }

        val newCache = mutableMapOf<String, Device>()
        warnings.addAll(initializeCache(newDevices, newCache))

        // The command set is recomputed on every refresh, not just at boot: a
        // command a device introduces via /refresh must start matching in
        // DeviceCommandFilter.
        snapshot = Snapshot(
            devices = newDevices,
            deviceCache = newCache,
            allDeviceCommands = newDevices.flatMap { it.supportedOps.keys }.toSet()
        )
        return Pair(newDevices.size, warnings)
    }

    fun isDeviceCommand(command: String): Boolean {
        return command in snapshot.allDeviceCommands
    }

    fun findDevice(name: String, command: String): Result<Device> {
        val normalizedQuery = name.lowercase()
        val device = snapshot.deviceCache[normalizedQuery]

        if (device != null) {
            return if (device.supportedOps.containsKey(command)) {
                Result.success(device)
            } else {
                Result.failure(
                    IllegalArgumentException("Command '$command' is not supported by device '${device.label}'")
                )
            }
        }

        return Result.failure(Exception("No device found for query: $name"))
    }

    fun <T : Device> findDevicesByType(type: Class<T>): List<T> {
        return snapshot.devices.filterIsInstance(type)
    }

    fun list(): String {
        // Grab the reference once: the snapshot may be swapped mid-render.
        val cache = snapshot.deviceCache
        val deviceToAliases = mutableMapOf<Device, MutableList<String>>()
        var maxDeviceNameLength = 0
        var maxAliasesLength = 0

        // Group aliases by device and find maximum lengths
        cache.forEach { (alias, device) ->
            deviceToAliases.getOrPut(device) { mutableListOf() }.add(escapeMarkdownCode(alias))
            maxDeviceNameLength = maxOf(maxDeviceNameLength, escapeMarkdownCode(device.label).length)
            maxAliasesLength = maxOf(maxAliasesLength, deviceToAliases[device]!!.joinToString(", ").length)
        }

        // Ensure column headers don't get cut off
        maxDeviceNameLength = maxOf(maxDeviceNameLength, "Device".length)
        maxAliasesLength = maxOf(maxAliasesLength, "Aliases".length)

        // Build the table string
        val tableBuilder = StringBuilder()
        tableBuilder.appendLine("```")
        tableBuilder.appendLine(tableBorder(maxDeviceNameLength, maxAliasesLength))
        tableBuilder.appendLine("| ${"Device".padEnd(maxDeviceNameLength)} | ${"Aliases".padEnd(maxAliasesLength)} |")
        tableBuilder.appendLine(tableBorder(maxDeviceNameLength, maxAliasesLength))

        deviceToAliases.forEach { (device, aliases) ->
            val aliasesString = aliases.joinToString(", ")
            tableBuilder.appendLine(
                "| ${escapeMarkdownCode(device.label).padEnd(maxDeviceNameLength)} | ${
                    aliasesString.padEnd(
                        maxAliasesLength
                    )
                } |"
            )
        }

        tableBuilder.appendLine(tableBorder(maxDeviceNameLength, maxAliasesLength))

        tableBuilder.appendLine("```")
        return tableBuilder.toString()
    }

    fun listByType(): Map<String, String> {
        // Grab the reference once: the snapshot may be swapped mid-render.
        val cache = snapshot.deviceCache
        // Group devices by their base type
        val devicesByType = cache.values.groupBy { device ->
            when (device) {
                is Device.Actuator -> "Actuators"
                is Device.Button -> "Buttons"
                is Device.Sensor -> "Sensors"
                is Device.Shade -> "Shades"
                is Device.Hub -> "Hubs"
            }
        }.toSortedMap()

        return devicesByType.mapValues { (_, devices) ->
            val deviceToAliases = mutableMapOf<Device, MutableList<String>>()
            var maxDeviceNameLength = 0
            var maxAliasesLength = 0

            // Group aliases by device and find maximum lengths for this type
            cache.forEach { (alias, device) ->
                if (devices.contains(device)) {
                    deviceToAliases.getOrPut(device) { mutableListOf() }.add(escapeMarkdownCode(alias))
                    maxDeviceNameLength = maxOf(maxDeviceNameLength, escapeMarkdownCode(device.label).length)
                    maxAliasesLength = maxOf(maxAliasesLength, deviceToAliases[device]!!.joinToString(", ").length)
                }
            }

            // Ensure column headers don't get cut off
            maxDeviceNameLength = maxOf(maxDeviceNameLength, "Device".length)
            maxAliasesLength = maxOf(maxAliasesLength, "Aliases".length)

            buildString {
                appendLine("```")
                appendLine(tableBorder(maxDeviceNameLength, maxAliasesLength))
                appendLine("| ${"Device".padEnd(maxDeviceNameLength)} | ${"Aliases".padEnd(maxAliasesLength)} |")
                appendLine(tableBorder(maxDeviceNameLength, maxAliasesLength))

                deviceToAliases.forEach { (device, aliases) ->
                    val aliasesString = aliases.joinToString(", ")
                    appendLine(
                        "| ${escapeMarkdownCode(device.label).padEnd(maxDeviceNameLength)} | " +
                            "${aliasesString.padEnd(maxAliasesLength)} |"
                    )
                }

                appendLine(tableBorder(maxDeviceNameLength, maxAliasesLength))
                appendLine("```")
            }
        }
    }


    val deviceCount: Int
        get() = snapshot.devices.size

    private fun initializeCache(devices: List<Device>, cache: MutableMap<String, Device>): List<String> {
        val warnings: MutableList<String> = ArrayList()
        val nameMatrix = DeviceAbbreviator()
        for (device in devices) {
            val fullName = device.label.lowercase()
            warnings.addAll(addToCache(cache, fullName, device))

            // Add name without "Light" or "Lights" if applicable
            val nameWithoutLights = removeLightSuffix(fullName)
            if (nameWithoutLights != fullName) {
                warnings.addAll(addToCache(cache, nameWithoutLights, device))
            }

            nameMatrix.addName(fullName)
        }
        nameMatrix.abbreviate()
        for (device in devices) {
            val fullName = device.label.lowercase()
            val abbreviation = nameMatrix.getAbbreviation(fullName)
            if (abbreviation.isSuccess) {
                warnings.addAll(addToCache(cache, abbreviation.getOrThrow(), device))
            } else {
                val message = "WARNING Device name was not abbreviated: $fullName"
                warnings.add(message)
                logger.warn(message.removePrefix("WARNING "))
            }
        }
        return warnings
    }

    private fun addToCache(cache: MutableMap<String, Device>, key: String, device: Device): List<String> {
        val warnings: MutableList<String> = ArrayList()
        if (cache.containsKey(key)) {
            val message = "WARNING Duplicate key found in cache: $key"
            warnings.add(message)
            logger.warn(message.removePrefix("WARNING "))
        }
        cache[key] = device
        return warnings
    }

    // The /list tables render inside MarkdownV2 code fences, where every
    // character is literal EXCEPT backslash and backtick - those two are the
    // only ones that can break the fence and fail the whole sendMessage.
    private fun tableBorder(nameWidth: Int, aliasWidth: Int): String =
        "+" + "-".repeat(nameWidth + 2) + "+" + "-".repeat(aliasWidth + 2) + "+"

    private fun escapeMarkdownCode(s: String): String =
        s.replace("\\", "\\\\").replace("`", "\\`")

    private fun removeLightSuffix(name: String): String {
        return name.replace(Regex(" lights?$", RegexOption.IGNORE_CASE), "").trim()
    }
}
