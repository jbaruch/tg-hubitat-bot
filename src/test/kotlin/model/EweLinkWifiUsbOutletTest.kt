package jbaru.ch.telegram.hubitat.model

import com.github.realzimboguy.ewelink.api.EweLink
import com.github.realzimboguy.ewelink.api.model.home.ItemData
import com.github.realzimboguy.ewelink.api.model.home.OutletSwitch
import com.github.realzimboguy.ewelink.api.model.home.Params
import com.github.realzimboguy.ewelink.api.model.home.Thing
import io.ktor.client.*
import jbaru.ch.telegram.hubitat.EweLinkManager
import jbaru.ch.telegram.hubitat.EweLinkSession
import jbaru.ch.telegram.hubitat.RetryConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.InOrder
import org.mockito.kotlin.*
import org.mockito.Mockito.anyString
import org.mockito.Mockito.anyList

class EweLinkWifiUsbOutletTest {
    private lateinit var eweLinkManager: EweLinkManager
    private lateinit var outlet: EweLinkWifiUsbOutlet
    private lateinit var session: EweLinkSession
    private lateinit var eweLink: EweLink

    companion object {
        val TEST_RETRY_CONFIG = RetryConfig(
            maxAttempts = 3,
            retryDelayMs = 5000,
            maxWebSocketRetries = 3,
            webSocketRetryDelayMs = 3000
        )
    }

    @BeforeEach
    fun setup() {
        session = mock<EweLinkSession>()
        eweLink = mock<EweLink>()
        
        // Mock session behavior
        doNothing().whenever(session).ensureLoggedIn()
        whenever(session.eweLink).thenReturn(eweLink)
        
        // Mock EweLink behavior
        val thing = mock<Thing>()
        val itemData = mock<ItemData>()
        val params = mock<Params>()
        val outletSwitch = OutletSwitch().apply {
            outlet = 0
            _switch = "off"
        }

        whenever(thing.itemData).thenReturn(itemData)
        whenever(itemData.deviceid).thenReturn("test-device-id")
        whenever(itemData.name).thenReturn("Test Device")
        whenever(itemData.params).thenReturn(params)
        whenever(params.switches).thenReturn(listOf(outletSwitch))
        whenever(eweLink.things).thenReturn(listOf(thing))

        // Create manager after setting up all mocks
        eweLinkManager = spy(EweLinkManager(session, TEST_RETRY_CONFIG))
        outlet = spy(EweLinkWifiUsbOutlet(
            name = "Test Device",
            eweLinkManager = eweLinkManager,
            outletNumber = 0
        ))

        // Mock EweLinkManager behavior
        doReturn(eweLink).whenever(eweLinkManager).eweLink
        doNothing().whenever(eweLinkManager).refreshDevices()
        doReturn(listOf(thing)).whenever(eweLinkManager).things
        doReturn(thing).whenever(eweLinkManager).findByName(eq("Test Device"), any())
        doReturn(TEST_RETRY_CONFIG).whenever(outlet).retryConfig
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
        // Set up the device ID lookup
        val deviceId = "test-device-id"
        val thing = mock<Thing>()
        val itemData = mock<ItemData>()
        doReturn(deviceId).`when`(itemData).deviceid
        doReturn(itemData).`when`(thing).itemData
        doReturn(thing).`when`(eweLinkManager).findByName("Test Device")

        // Make eweLink.setMultiDeviceStatus throw an exception twice, then succeed
        var callCount = 0
        doAnswer { 
            callCount++
            if (callCount <= 2) {
                throw Exception("WebSocket connection is closed")
            }
            null
        }.`when`(eweLink).setMultiDeviceStatus(eq(deviceId), argThat { switches ->
            switches.size == 1 && switches[0].let { switch ->
                switch.outlet == 0 && switch._switch == "off"
            }
        })

        // Execute the power off command
        outlet.powerOff()

        // Verify the behavior
        verify(eweLink, times(3)).setMultiDeviceStatus(eq(deviceId), argThat { switches ->
            switches.size == 1 && switches[0].let { switch ->
                switch.outlet == 0 && switch._switch == "off"
            }
        })
        verify(eweLinkManager).refreshDevices()
    }

    @Test
    fun `test deep reboot`() = runBlocking {
        // Create mock hub and client
        val mockHub = mock<Hub>()
        whenever(mockHub.label).thenReturn("Test Hub")
        
        val mockClient = mock<HttpClient>()
        
        // Create progress message collector
        val messages = mutableListOf<String>()
        val progressCallback: suspend (String) -> Unit = { messages.add(it) }
        
        // Create mock runCommand function
        val mockRunCommand: suspend (Device, String, List<String>) -> String = { _, _, _ -> "OK" }
        
        // Create test reboot config with shorter timeouts
        val testRebootConfig = RebootConfig(
            shutdownTimeoutMs = 100,  // 100ms instead of 45s
            rebootTimeoutMs = 100     // 100ms instead of 60s
        )

        // Mock the retryConfig to use test values
        whenever(eweLinkManager.retryConfig).thenReturn(TEST_RETRY_CONFIG)
        
        // Execute deep reboot
        outlet.deepReboot(mockHub, mockClient, progressCallback, mockRunCommand, testRebootConfig)
        
        // Verify power control sequence
        verify(eweLinkManager).apply(
            eq("Test Device"),
            argThat { outletSwitch ->
                outletSwitch.outlet == 0 &&
                        outletSwitch._switch == "off"
            }
        )
        
        verify(eweLinkManager).apply(
            eq("Test Device"),
            argThat { outletSwitch ->
                outletSwitch.outlet == 0 &&
                        outletSwitch._switch == "on"
            }
        )

        // Verify progress messages
        assert(messages.any { it.contains("Initiating graceful shutdown") })
        assert(messages.any { it.contains("shutting down") })
        assert(messages.any { it.contains("Cutting power") })
        assert(messages.any { it.contains("Restoring power") })
        assert(messages.any { it.contains("completed") })
    }
}