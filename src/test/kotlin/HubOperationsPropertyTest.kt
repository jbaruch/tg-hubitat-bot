package jbaru.ch.telegram.hubitat

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jbaru.ch.telegram.hubitat.model.Device
import org.mockito.kotlin.*

class HubOperationsPropertyTest : FunSpec({
    
    // **Feature: test-coverage-improvement, Property 6: Hub initialization with valid IP**
    test("hub initialization should set IP correctly for various valid IP addresses") {
        val testIPs = listOf(
            "192.168.1.100",
            "10.0.0.1",
            "172.16.0.50",
            "192.168.0.254",
            "10.10.10.10"
        )
        
        testIPs.forEach { ip ->
            val deviceManager = mock<DeviceManager>()
            val networkClient = mock<NetworkClient>()
            val hub = Device.Hub(1, "Test Hub")
            
            whenever(deviceManager.findDevicesByType(Device.Hub::class.java))
                .thenReturn(listOf(hub))
            
            val hubDetailsJson = """
                {
                    "attributes": [
                        {"name": "localIP", "currentValue": "$ip"}
                    ]
                }
            """.trimIndent()
            
            whenever(networkClient.getBody(any(), any())).thenReturn(hubDetailsJson)
            whenever(networkClient.getBody(argThat { contains("getManagementToken") }, any()))
                .thenReturn("test-token")
            
            val result = HubOperations.initializeHubs(
                deviceManager, networkClient, "hubitat.local", "app-id", "token"
            )
            
            result[0].ip shouldBe ip
        }
    }
})
