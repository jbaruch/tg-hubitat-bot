package jbaru.ch.telegram.hubitat

import com.github.realzimboguy.ewelink.api.EweLink
import com.github.realzimboguy.ewelink.api.model.home.OutletSwitch
import com.github.realzimboguy.ewelink.api.model.home.Thing
import com.github.realzimboguy.ewelink.api.wss.WssResponse
import com.github.realzimboguy.ewelink.api.wss.wssrsp.WssRspMsg
import java.util.concurrent.TimeUnit

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

        eweLink.getWebSocket(object : WssResponse {
            override fun onMessage(s: String) {}
            override fun onMessageParsed(rsp: WssRspMsg) {}
            override fun onError(error: String) {
                throw RuntimeException("WebSocket error: $error")
            }
        })
        TimeUnit.SECONDS.sleep(3)
        eweLink.setMultiDeviceStatus(deviceId, listOf(outletSwitch))

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
        }
    }

    fun getEweLink(): EweLink {
        ensureLoggedIn()
        return eweLink
    }
}