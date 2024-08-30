package jbaru.ch.telegram.hubitat.domain

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import jbaru.ch.telegram.hubitat.model.Device

fun interface UpdateHubsCommand : Command<UpdateHubsCommand.Param, Result<String>> {
    data class Param(
        val hubs: List<Device.Hub>,
        val client: HttpClient,
    )
}

class UpdateHubsCommandImpl : UpdateHubsCommand {
    override suspend fun invoke(param: UpdateHubsCommand.Param): Result<String> {
        val wins = mutableMapOf<String, HttpStatusCode>()
        val failures = mutableMapOf<String, HttpStatusCode>()

        param.hubs.forEach { hub ->
            runCatching {
                val response = param.client.get("http://${hub.ip}/management/firmwareUpdate") {
                    parameter("token", hub.managementToken)
                }
                wins[hub.label] = response.status
            }.onFailure {
                failures[hub.label] = HttpStatusCode.InternalServerError
            }
        }

        return when {
            failures.isEmpty() -> {
                Result.success("All hub updates initialized successfully.")
            }

            else -> {
                val failureMessages = failures.entries.joinToString("\n") { (name, status) ->
                    "Failed to update hub $name Status: $status"
                }
                val successMessages = wins.entries.joinToString("\n") { (name, status) ->
                    "Failed to update hub $name Status: $status"
                }
                Result.failure(
                    Exception(
                        "$failureMessages\n$successMessages"
                    )
                )
            }
        }
    }
}
