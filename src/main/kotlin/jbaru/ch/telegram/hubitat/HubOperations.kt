package jbaru.ch.telegram.hubitat

import io.ktor.http.HttpStatusCode
import java.util.concurrent.CancellationException
import jbaru.ch.telegram.hubitat.model.Device
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

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

    // Anything shorter cannot be a real hub-info JSON payload - it is a hub
    // mid-restart answering with a stub.
    private const val MIN_PLAUSIBLE_RESPONSE_LENGTH = 10
    private const val PREVIEW_LENGTH = 200

    private val logger = LoggerFactory.getLogger(HubOperations::class.java)

    suspend fun initializeHubs(
        deviceManager: DeviceManager,
        networkClient: NetworkClient,
        hubIp: String,
        makerApiAppId: String,
        makerApiToken: String
    ): List<Device.Hub> {
        // Initialize each hub independently: a hub that doesn't expose a usable
        // localIP (or errors out) is skipped with a warning rather than taking the
        // whole startup down with an NPE. Only fully-initialized hubs are returned,
        // since a hub without ip/managementToken can't be updated or rebooted.
        val hubs = deviceManager.findDevicesByType(Device.Hub::class.java)
        val initialized = mutableListOf<Device.Hub>()
        // Per-hub resilience: a hub that cannot be initialized is skipped with
        // a warning, so one bad hub never blocks startup.
        for (hub in hubs) {
            val ready = onExpectedFailureSuspend(
                onFailure = { e ->
                    val reason = KtorNetworkClient.redactSecrets(e.message?.substringBefore('\n'))
                    logger.warn("Skipping hub '${hub.label}' (id=${hub.id}): $reason")
                    null
                }
            ) {
                initializeHub(hub, networkClient, hubIp, makerApiAppId, makerApiToken)
            }
            if (ready != null) initialized.add(ready)
        }
        return initialized
    }
    
    /** @return the hub with ip/managementToken set, or null when it exposes no usable localIP. */
    private suspend fun initializeHub(
        hub: Device.Hub,
        networkClient: NetworkClient,
        hubIp: String,
        makerApiAppId: String,
        makerApiToken: String
    ): Device.Hub? {
        val json = Json.parseToJsonElement(
            networkClient.getBody(
                "http://${hubIp}/apps/api/${makerApiAppId}/devices/${hub.id}",
                mapOf("access_token" to makerApiToken)
            )
        ).jsonObject

        val ip = (json["attributes"] as? JsonArray)
            ?.firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.content == "localIP" }
            ?.jsonObject?.get("currentValue")?.jsonPrimitive?.content

        if (ip.isNullOrBlank()) {
            logger.warn(
                "Skipping hub '${hub.label}' (id=${hub.id}): no localIP attribute exposed via Maker API"
            )
            return null
        }

        hub.ip = ip
        hub.managementToken = networkClient.getBody("http://${ip}/hub/advanced/getManagementToken")
        return hub
    }

    private fun validateHubInfoResponse(hub: Device.Hub, endpoint: String, responseBody: String) {
        val problem = when {
            responseBody.isBlank() ->
                "Received empty response. This may happen if the hub is busy or restarting."
            responseBody.length < MIN_PLAUSIBLE_RESPONSE_LENGTH ->
                "Received incomplete response (${responseBody.length} chars): '$responseBody'. " +
                    "This may happen if the hub is busy, restarting, or experiencing network issues."
            else -> return
        }
        throw IllegalStateException(
            "Failed to get hub info for hub '${hub.label}' from endpoint '$endpoint': $problem"
        )
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
        
        validateHubInfoResponse(hub, endpoint, responseBody)
        
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
        } catch (e: SerializationException) {
            throw hubInfoParseError(hub, endpoint, responseBody, e)
        } catch (e: IllegalArgumentException) {
            throw hubInfoParseError(hub, endpoint, responseBody, e)
        }
    }

    private fun hubInfoParseError(
        hub: Device.Hub,
        endpoint: String,
        responseBody: String,
        e: Exception
    ): IllegalStateException {
        val preview =
            if (responseBody.length > PREVIEW_LENGTH) responseBody.take(PREVIEW_LENGTH) + "..." else responseBody
        return IllegalStateException(
                "Failed to parse hub info response for hub '${hub.label}' from endpoint '$endpoint': ${e.message}\n" +
                    "Response preview: $preview",
            e
        )
    }
    
    suspend fun updateHubs(
        hubs: List<Device.Hub>,
        networkClient: NetworkClient
    ): Result<String> {
        val statusMap = mutableMapOf<String, HttpStatusCode>()

        // Per-hub resilience: a failed request becomes a per-hub status line.
        for (hub in hubs) {
            onExpectedFailureSuspend(onFailure = { statusMap[hub.label] = HttpStatusCode.InternalServerError }) {
                val response = networkClient.get(
                    "http://${hub.ip}/management/firmwareUpdate",
                    mapOf("token" to hub.managementToken)
                )
                statusMap[hub.label] = response.status
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
        // collectVersionInfo wraps every expected failure in IllegalStateException.
        val versionInfo = try {
            collectVersionInfo(hubs, networkClient, hubIp, makerApiAppId, makerApiToken)
        } catch (e: CancellationException) {
            // CancellationException subclasses IllegalStateException; coroutine
            // cancellation must propagate, not become a Result.
            throw e
        } catch (e: IllegalStateException) {
            return Result.failure(e)
        }

        // Skip hubs that are already up to date
        val hubsNeedingUpdate = versionInfo.filter { it.value.needsUpdate }
        if (hubsNeedingUpdate.isEmpty()) {
            progressCallback("All hubs are already up to date")
            return Result.success("All hubs are already up to date")
        }
        progressCallback("Hubs needing update: ${hubsNeedingUpdate.keys.joinToString(", ")}")

        initiateUpdates(hubs.filter { hubsNeedingUpdate.containsKey(it.label) }, networkClient, progressCallback)
            .onFailure { return Result.failure(it) }

        val finalProgress = pollForCompletion(
            hubs, versionInfo, hubsNeedingUpdate.keys,
            networkClient, hubIp, makerApiAppId, makerApiToken,
            maxAttempts, delayMillis, progressCallback
        )
        return summarize(finalProgress, progressCallback)
    }

    private suspend fun collectVersionInfo(
        hubs: List<Device.Hub>,
        networkClient: NetworkClient,
        hubIp: String,
        makerApiAppId: String,
        makerApiToken: String
    ): Map<String, HubVersionInfo> {
        val versionInfo = mutableMapOf<String, HubVersionInfo>()
        for (hub in hubs) {
            onExpectedFailureSuspend(
                onFailure = { e ->
                    val reason = KtorNetworkClient.redactSecrets(e.message)
                    throw IllegalStateException("Failed to get version info for hub ${hub.label}: $reason", e)
                }
            ) {
                val (current, available) = getHubVersions(hub, networkClient, hubIp, makerApiAppId, makerApiToken)
                versionInfo[hub.label] = HubVersionInfo(hub.label, current, available)
            }
        }
        return versionInfo
    }

    private suspend fun initiateUpdates(
        hubs: List<Device.Hub>,
        networkClient: NetworkClient,
        progressCallback: suspend (String) -> Unit
    ): Result<Unit> {
        for (hub in hubs) {
            val attempt = onExpectedFailureSuspend(
                onFailure = { e ->
                    val reason = KtorNetworkClient.redactSecrets(e.message)
                    Result.failure(
                        IllegalStateException("Failed to initiate update for hub ${hub.label}: $reason", e)
                    )
                }
            ) {
                val response = networkClient.get(
                    "http://${hub.ip}/management/firmwareUpdate",
                    mapOf("token" to hub.managementToken)
                )
                if (response.status != HttpStatusCode.OK) {
                    Result.failure(
                        IllegalStateException("Failed to initiate update for hub ${hub.label}: ${response.status}")
                    )
                } else {
                    progressCallback("Update initiated for hub ${hub.label}")
                    Result.success(Unit)
                }
            }
            if (attempt.isFailure) return attempt
        }
        return Result.success(Unit)
    }

    // A hub is done when its firmware moves off the original version. While a
    // hub reboots to apply the update, the Maker API returns empty/error
    // responses and getHubVersions throws - that is EXPECTED mid-update
    // progress, NOT a failure. Treat it as "still rebooting", keep the hub
    // in-progress, and keep polling until it reports the new version. A hub
    // that genuinely never completes surfaces as the timeout in summarize; it
    // is never declared failed mid-flight on a transient reboot blip (which
    // used to end the whole watch early and mis-report a successful update).
    private suspend fun pollForCompletion(
        hubs: List<Device.Hub>,
        versionInfo: Map<String, HubVersionInfo>,
        updating: Set<String>,
        networkClient: NetworkClient,
        hubIp: String,
        makerApiAppId: String,
        makerApiToken: String,
        maxAttempts: Int,
        delayMillis: Long,
        progressCallback: suspend (String) -> Unit
    ): UpdateProgress {
        var currentProgress = UpdateProgress(
            totalHubs = updating.size,
            updatedHubs = emptySet(),
            failedHubs = emptyMap(),
            inProgressHubs = updating
        )
        var attempts = 0

        while (!currentProgress.isComplete && attempts < maxAttempts) {
            delay(delayMillis)
            attempts++

            val updatedHubs = mutableSetOf<String>()
            for (hubLabel in currentProgress.inProgressHubs) {
                val hub = hubs.find { it.label == hubLabel } ?: continue
                onExpectedFailureSuspend(
                    onFailure = {
                        // Hub unreachable / empty response: it is rebooting to
                        // apply the update. Keep waiting; do not mark it failed.
                        progressCallback("Hub $hubLabel is applying the update (not reachable yet); still waiting")
                    }
                ) {
                    val (newCurrent, _) = getHubVersions(hub, networkClient, hubIp, makerApiAppId, makerApiToken)
                    val originalVersion = versionInfo[hubLabel]!!.currentVersion
                    if (newCurrent != originalVersion) {
                        updatedHubs.add(hubLabel)
                        progressCallback("Hub $hubLabel updated from $originalVersion to $newCurrent")
                    }
                    // else: still on the old version - update still in progress, keep polling
                }
            }

            currentProgress = UpdateProgress(
                totalHubs = currentProgress.totalHubs,
                updatedHubs = currentProgress.updatedHubs + updatedHubs,
                failedHubs = currentProgress.failedHubs,
                inProgressHubs = currentProgress.inProgressHubs - updatedHubs
            )

            if (!currentProgress.isComplete) {
                progressCallback(
                    "Progress: ${currentProgress.successCount}/${currentProgress.totalHubs} updated, " +
                        "${currentProgress.inProgressHubs.size} still updating"
                )
            }
        }
        return currentProgress
    }

    private suspend fun summarize(
        progress: UpdateProgress,
        progressCallback: suspend (String) -> Unit
    ): Result<String> {
        if (!progress.isComplete) {
            val timeoutHubs = progress.inProgressHubs.joinToString(", ")
            progressCallback("Timeout: The following hubs did not complete update: $timeoutHubs")
            return Result.failure(IllegalStateException("Update timeout. Hubs that did not complete: $timeoutHubs"))
        }

        val successMessage = if (progress.successCount > 0) {
            "Successfully updated ${progress.successCount} hub(s): ${progress.updatedHubs.joinToString(", ")}"
        } else {
            ""
        }
        val failureMessage = if (progress.failureCount > 0) {
            "Failed to update ${progress.failureCount} hub(s): " +
                progress.failedHubs.entries.joinToString(", ") { "${it.key} (${it.value})" }
        } else {
            ""
        }

        return if (progress.failureCount == 0) {
            Result.success(successMessage)
        } else {
            Result.failure(IllegalStateException("$failureMessage\n$successMessage"))
        }
    }
}
