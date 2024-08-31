package jbaru.ch.telegram.hubitat

import jbaru.ch.telegram.hubitat.model.Device
import kotlinx.serialization.json.Json

class DeviceManager(deviceListJson: String) {

    private val deviceCache: MutableMap<String, Device> = mutableMapOf()
    private lateinit var devices: List<Device>

    init {
        refreshDevices(deviceListJson)
    }

    private fun initializeCache(devices: List<Device>) {
        for (device in devices) {
            val fullName = device.label.lowercase()
            addToCache(fullName, device)

            // Add name without "Light" or "Lights" if applicable
            val nameWithoutLights = removeLightSuffix(fullName)
            if (nameWithoutLights != fullName) {
                addToCache(nameWithoutLights, device)
            }

            // Add abbreviation of the full name
            val abbreviation = createCamelCaseAbbreviation(fullName)
            addToCache(abbreviation, device)
        }
    }

    private fun addToCache(key: String, device: Device) {
        if (deviceCache.containsKey(key)) {
            //TODO plug disambiguation logic
            println("WARNING Duplicate key found in cache: $key")
        }
        deviceCache[key] = device
    }

    private fun createCamelCaseAbbreviation(name: String): String {
        return name.split(" ")
            .filter { it.isNotEmpty() }
            .joinToString("") { it.first().lowercaseChar().toString() }
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

    fun <T : Device> findDevicesByType(type: Class<T>): List<T> {
        return devices.filterIsInstance(type)
    }

    override fun toString(): String {
        return devices.size.toString()
    }

    fun refreshDevices(deviceListJson: String): Int {
        val format = Json { ignoreUnknownKeys = true }
        devices = format.decodeFromString<List<Device>>(deviceListJson)
        deviceCache.clear()
        initializeCache(devices)
        return devices.size
    }
}