package jbaru.ch.telegram.hubitat

import jbaru.ch.telegram.hubitat.model.Device

interface DeviceRepository {
    fun findDevice(name: String, command: String): Result<Device>
    fun <T : Device> findDevicesByType(type: Class<T>): List<T>
}

class DeviceRepositoryImpl(
    private val deviceCache: MutableMap<String, Device> = mutableMapOf(),
) : DeviceRepository {

    override fun findDevice(name: String, command: String): Result<Device> {
        val device = deviceCache[name.lowercase()]

        return when {
            device == null -> {
                Result.failure(Exception("No device found for query: $name"))
            }

            !device.supportedOps.contains(command) -> {
                Result.failure(IllegalArgumentException("Command '$command' is not supported by device '${device.label}'"))
            }

            else -> Result.success(device)
        }
    }

    override fun <T : Device> findDevicesByType(type: Class<T>): List<T> {
        println("devices $deviceCache")
        return deviceCache.map { (_, device) -> device }.distinctBy { it.id }.filterIsInstance(type)
    }

    override fun toString(): String {
        return deviceCache.map { it.value }.distinctBy { it.id }.size.toString()
    }
}