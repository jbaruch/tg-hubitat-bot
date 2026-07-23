package jbaru.ch.telegram.hubitat.integration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import jbaru.ch.telegram.hubitat.FirmwareOperations
import jbaru.ch.telegram.hubitat.KtorNetworkClient
import jbaru.ch.telegram.hubitat.model.Device

/**
 * Real integration test that runs the full firmware sweep against live hubs.
 *
 * Set FIRMWARE_HUB_IPS to a comma-separated list of hub IPs to enable, e.g.:
 * FIRMWARE_HUB_IPS=192.168.30.15,192.168.30.17 ./gradlew test --tests "*RealFirmwareCheckTest*"
 */
class RealFirmwareCheckTest : FunSpec({

    val hubIps = System.getenv("FIRMWARE_HUB_IPS")

    if (hubIps.isNullOrBlank()) {
        println("RealFirmwareCheckTest skipped: FIRMWARE_HUB_IPS not set")
    } else {
        test("firmware sweep against live hubs produces a report") {
            val client = HttpClient(CIO) {
                install(HttpTimeout) {
                    connectTimeoutMillis = 10_000
                    requestTimeoutMillis = 30_000
                    socketTimeoutMillis = 30_000
                }
            }
            val hubs = hubIps.split(",").mapIndexed { i, ip ->
                Device.Hub(id = i, label = ip.trim(), ip = ip.trim())
            }

            val messages = FirmwareOperations.checkFirmware(hubs, KtorNetworkClient(client))

            messages.shouldNotBeEmpty()
            println("=== Firmware report (${messages.size} message(s)) ===")
            messages.forEach { println(it); println("--- message boundary ---") }
        }
    }
})
