package jbaru.ch.telegram.hubitat.mappers

import jbaru.ch.telegram.hubitat.models.Device
import kotlinx.serialization.json.Json

fun interface JsonToListOfDevicesMapper : Mapper<String, List<Device>>

class JsonToListOfDevicesMapperImpl : JsonToListOfDevicesMapper {

    override fun map(input: String): List<Device> {
        val format = Json { ignoreUnknownKeys = true }
        val devices = format.decodeFromString<List<Device>>(input)
        return devices
    }
}

