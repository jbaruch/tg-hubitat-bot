package jbaru.ch.telegram.hubitat.model

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import jbaru.ch.telegram.hubitat.EweLinkManager
import jbaru.ch.telegram.hubitat.EweLinkSession
import jbaru.ch.telegram.hubitat.getDeviceAttribute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
@SerialName("Hub Information Driver v3")
data class Hub(
    override val id: Int,
    override val label: String,
    var managementToken: String = "",
    var ip: String = "",
    var powerControl: PowerControl? = null
) : Device() {
    var currentVersion: String? = null
    var updateVersion: String? = null

    override val supportedOps: Map<String, Int> = mapOf(
        "reboot" to 0,
        "deepReboot" to 0
    )

    override val attributes: Map<String, List<String>> = emptyMap()

    /**
     * Initializes the hub with its IP address, management token, and firmware versions.
     * 
     * @param client The HTTP client to use for API requests
     * @param hubIp The default hub IP to use if the hub's IP cannot be determined
     * @param makerApiAppId The Maker API app ID
     * @param makerApiToken The Maker API token
     * @return True if initialization was successful, false otherwise
     */
    suspend fun initialize(
        client: HttpClient,
        hubIp: String,
        makerApiAppId: String,
        makerApiToken: String
    ): Boolean {
        try {
            // Get hub details from Maker API
            val json: Map<String, JsonElement> =
                Json.parseToJsonElement(client.get("http://${hubIp}/apps/api/${makerApiAppId}/devices/${id}") {
                    parameter("access_token", makerApiToken)
                }.body<String>()).jsonObject

            // Safely extract the IP address with null checks
            val attributes = json["attributes"] as? JsonArray
            val ipAttribute = attributes?.find {
                it.jsonObject["name"]?.jsonPrimitive?.content == "localIP"
            }
            val extractedIp = ipAttribute?.jsonObject?.get("currentValue")?.jsonPrimitive?.content

            if (extractedIp.isNullOrBlank()) {
                println("WARNING: Could not find valid IP address for hub ${label} (ID: ${id})")
                return false
            }

            ip = extractedIp

            try {
                // Check if IP is null or empty before using it
                if (ip.isNullOrBlank()) {
                    println("WARNING: IP address is null or empty for hub ${label}")
                    return false
                }

                managementToken = client.get("http://${ip}/hub/advanced/getManagementToken").body()

                // Initialize firmware versions
                try {
                    currentVersion = getDeviceAttribute(this, "firmwareVersionString")
                    updateVersion = getDeviceAttribute(this, "hubUpdateVersion")
                    println("Successfully initialized hub ${label} with IP ${ip} (firmware: ${currentVersion})")
                } catch (e: Exception) {
                    println("WARNING: Could not initialize firmware versions for hub ${label}: ${e.message}")
                    // Continue with the hub, just without firmware version info
                }

                return true
            } catch (e: Exception) {
                println("ERROR: Failed to get management token for hub ${label} at IP ${ip}: ${e.message}")
                // Continue with the hub, this one will have IP but no management token
                // We still return true because the hub has a valid IP and should be included
                return true
            }
        } catch (e: Exception) {
            println("ERROR: Failed to initialize hub ${label}: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    suspend fun deepReboot(
        client: HttpClient,
        progressCallback: suspend (String) -> Unit,
        config: RebootConfig = RebootConfig()
    ) {
        powerControl?.deepReboot(
            this,
            client,
            progressCallback,
            config = config
        )
    }
}

class HubPowerManager(eweLinkSession: EweLinkSession) {
    private val eweLink = EweLinkManager(eweLinkSession)

    companion object {
        fun getOutletNameForHub(hubLabel: String) = "Hub Power - $hubLabel"
    }

    fun configureHubPower(hub: Hub) {
        val outletName = getOutletNameForHub(hub.label)
        val thing = eweLink.findByName(outletName)
            ?: throw IllegalStateException("No power outlet found with name '$outletName' for hub '${hub.label}'")

        hub.powerControl = EweLinkWifiUsbOutlet.fromThing(thing, eweLink)
    }
}
