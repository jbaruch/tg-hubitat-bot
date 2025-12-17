package jbaru.ch.telegram.hubitat

import io.ktor.http.HttpStatusCode
import jbaru.ch.telegram.hubitat.model.Device
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.delay

data class HubVersionInfo(
    val hubLabel: String,
    val currentVersion: String,
    val availableVersion: String,
    val needsUpdate: Boolean = currentVersion != availableVersion
)

data class UpdateProgress(
    val totalHubs: Int,
    val updatedHubs: Set<String>,
    val failedHubs: Map<String, String>,
    val inProgressHubs: Set<String>
) {
    val isComplete: Boolean
        get() = updatedHubs.size + failedHubs.size == totalHubs
    
    val successCount: Int
        get() = updatedHubs.size
    
    val failureCount: Int
        get() = failedHubs.size
}

object HubOperations {
    
    suspend fun initializeHubs(
        deviceManager: DeviceManager,
        networkClient: NetworkClient,
        hubIp: String,
        makerApiAppId: String,
        makerApiToken: String
    ): List<Device.Hub> {
        val hubs = deviceManager.findDevicesByType(Device.Hub::class.java)
        for (hub in hubs) {
            val json: Map<String, JsonElement> =
                Json.parseToJsonElement(
                    networkClient.getBody(
                        "http://${hubIp}/apps/api/${makerApiAppId}/devices/${hub.id}",
                        mapOf("access_token" to makerApiToken)
                    )
                ).jsonObject

            val ip = (json["attributes"] as JsonArray).find {
                it.jsonObject["name"]!!.jsonPrimitive.content == "localIP"
            }!!.jsonObject["currentValue"]!!.jsonPrimitive.content
            hub.ip = ip
            hub.managementToken = networkClient.getBody("http://${ip}/hub/advanced/getManagementToken")
        }
        return hubs
    }
    
    suspend fun getHubVersions(
        hub: Device.Hub,
        networkClient: NetworkClient,
        hubIp: String,
        makerApiAppId: String,
        makerApiToken: String
    ): Pair<String, String> {
        // Query the Hub Information Driver device through Maker API
        val endpoint = "http://${hubIp}/apps/api/${makerApiAppId}/devices/${hub.id}"
        val responseBody = networkClient.getBody(endpoint, mapOf("access_token" to makerApiToken))
        
        try {
            val json = Json.parseToJsonElement(responseBody).jsonObject
            val attributes = json["attributes"] as? JsonArray
            
            // Hub Information Driver v3 exposes these attributes
            val currentVersion = attributes?.find {
                it.jsonObject["name"]?.jsonPrimitive?.content == "firmwareVersionString"
            }?.jsonObject?.get("currentValue")?.jsonPrimitive?.content ?: ""
            
            val availableVersion = attributes?.find {
                it.jsonObject["name"]?.jsonPrimitive?.content == "hubUpdateVersion"
            }?.jsonObject?.get("currentValue")?.jsonPrimitive?.content ?: ""
            
            return Pair(currentVersion, availableVersion)
        } catch (e: Exception) {
            val preview = if (responseBody.length > 200) responseBody.take(200) + "..." else responseBody
            throw Exception(
                "Failed to parse hub info response for hub '${hub.label}' from endpoint '$endpoint': ${e.message}\n" +
                "Response preview: $preview",
                e
            )
        }
    }
    
    suspend fun updateHubs(
        hubs: List<Device.Hub>,
        networkClient: NetworkClient
    ): Result<String> {
        val statusMap = mutableMapOf<String, HttpStatusCode>()

        for (hub in hubs) {
            try {
                val response = networkClient.get(
                    "http://${hub.ip}/management/firmwareUpdate",
                    mapOf("token" to hub.managementToken)
                )
                statusMap[hub.label] = response.status
            } catch (_: Exception) {
                statusMap[hub.label] = HttpStatusCode.InternalServerError
            }
        }
        val failures = statusMap.filterValues { it != HttpStatusCode.OK }
        return if (failures.isEmpty()) {
            Result.success("All hub updates initialized successfully.")
        } else {
            val failureMessages = failures.entries.joinToString("\n") { (name, status) ->
                "Failed to update hub $name Status: $status"
            }
            val successMessages = statusMap.filterValues { it == HttpStatusCode.OK }
                .entries.joinToString("\n") { (name, _) ->
                    "Successfully issued update request to hub $name"
                }
            Result.failure(Exception("$failureMessages\n$successMessages"))
        }
    }
    
