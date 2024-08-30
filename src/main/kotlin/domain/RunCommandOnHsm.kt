package jbaru.ch.telegram.hubitat.domain

import io.ktor.client.*
import io.ktor.client.request.*
import jbaru.ch.telegram.hubitat.MAKER_API_APP_ID
import jbaru.ch.telegram.hubitat.MAKER_API_TOKEN

interface RunCommandOnHsm : Command<RunCommandOnHsm.Param, String> {
    data class Param(val client: HttpClient, val command: String)
}

class RunCommandOnHsmImpl : RunCommandOnHsm {
    override suspend fun invoke(param: RunCommandOnHsm.Param): String {
        return param.client
            .get("http://hubitat.local/apps/api/$MAKER_API_APP_ID/hsm/${param.command}") {
                parameter("access_token", MAKER_API_TOKEN)
            }.status
            .description
    }
}
