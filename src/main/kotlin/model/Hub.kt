package jbaru.ch.telegram.hubitat.jbaru.ch.telegram.hubitat.model

import io.ktor.client.*
import jbaru.ch.telegram.hubitat.EweLinkManager
import jbaru.ch.telegram.hubitat.EweLinkSession
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("Hub Information Driver v3")
data class Hub(
    override val id: Int,
    override val label: String,
    var managementToken: String = "",
    var ip: String = "",
    var powerControl: PowerControl? = null
) : Device() {
    override val supportedOps: Map<String, Int> = mapOf(
        "reboot" to 0,
        "deepReboot" to 0
    )
    override val attributes: Map<String, List<String>> = mapOf(
        "firmwareVersionString" to listOf("string")
    )

    suspend fun deepReboot(client: HttpClient, progressCallback: suspend (String) -> Unit) {
        powerControl?.deepReboot(this, client, progressCallback)
            ?: throw UnsupportedOperationException("Deep reboot not supported for hub $label - no power control configured")
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