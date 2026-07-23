package jbaru.ch.telegram.hubitat
import io.ktor.http.isSuccess

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ModeInfo(
    val id: Int,
    val name: String,
    val active: Boolean
)

object ModeOperations {
    private val json = Json { ignoreUnknownKeys = true }
    
    suspend fun getAllModes(
        networkClient: NetworkClient,
        makerApiAppId: String,
        makerApiToken: String,
        hubIp: String
    ): Result<List<ModeInfo>> {
        return onExpectedFailureSuspend(
            // Handlers print the failure message into chat; network exception
            // text can carry the request URL with access_token - redact.
            onFailure = { Result.failure(IllegalStateException(KtorNetworkClient.redactSecrets(it.message), it)) }
        ) {
            val modesJson = networkClient.getBody(
                "http://${hubIp}/apps/api/${makerApiAppId}/modes",
                mapOf("access_token" to makerApiToken)
            )
            val modes = json.decodeFromString<List<ModeInfo>>(modesJson)
            Result.success(modes)
        }
    }
    
    suspend fun getCurrentMode(
        networkClient: NetworkClient,
        makerApiAppId: String,
        makerApiToken: String,
        hubIp: String
    ): Result<ModeInfo> {
        return getAllModes(networkClient, makerApiAppId, makerApiToken, hubIp)
            .mapCatching { modes ->
                modes.find { it.active }
                    ?: throw IllegalStateException("No active mode found")
            }
    }
    
    suspend fun setMode(
        networkClient: NetworkClient,
        makerApiAppId: String,
        makerApiToken: String,
        hubIp: String,
        modeName: String
    ): Result<String> {
        return onExpectedFailureSuspend(
            // Handlers print the failure message into chat; network exception
            // text can carry the request URL with access_token - redact.
            onFailure = { Result.failure(IllegalStateException(KtorNetworkClient.redactSecrets(it.message), it)) }
        ) {
            // First get all modes to find the ID (Maker API uses GET for all commands)
            val modesResult = getAllModes(networkClient, makerApiAppId, makerApiToken, hubIp)
            val modeId = modesResult.getOrNull()?.let { findModeIdByName(it, modeName) }
            when {
                modesResult.isFailure -> Result.failure(modesResult.exceptionOrNull()!!)
                modeId == null -> Result.failure(IllegalArgumentException("Mode not found: $modeName"))
                else -> {
                    val response = networkClient.get(
                        "http://${hubIp}/apps/api/${makerApiAppId}/modes/${modeId}",
                        mapOf("access_token" to makerApiToken)
                    )
                    if (response.status.isSuccess()) {
                        Result.success("Mode changed to $modeName")
                    } else {
                        Result.failure(IllegalStateException("Failed to set mode: ${response.status}"))
                    }
                }
            }
        }
    }
    
    private fun findModeIdByName(
        modes: List<ModeInfo>,
        modeName: String
    ): Int? {
        return modes.find { it.name.equals(modeName, ignoreCase = true) }?.id
    }
}
