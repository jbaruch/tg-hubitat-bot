package jbaru.ch.telegram.hubitat.domain

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import jbaru.ch.telegram.hubitat.DeviceRepository
import jbaru.ch.telegram.hubitat.MAKER_API_APP_ID
import jbaru.ch.telegram.hubitat.MAKER_API_TOKEN
import jbaru.ch.telegram.hubitat.model.Device
import kotlinx.serialization.json.*

fun interface InitHubsCommand : Command<InitHubsCommand.Param, List<Device.Hub>> {
    data class Param(
        val client: HttpClient,
        val deviceRepository: DeviceRepository,
    )
}

class InitHubsCommandImpl : InitHubsCommand {
    override suspend fun invoke(param: InitHubsCommand.Param): List<Device.Hub> {
        val hubs = param.deviceRepository
            .findDevicesByType(Device.Hub::class.java)
            .map { hub ->
                val json: Map<String, JsonElement> =
                    Json.parseToJsonElement(
                        param.client.get("http://hubitat.local/apps/api/$MAKER_API_APP_ID/devices/${hub.id}") {
                            parameter("access_token", MAKER_API_TOKEN)
                        }.body<String>()
                    ).jsonObject

                runCatching {
                    val ip = (json["attributes"] as JsonArray).find {
                        it.jsonObject["name"]!!.jsonPrimitive.content == "localIP"
                    }!!.jsonObject["currentValue"]!!.jsonPrimitive.content

                    hub.ip = ip
                    hub.managementToken = param.client.get("http://${ip}/hub/advanced/getManagementToken").body()
                }.onFailure {
                    it.printStackTrace()
                }

                hub
            }

        return hubs
    }
}
