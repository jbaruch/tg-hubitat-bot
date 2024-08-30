package jbaru.ch.telegram.hubitat.factories

import jbaru.ch.telegram.hubitat.DeviceRepository
import jbaru.ch.telegram.hubitat.DeviceRepositoryImpl
import jbaru.ch.telegram.hubitat.mappers.*

interface DeviceRepositoryFactory : Factory<String, DeviceRepository>

class DeviceRepositoryFactoryImpl(
    private val jsonToListOfDevicesMapper: JsonToListOfDevicesMapper = JsonToListOfDevicesMapperImpl(),
    private val deviceListToMapMapper: DeviceListToMapMapper = DeviceListToMapMapperImpl(
        CamelCaseAbbreviationMapperImpl(),
        LightSuffixRemovalMapperImpl(),
    ),
) : DeviceRepositoryFactory {

    override fun create(param: String): DeviceRepository {
        val devicesList = jsonToListOfDevicesMapper.map(param)
        val devicesMap = deviceListToMapMapper.map(devicesList)
        return DeviceRepositoryImpl(devicesMap)
    }
}
