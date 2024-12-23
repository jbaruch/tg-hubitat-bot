package jbaru.ch.telegram.hubitat

import jbaru.ch.telegram.hubitat.model.Device
import jbaru.ch.telegram.hubitat.model.Hub
import kotlinx.serialization.json.Json

class DeviceManager(deviceListJson: String) {
    private val deviceCache: MutableMap<String, Device> = mutableMapOf()
    private lateinit var devices: List<Device>
    private val allDeviceCommands: Set<String>

    init {
        refreshDevices(deviceListJson)
        allDeviceCommands = devices.flatMap { it.supportedOps?.keys ?: emptySet() }.toSet()
    }

    fun refreshDevices(deviceListJson: String): Pair<Int, List<String>> {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            allowStructuredMapKeys = true
            coerceInputValues = true
        }
        devices = json.decodeFromString<List<Device>>(deviceListJson)
        val warnings = initializeCache(devices)
        return Pair(devices.size, warnings)
    }

    fun isDeviceCommand(command: String): Boolean {
        return command in allDeviceCommands
    }

    fun findDevice(name: String, command: String): Result<Device> {
        val normalizedQuery = name.lowercase()
        val device = deviceCache[normalizedQuery]

        if (device != null) {
            return if (device.supportedOps.containsKey(command)) {
                Result.success(device)
            } else {
                Result.failure(IllegalArgumentException("Command '$command' is not supported by device '${device.label}'"))
            }
        }

        return Result.failure(Exception("No device found for query: ${name}"))
    }

    fun <T : Device> findDevicesByType(type: Class<T>): List<T> {
        return devices.filterIsInstance(type)
    }

    fun list(): String {
        val deviceToAliases = mutableMapOf<Device, MutableList<String>>()
        var maxDeviceNameLength = 0
        var maxAliasesLength = 0

        // Group aliases by device and find maximum lengths
        deviceCache.forEach { (alias, device) ->
            deviceToAliases.getOrPut(device) { mutableListOf() }.add(alias)
            maxDeviceNameLength = maxOf(maxDeviceNameLength, device.label.length)
            maxAliasesLength = maxOf(maxAliasesLength, deviceToAliases[device]!!.joinToString(", ").length)
        }

        // Ensure column headers don't get cut off
        maxDeviceNameLength = maxOf(maxDeviceNameLength, "Device".length)
        maxAliasesLength = maxOf(maxAliasesLength, "Aliases".length)

        // Build the table string
        val tableBuilder = StringBuilder()
        tableBuilder.appendLine("```")
        tableBuilder.appendLine("+" + "-".repeat(maxDeviceNameLength + 2) + "+" + "-".repeat(maxAliasesLength + 2) + "+")
        tableBuilder.appendLine("| ${"Device".padEnd(maxDeviceNameLength)} | ${"Aliases".padEnd(maxAliasesLength)} |")
        tableBuilder.appendLine("+" + "-".repeat(maxDeviceNameLength + 2) + "+" + "-".repeat(maxAliasesLength + 2) + "+")

        deviceToAliases.forEach { (device, aliases) ->
            val aliasesString = aliases.joinToString(", ")
            tableBuilder.appendLine(
                "| ${device.label.padEnd(maxDeviceNameLength)} | ${
                    aliasesString.padEnd(
                        maxAliasesLength
                    )
                } |"
            )
        }

        tableBuilder.appendLine("+" + "-".repeat(maxDeviceNameLength + 2) + "+" + "-".repeat(maxAliasesLength + 2) + "+")

        tableBuilder.appendLine("```")
        return tableBuilder.toString()
    }

    fun listByType(): Map<String, String> {
        // Group devices by their base type
        val devicesByType = deviceCache.values.groupBy { device ->
            when (device) {
                is Device.Actuator -> "Actuators"
                is Device.Button -> "Buttons"
                is Device.Sensor -> "Sensors"
                is Device.Shade -> "Shades"
                is Hub -> "Hubs"
            }
        }.toSortedMap()

        return devicesByType.mapValues { (_, devices) ->
            val deviceToAliases = mutableMapOf<Device, MutableList<String>>()
            var maxDeviceNameLength = 0
            var maxAliasesLength = 0

            // Group aliases by device and find maximum lengths
            devices.forEach { device ->
                deviceToAliases[device] = mutableListOf(device.label)
                maxDeviceNameLength = maxOf(maxDeviceNameLength, device.label.length)
                maxAliasesLength = maxOf(maxAliasesLength, deviceToAliases[device]!!.joinToString(", ").length)
            }

            // Build the table string
            val tableBuilder = StringBuilder()
            deviceToAliases.forEach { (device, aliases) ->
                tableBuilder.appendLine("\\- ${device.label}")
            }

            tableBuilder.toString().trim()
        }
    }

    override fun toString(): String {
        return deviceCache.entries.joinToString("\n") { (key, device) -> "$key -> ${device.label}" }
    }

    private fun initializeCache(devices: List<Device>): List<String> {
        deviceCache.clear()
        val warnings = mutableListOf<String>()

        // First, add each device with its full name and without "Light(s)" suffix
        for (device in devices) {
            val fullName = device.label.lowercase()
            warnings.addAll(addToCache(fullName, device))

            // Add name without "Light" or "Lights" if applicable
            val nameWithoutLights = removeLightSuffix(fullName)
            if (nameWithoutLights != fullName) {
                warnings.addAll(addToCache(nameWithoutLights, device))
            }
        }

        // Then, add abbreviations
        val nameMatrix = DeviceAbbreviator()
        for (device in devices) {
            val fullName = device.label.lowercase()
            nameMatrix.addName(fullName)
        }
        nameMatrix.abbreviate()

        for (device in devices) {
            val fullName = device.label.lowercase()
            val abbreviation = nameMatrix.getAbbreviation(fullName)
            if (abbreviation.isSuccess) {
                warnings.addAll(addToCache(abbreviation.getOrThrow(), device))
            } else {
                val message = "WARNING Device name was not abbreviated: $fullName"
                warnings.add(message)
                println(message)
            }
        }

        return warnings
    }

    private fun addToCache(key: String, device: Device): List<String> {
        val warnings = mutableListOf<String>()
        if (deviceCache.containsKey(key)) {
            val message = "WARNING Duplicate key found in cache: $key"
            warnings.add(message)
            println(message)
        }
        deviceCache[key] = device
        return warnings
    }

    private fun removeLightSuffix(name: String): String {
        return name.replace(Regex(" lights?$", RegexOption.IGNORE_CASE), "").trim()
    }
}
