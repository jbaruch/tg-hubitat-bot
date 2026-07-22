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
    private val unabbreviatable: MutableSet<String> = mutableSetOf()
    private var isAbbreviated = false

    fun addName(name: String) {
        if (isAbbreviated) throw IllegalStateException("Cannot add more devices after abbreviate was called")
        deviceAbbreviationMap[name.lowercase()] = DeviceAbbreviation(name)
    }

    fun abbreviate() {
        var collisions = getShortestCollidingAbbreviations()
        while (collisions.isNotEmpty()) {
            val before = collisions.map { it.getAbbreviation() }
            incrementAbbreviations(collisions)
            val after = collisions.map { it.getAbbreviation() }
            if (before == after) {
                // Every token in the colliding group is fully expanded and the
                // abbreviations are still identical (e.g. "a b" vs "ab" - both
                // can only ever reach "ab"). These names can never diverge, so
                // iterating further would loop forever and hang the whole boot.
                // Drop them from the abbreviation map and remember why:
                // getAbbreviation() then fails with a dedicated message,
                // DeviceManager emits its "Device name was not abbreviated"
                // warning, and the devices stay reachable by full name.
                val stuck = collisions.toSet()
                deviceAbbreviationMap.entries
                    .filter { it.value in stuck }
                    .forEach { unabbreviatable.add(it.key) }
                deviceAbbreviationMap.entries.removeIf { it.value in stuck }
            }
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
        // Normalize like addName() stores: lookups must be case-insensitive.
        val key = fullName.lowercase()
        if (key in unabbreviatable) {
            return Result.failure(
                IllegalStateException(
                    "$fullName cannot be abbreviated: its abbreviation collides irreconcilably with another device name"
                )
            )
        }
        val abbreviation = this.deviceAbbreviationMap[key]?.getAbbreviation() ?: return Result.failure(
            IllegalArgumentException("$fullName name was not added to this DeviceAbbreviator instance")
        )
        return Result.success(abbreviation)
    }
}