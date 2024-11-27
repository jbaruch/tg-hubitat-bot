package jbaru.ch.telegram.hubitat.model

import io.ktor.client.*
import jbaru.ch.telegram.hubitat.runDeviceCommand
import kotlinx.coroutines.delay

interface PowerControl {
    fun powerOff()
    fun powerOn()

    suspend fun deepReboot(hub: Hub, client: HttpClient, progressCallback: suspend (String) -> Unit) {
        // 1. Send shutdown command via Maker API
        progressCallback("Initiating graceful shutdown of ${hub.label}")
        val response = runDeviceCommand(hub, "shutdown", emptyList())
        if (response != "OK") {
            throw RuntimeException("Failed to initiate hub shutdown: $response")
        }

        // 2. Wait for shutdown to complete
        progressCallback("Hub ${hub.label} shutting down, waiting 45 seconds...")
        delay(45000)

        // 3. Cut power
        progressCallback("Cutting power to hub ${hub.label}")
        powerOff()

        // 4. Wait while powered off
        progressCallback("Waiting 60 seconds with power off to ensure complete reset...")
        delay(60000)

        // 5. Restore power
        progressCallback("Restoring power to hub ${hub.label}")
        powerOn()

        progressCallback("Deep reboot sequence completed. The hub will now begin its boot process.")
    }
}