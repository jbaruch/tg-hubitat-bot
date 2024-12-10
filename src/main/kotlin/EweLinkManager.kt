package jbaru.ch.telegram.hubitat

import com.github.realzimboguy.ewelink.api.EweLink
import com.github.realzimboguy.ewelink.api.model.home.OutletSwitch
import com.github.realzimboguy.ewelink.api.model.home.Thing

class EweLinkManager(private val session: EweLinkSession) {

    val eweLink: EweLink
        get() = session.getEweLink()

    var things: List<Thing> = emptyList()
        private set

    init {
        refreshDevices()
    }

    fun refreshDevices() {
        session.ensureLoggedIn()
        things = eweLink.things
    }

    fun findByName(namePattern: String, refresh: Boolean = false): Thing? {
        if (refresh) refreshDevices()
        return things.firstOrNull {
            it.itemData.name.contains(namePattern, ignoreCase = true)
        }
    }

    fun apply(name: String, outletSwitch: OutletSwitch) {
        session.ensureLoggedIn()
        val deviceId = findByName(name)?.itemData?.deviceid
            ?: throw IllegalArgumentException("Device '$name' not found")

        try {
            eweLink.setMultiDeviceStatus(deviceId, listOf(outletSwitch))
        } catch (e: Exception) {
            throw RuntimeException("Failed to set device status: ${e.message ?: e.javaClass.simpleName}. This may be due to a WebSocket connection issue.", e)
        }

        // Refresh after state change
        refreshDevices()
    }
}

class EweLinkSession(
    region: String = "us",
    email: String,
    password: String,
    countryCode: String = "+1",
    timeoutSeconds: Int = 60
) {
    private val eweLink = EweLink(region, email, password, countryCode, timeoutSeconds)
    private var isLoggedIn = false

    fun ensureLoggedIn() {
        if (!isLoggedIn) {
            eweLink.login()
            isLoggedIn = true
            // Wait for WebSocket to be connected by attempting a no-op command
            var retries = 0
            while (retries < 10) {
                try {
                    // Try to get things as a connection test
                    eweLink.things
                    break // If successful, WebSocket is connected
                } catch (e: Exception) {
                    if (e.cause?.toString()?.contains("WebsocketNotConnectedException") == true) {
                        retries++
                        if (retries < 10) {
                            Thread.sleep(500) // Wait 500ms between checks
                        }
                    } else {
                        throw e // Some other error occurred
                    }
                }
            }
            if (retries == 10) {
                throw RuntimeException("Failed to establish WebSocket connection after login")
            }
        }
    }

    fun getEweLink(): EweLink {
        ensureLoggedIn()
        return eweLink
    }
}