package jbaru.ch.telegram.hubitat

import jbaru.ch.telegram.hubitat.model.Device
import kotlinx.serialization.json.Json

class DeviceManager(deviceListJson: String) {

    private val deviceCache: MutableMap<String, Device> = mutableMapOf()

    init {
        val format = Json { ignoreUnknownKeys = true }
        val devices = format.decodeFromString<List<Device>>(deviceListJson)
        initializeCache(devices)
    }

    private fun initializeCache(devices: List<Device>) {
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
            result.getOrElse { println("WARNING Unable to add $fullName due to ${result.exceptionOrNull()}") }
        }
        nameMatrix.abbreviate()
        for (device in devices) {
            val fullName = device.label.lowercase()
            val abbreviation = nameMatrix.getAbbreviation(fullName)
            if (abbreviation.isSuccess) addToCache(abbreviation.getOrThrow(), device)
            else println("WARNING Device name was not abbreviated: $fullName")
        }
    }

    private fun addToCache(key: String, device: Device) {
        if (deviceCache.containsKey(key)) {
            println("WARNING Duplicate key found in cache: $key")
        }
        deviceCache[key] = device
    }

    private fun removeLightSuffix(name: String): String {
        return name.replace(Regex(" lights?$", RegexOption.IGNORE_CASE), "").trim()
    }

    fun findDevice(name: String, command: String): Result<Device> {
        val normalizedQuery = name.lowercase()
        val device = deviceCache[normalizedQuery]

        if (device != null) {
            return if (device.SUPPORTED_OPS.contains(command)) {
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
            shortenAbbreviation()
            updateMinAbbrevLength()
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

    private fun updateMinAbbrevLength() {
        for ((i, abbreviation) in abbreviations.withIndex()) {
            previousAbbreviationLength[i] = abbreviation.length
        }
    }

    private fun shortenAbbreviation() {
        val uniqueAbbreviations = mutableSetOf<String>()
        val abbreviationCache = mutableMapOf<String, String>()
        uniqueAbbreviations.addAll(abbreviations)

        for ((i, abbreviation) in this.abbreviations.withIndex()) {
            val cachedAbbreviation = abbreviationCache[abbreviation]
            if (cachedAbbreviation != null) {
                this.abbreviations[i] = cachedAbbreviation
                continue
            }
            uniqueAbbreviations.remove(abbreviation)
            var newAbbreviation = abbreviation
            for (stringEndIdx in previousAbbreviationLength[i] + 1..abbreviation.length) {
                newAbbreviation = abbreviation.substring(0, stringEndIdx)
                if (!isColliding(newAbbreviation, uniqueAbbreviations)) break
            }
            this.abbreviations[i] = newAbbreviation
            uniqueAbbreviations.add(abbreviation)
        }
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