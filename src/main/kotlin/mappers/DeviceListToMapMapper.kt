package jbaru.ch.telegram.hubitat.mappers

import jbaru.ch.telegram.hubitat.models.Device

interface DeviceListToMapMapper : Mapper<List<Device>, MutableMap<String, Device>>

class DeviceListToMapMapperImpl(
    private val camelCaseAbbreviationMapperImpl: CamelCaseAbbreviationMapper,
    private val lightSuffixRemovalMapperImpl: LightSuffixRemovalMapper,
) : DeviceListToMapMapper {

    override fun map(input: List<Device>): MutableMap<String, Device> {
        val map = mutableMapOf<String, Device>()
        input.forEach { device ->
            val fullName = device.label.lowercase()
            map[fullName] = device

            // Add name without "Light" or "Lights" if applicable
            val nameWithoutLights = lightSuffixRemovalMapperImpl.map(fullName)
            if (nameWithoutLights != fullName) {
                map[nameWithoutLights] = device
            }

            // Add abbreviation of the full name
            val abbreviation = camelCaseAbbreviationMapperImpl.map(fullName)
            map[abbreviation] = device
        }

        return map
    }
}