    suspend fun updateHubsWithPolling(
        hubs: List<Device.Hub>,
        networkClient: NetworkClient,
        hubIp: String,
        makerApiAppId: String,
        makerApiToken: String,
        maxAttempts: Int = 20,
        delayMillis: Long = 30000,
        progressCallback: suspend (String) -> Unit
    ): Result<String> {
        // Check current and available versions for all hubs
        val versionInfo = mutableMapOf<String, HubVersionInfo>()
        for (hub in hubs) {
            try {
                val (current, available) = getHubVersions(hub, networkClient, hubIp, makerApiAppId, makerApiToken)
                versionInfo[hub.label] = HubVersionInfo(hub.label, current, available)
            } catch (e: Exception) {
                return Result.failure(Exception("Failed to get version info for hub ${hub.label}: ${e.message}"))
            }
        }
        
        // Skip hubs that are already up to date
        val hubsNeedingUpdate = versionInfo.filter { it.value.needsUpdate }
        if (hubsNeedingUpdate.isEmpty()) {
            progressCallback("All hubs are already up to date")
            return Result.success("All hubs are already up to date")
        }
        
        progressCallback("Hubs needing update: ${hubsNeedingUpdate.keys.joinToString(", ")}")
        
        // Initiate updates for hubs that need them
        val updateProgress = UpdateProgress(
            totalHubs = hubsNeedingUpdate.size,
            updatedHubs = emptySet(),
            failedHubs = emptyMap(),
            inProgressHubs = hubsNeedingUpdate.keys.toSet()
        )
        
        for (hub in hubs.filter { hubsNeedingUpdate.containsKey(it.label) }) {
            try {
                val response = networkClient.get(
                    "http://${hub.ip}/management/firmwareUpdate",
                    mapOf("token" to hub.managementToken)
                )
                if (response.status != HttpStatusCode.OK) {
                    return Result.failure(Exception("Failed to initiate update for hub ${hub.label}: ${response.status}"))
                }
                progressCallback("Update initiated for hub ${hub.label}")
            } catch (e: Exception) {
                return Result.failure(Exception("Failed to initiate update for hub ${hub.label}: ${e.message}"))
            }
        }
        
        // Poll hub versions periodically
        var currentProgress = updateProgress
        var attempts = 0
        
        while (!currentProgress.isComplete && attempts < maxAttempts) {
            delay(delayMillis)
            attempts++
            
            val updatedHubs = mutableSetOf<String>()
            val failedHubs = mutableMapOf<String, String>()
            
            for (hubLabel in currentProgress.inProgressHubs) {
                val hub = hubs.find { it.label == hubLabel } ?: continue
                try {
                    val (newCurrent, _) = getHubVersions(hub, networkClient, hubIp, makerApiAppId, makerApiToken)
                    val originalVersion = versionInfo[hubLabel]!!.currentVersion
                    val targetVersion = versionInfo[hubLabel]!!.availableVersion
                    
                    if (newCurrent != originalVersion) {
                        updatedHubs.add(hubLabel)
                        progressCallback("Hub $hubLabel updated from $originalVersion to $newCurrent")
                    }
                } catch (e: Exception) {
                    failedHubs[hubLabel] = e.message ?: "Unknown error"
                }
            }
            
            currentProgress = UpdateProgress(
                totalHubs = currentProgress.totalHubs,
                updatedHubs = currentProgress.updatedHubs + updatedHubs,
                failedHubs = currentProgress.failedHubs + failedHubs,
                inProgressHubs = currentProgress.inProgressHubs - updatedHubs - failedHubs.keys
            )
            
            if (!currentProgress.isComplete) {
                progressCallback("Progress: ${currentProgress.successCount}/${currentProgress.totalHubs} updated, ${currentProgress.failureCount} failed, ${currentProgress.inProgressHubs.size} in progress")
            }
        }
        
        // Handle timeout scenario
        if (!currentProgress.isComplete) {
            val timeoutHubs = currentProgress.inProgressHubs.joinToString(", ")
            progressCallback("Timeout: The following hubs did not complete update: $timeoutHubs")
            return Result.failure(Exception("Update timeout. Hubs that did not complete: $timeoutHubs"))
        }
        
        // Report final results
        val successMessage = if (currentProgress.successCount > 0) {
            "Successfully updated ${currentProgress.successCount} hub(s): ${currentProgress.updatedHubs.joinToString(", ")}"
        } else ""
        
        val failureMessage = if (currentProgress.failureCount > 0) {
            "Failed to update ${currentProgress.failureCount} hub(s): ${currentProgress.failedHubs.entries.joinToString(", ") { "${it.key} (${it.value})" }}"
        } else ""
        
        return if (currentProgress.failureCount == 0) {
            Result.success(successMessage)
        } else {
            Result.failure(Exception("$failureMessage\n$successMessage"))
        }
    }
}
