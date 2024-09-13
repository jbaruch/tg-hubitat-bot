package jbaru.ch.telegram.hubitat


class DeviceAbbreviator {
    private class DeviceAbbreviation(name: String) {
        private val tokens: List<String> = name.lowercase().split(' ')
        private val tokenAbbreviations: MutableList<StringBuilder> = mutableListOf()

        val numberOfTokens: Int
            get() {
                return tokenAbbreviations.size
            }

        init {
            for (token in tokens) {
                tokenAbbreviations.add(StringBuilder().append(token.first()))
            }
        }

        fun getTokenAt(i: Int): String {
            return tokens[i]
        }

        fun expandTokenAbbreviationAt(i: Int) {
            val token = tokens[i]
            val abbreviation = tokenAbbreviations[i]
            if (abbreviation.length == token.length) return
            abbreviation.append(token[abbreviation.length])
        }

        fun getAbbreviation(): String {
            return tokenAbbreviations.joinToString("")
        }
    }

    private val deviceAbbreviationMap: MutableMap<String, DeviceAbbreviation> = mutableMapOf()
    private var isAbbreviated = false

    fun addName(name: String) {
        if (isAbbreviated) throw IllegalStateException("Cannot add more devices after abbreviate was called")
        deviceAbbreviationMap[name.lowercase()] = DeviceAbbreviation(name)
    }

    fun abbreviate() {
        var collisions = getShortestCollidingAbbreviations()
        while (collisions.isNotEmpty()) {
            incrementAbbreviations(collisions)
            collisions = getShortestCollidingAbbreviations()
        }
        isAbbreviated = true
    }

    private fun incrementAbbreviations(collision: List<DeviceAbbreviation>) {
        val maxTokens = collision.maxOf { it.numberOfTokens }
        for (i in 0..<maxTokens) {
            val uniqueTokens: MutableSet<String> = mutableSetOf()
            for (deviceAbbreviation in collision) {
                if (i < deviceAbbreviation.numberOfTokens) uniqueTokens.add(deviceAbbreviation.getTokenAt(i))
                else uniqueTokens.add("")
            }
            if (uniqueTokens.size > 1) {
                for (deviceAbbreviation in collision) {
                    if (i < deviceAbbreviation.numberOfTokens) deviceAbbreviation.expandTokenAbbreviationAt(i)
                }
                break
            }
        }
    }

    private fun getShortestCollidingAbbreviations(): List<DeviceAbbreviation> {
        var res: List<DeviceAbbreviation> = listOf()
        val abbreviationMap: MutableMap<String, MutableList<DeviceAbbreviation>> = mutableMapOf()

        for ((_, deviceAbbreviation) in this.deviceAbbreviationMap) {
            val currentAbbreviation = deviceAbbreviation.getAbbreviation()
            abbreviationMap.putIfAbsent(currentAbbreviation, mutableListOf())
            abbreviationMap[currentAbbreviation]!!.add(deviceAbbreviation) // Assertion is necessary because it indicates a bag on previous line.
        }
        for ((_, collidingAbbreviation) in abbreviationMap) {
            if ((res.isEmpty() || collidingAbbreviation.size < res.size) && collidingAbbreviation.size > 1) {
                res = collidingAbbreviation
            }
        }
        return res.toList()
    }

    fun getAbbreviation(fullName: String): Result<String> {
        val abbreviation = this.deviceAbbreviationMap[fullName]?.getAbbreviation() ?: return Result.failure(
            IllegalArgumentException("$fullName name was not added to this DeviceAbbreviator instance")
        )
        return Result.success(abbreviation)
    }
}