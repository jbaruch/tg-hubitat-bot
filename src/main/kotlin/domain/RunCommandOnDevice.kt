package jbaru.ch.telegram.hubitat.domain

import io.ktor.client.*
import io.ktor.client.request.*
import jbaru.ch.telegram.hubitat.DeviceRepository
import jbaru.ch.telegram.hubitat.MAKER_API_APP_ID
import jbaru.ch.telegram.hubitat.MAKER_API_TOKEN

interface RunCommandOnDevice : Command<RunCommandOnDevice.Param, String> {
    data class Param(
        val deviceRepository: DeviceRepository,
        val client: HttpClient,
        val command: String,
        val device: String,
    )
}

class RunCommandOnDeviceImpl : RunCommandOnDevice {
    override suspend fun invoke(param: RunCommandOnDevice.Param): String {
        return param.deviceRepository.findDevice(param.device, param.command).fold(
            onSuccess = { device ->
                runCatching {
                    param.client.get("http://hubitat.local/apps/api/$MAKER_API_APP_ID/devices/${device.id}/${param.command}") {
                        parameter("access_token", MAKER_API_TOKEN)
                    }.status.description
                }.onFailure {
                    it.message.toString()
                }.getOrDefault("runCommandOnDevice total failure")
            },
            onFailure = {
                it.printStackTrace()
                it.message.toString()
            }
        )
    }
}
