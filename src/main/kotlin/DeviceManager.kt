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

            val result = nameMatrix.addName(fullName)
            result.getOrElse {
                val message = "WARNING Unable to add $fullName due to ${result.exceptionOrNull()}"
                warnings.add(message)
                println(message)
            }
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

class DeviceAbbreviator {
    private val tokenizedNames: MutableList<MutableList<String>> = mutableListOf()
    private val names: MutableMap<String, Int> = mutableMapOf()
    private val abbreviations: MutableList<String> = mutableListOf()
    private val previousAbbreviationLength: MutableList<Int> = mutableListOf()
    private var closedForAdditions = false

    fun addName(name: String): Result<Unit> {
        if (closedForAdditions) return Result.failure(IllegalStateException("Cannot add names after executing abbreviate"))
        if (name in this.names) return Result.failure(IllegalArgumentException("Name is already present: $name"))

        val tokenizedName = name.split(' ').toMutableList()
        names[name] = this.tokenizedNames.size
        this.tokenizedNames.add(tokenizedName)
        this.abbreviations.add("")
        this.previousAbbreviationLength.add(0)

        return Result.success(Unit)
    }

    fun abbreviate() {
        while (appendNextTokensToAbbreviations()) {
            shortenAbbreviations()
            updatePrevAbbrevLength()
        }
        closedForAdditions = true
    }

    fun getAbbreviation(fullName: String): Result<String> {
        val i =
            this.names[fullName] ?: return Result.failure(IllegalArgumentException("$fullName was never abbreviated"))
        return Result.success(this.abbreviations[i])
    }

    private fun appendNextTokensToAbbreviations(): Boolean {
        var appended = false
        for ((i, tokenizedName) in tokenizedNames.withIndex()) {
            if (tokenizedName.isNotEmpty()) {
                val nextToken = tokenizedName.removeFirst()
                abbreviations[i] += nextToken
                appended = true
            }
        }
        return appended
    }

    private fun updatePrevAbbrevLength() {
        for ((i, abbreviation) in abbreviations.withIndex()) {
            previousAbbreviationLength[i] = abbreviation.length
        }
    }

    private fun shortenAbbreviations() {
        val uniqueAbbreviations = mutableSetOf<String>()
        val abbreviationCache = mutableMapOf<String, String>()
        uniqueAbbreviations.addAll(abbreviations)

        for ((i, abbreviation) in this.abbreviations.withIndex()) {
            uniqueAbbreviations.remove(abbreviation)
            val cachedAbbreviation = abbreviationCache.computeIfAbsent(abbreviation){
                findCollisionFreeAbbreviation(abbreviation, uniqueAbbreviations, previousAbbreviationLength[i] + 1)
            }
            this.abbreviations[i] = cachedAbbreviation
            uniqueAbbreviations.add(abbreviation)
        }
    }

    private fun findCollisionFreeAbbreviation(abbrev: String, otherAbbreviations: Set<String>, minLength: Int): String {
        var newAbbreviation = abbrev
        for (stringEndIdx in minLength..abbrev.length) {
            newAbbreviation = abbrev.substring(0, stringEndIdx)
            if (!isColliding(newAbbreviation, otherAbbreviations)) break
        }
        return newAbbreviation
    }

    private fun isColliding(abbrev: String, otherTokens: Iterable<String>): Boolean {
        for (otherToken in otherTokens) {
            if (isValidAbbrev(abbrev, otherToken)) return true
        }
        return false
    }

    private fun isValidAbbrev(abbrev: String, word: String): Boolean {
        if (abbrev.length > word.length) return false
        for ((i, char) in abbrev.withIndex()) {
            if (char != word[i]) return false
        }
        return true
    }

}