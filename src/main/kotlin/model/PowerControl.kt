package jbaru.ch.telegram.hubitat.model

import io.ktor.client.*
import jbaru.ch.telegram.hubitat.runDeviceCommand
import kotlinx.coroutines.delay

interface PowerControl {
    fun powerOff()
    fun powerOn()

    suspend fun deepReboot(
        hub: Hub,
        client: HttpClient,
        progressCallback: suspend (String) -> Unit,
        runCommand: suspend (Device, String, List<String>) -> String = ::runDeviceCommand
    ) {
        try {
            // 1. Send shutdown command via Maker API
            progressCallback("Initiating graceful shutdown of ${hub.label}")
            val response = runCommand(hub, "shutdown", emptyList())
            if (response != "OK") {
                throw RuntimeException("Failed to initiate hub shutdown: $response")
            }

            // 2. Wait for shutdown to complete
            progressCallback("Hub ${hub.label} shutting down, waiting 45 seconds...")
            delay(45000)

            // 3. Cut power
            progressCallback("Cutting power to hub ${hub.label}")
            try {
                powerOff()
            } catch (e: Exception) {
                throw RuntimeException("Failed to cut power to hub: ${e.message}. Please check the eWeLink device connection.", e)
            }

            // 4. Wait while powered off
            progressCallback("Waiting 60 seconds with power off to ensure complete reset...")
            delay(60000)

            // 5. Restore power
            progressCallback("Restoring power to hub ${hub.label}")
            try {
                powerOn()
            } catch (e: Exception) {
                throw RuntimeException("Failed to restore power to hub: ${e.message}. Please check the eWeLink device connection.", e)
            }

            progressCallback("Deep reboot sequence completed. The hub will now begin its boot process.")
        } catch (e: Exception) {
            progressCallback("❌ Deep reboot failed: ${e.message}")
            throw e
        }
    }
}