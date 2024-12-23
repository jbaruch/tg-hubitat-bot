package jbaru.ch.telegram.hubitat.model

import io.ktor.client.*
import jbaru.ch.telegram.hubitat.RetryConfig
import jbaru.ch.telegram.hubitat.runDeviceCommand
import kotlinx.coroutines.delay

data class RebootConfig(
    val shutdownTimeoutMs: Long = 45000L,
    val rebootTimeoutMs: Long = 60000L
)

interface PowerControl {
    fun powerOff()
    fun powerOn()
    val retryConfig: RetryConfig
        get() = RetryConfig()  // Default implementation

    suspend fun deepReboot(
        hub: Hub,
        client: HttpClient,
        progressCallback: suspend (String) -> Unit,
        runCommand: suspend (Device, String, List<String>) -> String = ::runDeviceCommand,
        config: RebootConfig = RebootConfig()
    ) {
        var powerRestored = false
        try {
            // 1. Send shutdown command via Maker API
            progressCallback("Initiating graceful shutdown of ${hub.label}")
            val response = runCommand(hub, "shutdown", emptyList())
            if (response != "OK") {
                throw RuntimeException("Failed to initiate hub shutdown: $response")
            }

            // 2. Wait for shutdown to complete
            progressCallback("Hub ${hub.label} shutting down, waiting ${config.shutdownTimeoutMs/1000} seconds...")
            delay(config.shutdownTimeoutMs)

            // 3. Cut power
            progressCallback("Cutting power to hub ${hub.label}")
            try {
                powerOff()
            } catch (e: Exception) {
                throw RuntimeException("Failed to cut power to hub: ${e.message}. Please check the eWeLink device connection.", e)
            }

            // 4. Wait while powered off
            progressCallback("Waiting ${config.rebootTimeoutMs/1000} seconds with power off to ensure complete reset...")
            delay(config.rebootTimeoutMs)

            // 5. Restore power with retries
            var retryCount = 0
            while (!powerRestored && retryCount < retryConfig.maxPowerRestoreAttempts) {
                try {
                    progressCallback(if (retryCount == 0) "Restoring power to hub ${hub.label}" 
                                  else "Retry ${retryCount}/${retryConfig.maxPowerRestoreAttempts}: Attempting to restore power to hub ${hub.label}")
                    powerOn()
                    powerRestored = true
                } catch (e: Exception) {
                    retryCount++
                    if (retryCount < retryConfig.maxPowerRestoreAttempts) {
                        progressCallback("Power restoration attempt failed: ${e.message}. Retrying in ${retryConfig.powerRestoreDelayMs/1000} seconds...")
                        delay(retryConfig.powerRestoreDelayMs)
                    } else {
                        throw RuntimeException("Failed to restore power to hub after ${retryConfig.maxPowerRestoreAttempts} attempts: ${e.message}. Please check the eWeLink device connection.", e)
                    }
                }
            }

            progressCallback("Deep reboot sequence completed. The hub will now begin its boot process.")
        } catch (e: Exception) {
            // If we failed after cutting power but before restoring it, attempt emergency power restoration
            if (!powerRestored) {
                progressCallback("❌ Deep reboot failed: ${e.message}")
                progressCallback("Attempting emergency power restoration...")
                try {
                    powerOn()
                    progressCallback("Emergency power restoration successful")
                } catch (emergencyE: Exception) {
                    progressCallback("⚠️ Emergency power restoration also failed: ${emergencyE.message}")
                }
            }
            throw e
        }
    }
}