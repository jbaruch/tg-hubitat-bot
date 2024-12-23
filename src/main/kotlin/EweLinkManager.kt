package jbaru.ch.telegram.hubitat

import com.github.realzimboguy.ewelink.api.EweLink
import com.github.realzimboguy.ewelink.api.model.home.OutletSwitch
import com.github.realzimboguy.ewelink.api.model.home.Thing

data class RetryConfig(
    val maxAttempts: Int = 3,
    val retryDelayMs: Long = 5000,  // 5 seconds between retries
    val maxWebSocketRetries: Int = 3,
    val webSocketRetryDelayMs: Long = 3000,  // 3 seconds between WebSocket retries
    val maxPowerRestoreAttempts: Int = 3,
    val powerRestoreDelayMs: Long = 5000  // 5 seconds between power restore attempts
)

class EweLinkManager(
    private val session: EweLinkSession,
    val retryConfig: RetryConfig = RetryConfig()
) {

    val eweLink: EweLink by lazy { session.eweLink }

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
        var lastException: Exception? = null
        var success = false
        repeat(retryConfig.maxAttempts) { attempt ->
            if (!success) {  // Only try if we haven't succeeded yet
                try {
                    session.ensureLoggedIn()
                    val deviceId = findByName(name)?.itemData?.deviceid
                        ?: throw IllegalArgumentException("Device '$name' not found")

                    eweLink.setMultiDeviceStatus(deviceId, listOf(outletSwitch))
                    // Refresh after successful state change
                    refreshDevices()
                    success = true  // Mark as successful
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < retryConfig.maxAttempts - 1) { // Only sleep if we're going to retry
                        Thread.sleep(retryConfig.retryDelayMs) // Wait between retries
                    } else {
                        // On last attempt, wrap and throw the exception
                        throw RuntimeException("Failed to set device status: ${e.message ?: e.javaClass.simpleName}. This may be due to a WebSocket connection issue.", e)
                    }
                }
            }
        }
        
        // If we get here and haven't succeeded, all retries failed
        if (!success) {
            throw RuntimeException("Failed to set device status after ${retryConfig.maxAttempts} attempts: ${lastException?.message}. This may be due to a WebSocket connection issue.", lastException)
        }
    }
}

class EweLinkSession(
    region: String = "us",
    email: String,
    password: String,
    countryCode: String = "+1",
    timeoutSeconds: Int = 60,
    val retryConfig: RetryConfig = RetryConfig()
) {
    private val _eweLink = EweLink(region, email, password, countryCode, timeoutSeconds)
    private var isLoggedIn = false

    val eweLink: EweLink
        get() {
            ensureLoggedIn()
            return _eweLink
        }

    fun ensureLoggedIn() {
        if (!isLoggedIn) {
            _eweLink.login()
            isLoggedIn = true
            // Wait for WebSocket to be connected by attempting a no-op command
            var retries = 0
            while (retries < retryConfig.maxWebSocketRetries) {
                try {
                    // Try to get things as a connection test
                    _eweLink.things
                    break // If successful, WebSocket is connected
                } catch (e: Exception) {
                    if (e.cause?.toString()?.contains("WebsocketNotConnectedException") == true) {
                        retries++
                        if (retries < retryConfig.maxWebSocketRetries) {
                            Thread.sleep(retryConfig.webSocketRetryDelayMs) // Wait between checks
                        }
                    } else {
                        throw e // Some other error occurred
                    }
                }
            }
            if (retries == retryConfig.maxWebSocketRetries) {
                throw RuntimeException("Failed to establish WebSocket connection after login")
            }
        }
    }
}