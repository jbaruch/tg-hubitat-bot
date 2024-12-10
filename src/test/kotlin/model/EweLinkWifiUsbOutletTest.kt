package jbaru.ch.telegram.hubitat.model

import io.ktor.client.*
import jbaru.ch.telegram.hubitat.EweLinkManager
import jbaru.ch.telegram.hubitat.runDeviceCommand
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*

class EweLinkWifiUsbOutletTest {
    private lateinit var eweLinkManager: EweLinkManager
    private lateinit var outlet: EweLinkWifiUsbOutlet

    @BeforeEach
    fun setup() {
        eweLinkManager = mock()
        outlet = EweLinkWifiUsbOutlet(
            name = "Test Device",
            eweLinkManager = eweLinkManager
        )
    }

    @Test
    fun `test power off`() {
        outlet.powerOff()

        verify(eweLinkManager).apply(
            eq("Test Device"),
            argThat { outletSwitch ->
                outletSwitch.outlet == 0 &&
                        outletSwitch._switch == "off"
            }
        )
    }

    @Test
    fun `test power on`() {
        outlet.powerOn()

        verify(eweLinkManager).apply(
            eq("Test Device"),
            argThat { outletSwitch ->
                outletSwitch.outlet == 0 &&
                        outletSwitch._switch == "on"
            }
        )
    }

    @Test
    fun `test power off fails when websocket is not connected`() {
        val websocketException = org.java_websocket.exceptions.WebsocketNotConnectedException()
        whenever(eweLinkManager.apply(
            eq("Test Device"),
            argThat { outletSwitch ->
                outletSwitch.outlet == 0 &&
                        outletSwitch._switch == "off"
            }
        )).thenThrow(RuntimeException("Failed to set device status: WebsocketNotConnectedException. This may be due to a WebSocket connection issue.", websocketException))

        val exception = assertThrows<RuntimeException> {
            outlet.powerOff()
        }

        assert(exception.message?.contains("WebSocket") == true)
        assert(exception.cause is org.java_websocket.exceptions.WebsocketNotConnectedException)
    }

    @Test
    fun `test deep reboot fails when websocket is not connected`() {
        val websocketException = org.java_websocket.exceptions.WebsocketNotConnectedException()
        whenever(eweLinkManager.apply(any(), any())).thenThrow(
            RuntimeException(
                "Failed to set device status: WebsocketNotConnectedException. This may be due to a WebSocket connection issue.",
                websocketException
            )
        )

        val progressMessages = mutableListOf<String>()
        val hub = Hub(id = 1, label = "Test Hub", ip = "http://localhost").apply {
            powerControl = outlet
        }
        val httpClient = mock<HttpClient>()

        runBlocking {
            val exception = assertThrows<RuntimeException> {
                outlet.deepReboot(
                    hub = hub,
                    client = httpClient,
                    progressCallback = { message -> progressMessages.add(message) },
                    runCommand = { _, _, _ -> "OK" }
                )
            }

            assert(exception.message?.contains("Failed to cut power to hub") == true)
            assert(progressMessages.any { it.contains("❌ Deep reboot failed") })
        }
    }
}