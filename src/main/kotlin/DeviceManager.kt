package jbaru.ch.telegram.hubitat

import jbaru.ch.telegram.hubitat.model.Device
import kotlinx.serialization.json.Json

class DeviceManager(deviceListJson: String) {

    private val devices: List<Device>
    private val nameCache: MutableMap<String, Int> = mutableMapOf()
    private val abbreviationCache: MutableMap<String, Int> = mutableMapOf()

    init {
        val format = Json { ignoreUnknownKeys = true }
        devices = format.decodeFromString(deviceListJson)
        initializeNameCache()
        initializeAbbreviationCache()
    }

    private fun initializeNameCache() {
        for (device in devices) {
            val fullName = device.label.lowercase()
            nameCache[fullName] = device.id
            println("Caching full name '$fullName' for device ID: ${device.id}")

            // Cache the name without "Light" or "Lights" if they appear at the end
            val nameWithoutLights = removeLightSuffix(fullName)
            if (nameWithoutLights != fullName) {
                nameCache[nameWithoutLights] = device.id
                println("Caching name without 'Light/Lights' '$nameWithoutLights' for device ID: ${device.id}")
            }
        }
    }

    private fun initializeAbbreviationCache() {
        for (device in devices) {
            val abbreviation = createCamelCaseAbbreviation(device.label)
            abbreviationCache[abbreviation] = device.id
            println("Caching abbreviation '$abbreviation' for device ID: ${device.id}")
        }
    }

    private fun createCamelCaseAbbreviation(label: String): String {
        return label.split(" ")
            .joinToString("") { word ->
                word.firstOrNull()?.lowercaseChar()?.toString() ?: ""
            }
    }

    private fun removeLightSuffix(name: String): String {
        return name.replace(Regex(" lights?$", RegexOption.IGNORE_CASE), "").trim()
    }

    fun findDeviceId(query: String): Result<Int> {
        val normalizedQuery = query.lowercase()

        println("Searching for device: $query (Normalized: $normalizedQuery)")

        // Check full name cache first
        nameCache[normalizedQuery]?.let {
            println("Found in name cache: $it")
            return Result.success(it)
        }

        // Check abbreviation cache if not found in full names
        abbreviationCache[normalizedQuery]?.let {
            println("Found in abbreviation cache: $it")
            return Result.success(it)
        }

        return Result.failure(Exception("No device found for query: $query"))
    }
}