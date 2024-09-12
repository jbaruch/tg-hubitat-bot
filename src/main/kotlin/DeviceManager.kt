package jbaru.ch.telegram.hubitat

import jbaru.ch.telegram.hubitat.model.Device
import kotlinx.serialization.json.Json

class DeviceManager(deviceListJson: String) {

    private val deviceCache: MutableMap<String, Device> = mutableMapOf()
    private lateinit var devices: List<Device>

    init {
        refreshDevices(deviceListJson)
    }

    fun refreshDevices(deviceListJson: String): Pair<Int, List<String>> {
        val format = Json { ignoreUnknownKeys = true }
        devices = format.decodeFromString<List<Device>>(deviceListJson)
        deviceCache.clear()
        val result: Pair<Int,List<String>> = Pair(devices.size, initializeCache(devices))
        return result
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
                tableBuilder.appendLine("| ${device.label.padEnd(maxDeviceNameLength)} | ${aliasesString.padEnd(maxAliasesLength)} |")
            }

            tableBuilder.appendLine("+" + "-".repeat(maxDeviceNameLength + 2) + "+" + "-".repeat(maxAliasesLength + 2) + "+")

            tableBuilder.appendLine("```")
            return tableBuilder.toString()
        }

    override fun toString(): String {
        return devices.size.toString()
    }

    private fun initializeCache(devices: List<Device>): List<String> {
        val warnings: MutableList<String> = ArrayList()
        val nameMatrix = DeviceAbbreviator()
        for (device in devices) {
            val fullName = device.label.lowercase()
            addToCache(fullName, device)

            // Add name without "Light" or "Lights" if applicable
            val nameWithoutLights = removeLightSuffix(fullName)
            if (nameWithoutLights != fullName) {
                addToCache(nameWithoutLights, device)
            }

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

    private fun addToCache(key: String, device: Device):List<String> {
        var warnings: MutableList<String> = ArrayList()
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

    fun findDevice(name: String, command: String): Result<Device> {
        val normalizedQuery = name.lowercase()
        val device = deviceCache[normalizedQuery]

        if (device != null) {
            return if (device.supportedOps.contains(command)) {
                Result.success(device)
            } else {
                Result.failure(IllegalArgumentException("Command '$command' is not supported by device '${device.label}'"))
            }
        }

        return Result.failure(Exception("No device found for query: $name"))
    }
}
