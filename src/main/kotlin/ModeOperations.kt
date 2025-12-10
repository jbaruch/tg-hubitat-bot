package jbaru.ch.telegram.hubitat

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
        return try {
            val modesJson = networkClient.getBody(
                "http://${hubIp}/apps/api/${makerApiAppId}/modes",
                mapOf("access_token" to makerApiToken)
            )
            val modes = json.decodeFromString<List<ModeInfo>>(modesJson)
            Result.success(modes)
        } catch (e: Exception) {
            Result.failure(e)
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
        return try {
            // First get all modes to find the ID
            val modesResult = getAllModes(networkClient, makerApiAppId, makerApiToken, hubIp)
            if (modesResult.isFailure) {
                return Result.failure(modesResult.exceptionOrNull()!!)
            }
            
            val modes = modesResult.getOrNull()!!
            val modeId = findModeIdByName(modes, modeName)
                ?: return Result.failure(IllegalArgumentException("Mode not found: $modeName"))
            
            // Call GET endpoint with the mode ID (Maker API uses GET for all commands)
            val response = networkClient.get(
                "http://${hubIp}/apps/api/${makerApiAppId}/modes/${modeId}",
                mapOf("access_token" to makerApiToken)
            )
            
            if (response.status.value in 200..299) {
                Result.success("Mode changed to $modeName")
            } else {
                Result.failure(Exception("Failed to set mode: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun findModeIdByName(
        modes: List<ModeInfo>,
        modeName: String
    ): Int? {
        return modes.find { it.name.equals(modeName, ignoreCase = true) }?.id
    }
}
