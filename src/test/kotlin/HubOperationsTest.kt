package jbaru.ch.telegram.hubitat

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import jbaru.ch.telegram.hubitat.model.Device
import org.mockito.kotlin.*

class HubOperationsTest : FunSpec({
    
    lateinit var deviceManager: DeviceManager
    lateinit var networkClient: NetworkClient
    val hubIp = "hubitat.local"
    val makerApiAppId = "test-app-id"
    val makerApiToken = "test-token"
    
    beforeEach {
        deviceManager = mock()
        networkClient = mock()
    }
    
    context("initializeHubs") {
        test("should initialize hub with valid IP address") {
            val hub = Device.Hub(1, "Test Hub")
            whenever(deviceManager.findDevicesByType(Device.Hub::class.java))
                .thenReturn(listOf(hub))
            
            val hubDetailsJson = """
                {
                    "attributes": [
                        {"name": "localIP", "currentValue": "192.168.1.100"}
                    ]
                }
            """.trimIndent()
            
            whenever(networkClient.getBody(argThat { contains("/devices/1") }, any()))
                .thenReturn(hubDetailsJson)
            whenever(networkClient.getBody(argThat { equals("http://192.168.1.100/hub/advanced/getManagementToken") }, any()))
                .thenReturn("test-management-token")
            
            val result = HubOperations.initializeHubs(
                deviceManager, networkClient, hubIp, makerApiAppId, makerApiToken
            )
            
            result.size shouldBe 1
            result[0].ip shouldBe "192.168.1.100"
            result[0].managementToken shouldBe "test-management-token"
        }
        
        test("should handle multiple hubs") {
            val hub1 = Device.Hub(1, "Hub 1")
            val hub2 = Device.Hub(2, "Hub 2")
            whenever(deviceManager.findDevicesByType(Device.Hub::class.java))
                .thenReturn(listOf(hub1, hub2))
            
            val hubDetailsJson1 = """
                {
                    "attributes": [
                        {"name": "localIP", "currentValue": "192.168.1.100"}
                    ]
                }
            """.trimIndent()
            
            val hubDetailsJson2 = """
                {
                    "attributes": [
                        {"name": "localIP", "currentValue": "192.168.1.101"}
                    ]
                }
            """.trimIndent()
            
            whenever(networkClient.getBody(argThat { contains("/devices/1") }, any()))
                .thenReturn(hubDetailsJson1)
            whenever(networkClient.getBody(argThat { contains("/devices/2") }, any()))
                .thenReturn(hubDetailsJson2)
            whenever(networkClient.getBody(argThat { contains("192.168.1.100") }, any()))
                .thenReturn("token1")
            whenever(networkClient.getBody(argThat { contains("192.168.1.101") }, any()))
                .thenReturn("token2")
            
            val result = HubOperations.initializeHubs(
                deviceManager, networkClient, hubIp, makerApiAppId, makerApiToken
            )
            
            result.size shouldBe 2
            result[0].ip shouldBe "192.168.1.100"
            result[1].ip shouldBe "192.168.1.101"
        }
    }
    
    context("updateHubs") {
        test("should return success when all hub updates succeed") {
            val hub1 = Device.Hub(1, "Hub 1", "token1", "192.168.1.100")
            val hub2 = Device.Hub(2, "Hub 2", "token2", "192.168.1.101")
            
            val mockResponse = mock<HttpResponse> {
                on { status } doReturn HttpStatusCode.OK
            }
            whenever(networkClient.get(any(), any())).thenReturn(mockResponse)
            
            val result = HubOperations.updateHubs(listOf(hub1, hub2), networkClient)
            
            result.isSuccess shouldBe true
            result.getOrNull() shouldBe "All hub updates initialized successfully."
        }
        
        test("should report failures and successes when some hubs fail") {
            val hub1 = Device.Hub(1, "Hub 1", "token1", "192.168.1.100")
            val hub2 = Device.Hub(2, "Hub 2", "token2", "192.168.1.101")
            
            val okResponse = mock<HttpResponse> {
                on { status } doReturn HttpStatusCode.OK
            }
            val errorResponse = mock<HttpResponse> {
                on { status } doReturn HttpStatusCode.InternalServerError
            }
            
            whenever(networkClient.get(argThat { contains("192.168.1.100") }, any()))
                .thenReturn(okResponse)
            whenever(networkClient.get(argThat { contains("192.168.1.101") }, any()))
                .thenReturn(errorResponse)
            
            val result = HubOperations.updateHubs(listOf(hub1, hub2), networkClient)
            
            result.isFailure shouldBe true
            val message = result.exceptionOrNull()?.message ?: ""
            message shouldContain "Failed to update hub Hub 2"
            message shouldContain "Successfully issued update request to hub Hub 1"
        }
        
        test("should return failure when all hubs fail") {
            val hub1 = Device.Hub(1, "Hub 1", "token1", "192.168.1.100")
            val hub2 = Device.Hub(2, "Hub 2", "token2", "192.168.1.101")
            
            val errorResponse = mock<HttpResponse> {
                on { status } doReturn HttpStatusCode.InternalServerError
            }
            whenever(networkClient.get(any(), any())).thenReturn(errorResponse)
            
            val result = HubOperations.updateHubs(listOf(hub1, hub2), networkClient)
            
            result.isFailure shouldBe true
            val message = result.exceptionOrNull()?.message ?: ""
            message shouldContain "Failed to update hub Hub 1"
            message shouldContain "Failed to update hub Hub 2"
        }
        
        test("should handle exceptions and report InternalServerError status") {
            val hub = Device.Hub(1, "Hub 1", "token1", "192.168.1.100")
            
            whenever(networkClient.get(any(), any()))
                .thenThrow(RuntimeException("Network error"))
            
            val result = HubOperations.updateHubs(listOf(hub), networkClient)
            
            result.isFailure shouldBe true
            val message = result.exceptionOrNull()?.message ?: ""
            message shouldContain "Failed to update hub Hub 1"
            message shouldContain "Internal Server Error"
        }
    }
    
    context("getHubVersions") {
        test("should retrieve current and available firmware versions") {
            val hub = Device.Hub(1, "Test Hub")
            hub.ip = "192.168.1.100"
            
            val hubInfoJson = """
                {
                    "firmwareVersions": {
                        "current": "2.3.4.150",
                        "available": "2.3.5.160"
                    }
                }
            """.trimIndent()
            
            whenever(networkClient.getBody(eq("http://192.168.1.100/hub/advanced/hubInfo"), any()))
                .thenReturn(hubInfoJson)
            
            val (current, available) = HubOperations.getHubVersions(hub, networkClient)
            
            current shouldBe "2.3.4.150"
            available shouldBe "2.3.5.160"
        }
        
        test("should handle malformed JSON response") {
            val hub = Device.Hub(1, "Test Hub")
            hub.ip = "192.168.1.100"
            
            val malformedJson = """{"invalid": "json"}"""
            
            whenever(networkClient.getBody(eq("http://192.168.1.100/hub/advanced/hubInfo"), any()))
                .thenReturn(malformedJson)
            
            val (current, available) = HubOperations.getHubVersions(hub, networkClient)
            
            current shouldBe ""
            available shouldBe ""
        }
        
        test("should handle network error") {
            val hub = Device.Hub(1, "Test Hub")
            hub.ip = "192.168.1.100"
            
            whenever(networkClient.getBody(eq("http://192.168.1.100/hub/advanced/hubInfo"), any()))
                .thenThrow(RuntimeException("Network error"))
            
            try {
                HubOperations.getHubVersions(hub, networkClient)
                throw AssertionError("Expected exception to be thrown")
            } catch (e: RuntimeException) {
                e.message shouldContain "Network error"
            }
        }
    }
    
    context("updateHubsWithPolling") {
        test("should report all hubs up to date when no updates needed") {
            val hub1 = Device.Hub(1, "Hub 1")
            hub1.ip = "192.168.1.100"
            hub1.managementToken = "token1"
            
            val hubInfoJson = """
                {
                    "firmwareVersions": {
                        "current": "2.3.4.150",
                        "available": "2.3.4.150"
                    }
                }
            """.trimIndent()
            
            whenever(networkClient.getBody(eq("http://192.168.1.100/hub/advanced/hubInfo"), any()))
                .thenReturn(hubInfoJson)
            
            val messages = mutableListOf<String>()
            val result = HubOperations.updateHubsWithPolling(
                listOf(hub1),
                networkClient,
                maxAttempts = 5,
                delayMillis = 100
            ) { messages.add(it) }
            
            result.isSuccess shouldBe true
            result.getOrNull() shouldContain "already up to date"
            messages.any { it.contains("already up to date") } shouldBe true
        }
        
        test("should successfully update hubs with polling") {
            val hub1 = Device.Hub(1, "Hub 1")
            hub1.ip = "192.168.1.100"
            hub1.managementToken = "token1"
            
            val beforeUpdateJson = """
                {
                    "firmwareVersions": {
                        "current": "2.3.4.150",
                        "available": "2.3.5.160"
                    }
                }
            """.trimIndent()
            
            val afterUpdateJson = """
                {
                    "firmwareVersions": {
                        "current": "2.3.5.160",
                        "available": "2.3.5.160"
                    }
                }
            """.trimIndent()
            
            val mockResponse: HttpResponse = mock()
            whenever(mockResponse.status).thenReturn(HttpStatusCode.OK)
            
            whenever(networkClient.getBody(eq("http://192.168.1.100/hub/advanced/hubInfo"), any()))
                .thenReturn(beforeUpdateJson)
                .thenReturn(afterUpdateJson)
            
            whenever(networkClient.get(argThat { contains("/management/firmwareUpdate") }, any()))
                .thenReturn(mockResponse)
            
            val messages = mutableListOf<String>()
            val result = HubOperations.updateHubsWithPolling(
                listOf(hub1),
                networkClient,
                maxAttempts = 5,
                delayMillis = 100
            ) { messages.add(it) }
            
            result.isSuccess shouldBe true
            result.getOrNull() shouldContain "Successfully updated"
            messages.any { it.contains("Update initiated") } shouldBe true
        }
        
        test("should handle timeout scenario") {
            val hub1 = Device.Hub(1, "Hub 1")
            hub1.ip = "192.168.1.100"
            hub1.managementToken = "token1"
            
            val hubInfoJson = """
                {
                    "firmwareVersions": {
                        "current": "2.3.4.150",
                        "available": "2.3.5.160"
                    }
                }
            """.trimIndent()
            
            val mockResponse: HttpResponse = mock()
            whenever(mockResponse.status).thenReturn(HttpStatusCode.OK)
            
            whenever(networkClient.getBody(eq("http://192.168.1.100/hub/advanced/hubInfo"), any()))
                .thenReturn(hubInfoJson)
            
            whenever(networkClient.get(argThat { contains("/management/firmwareUpdate") }, any()))
                .thenReturn(mockResponse)
            
            val messages = mutableListOf<String>()
            val result = HubOperations.updateHubsWithPolling(
                listOf(hub1),
                networkClient,
                maxAttempts = 2,
                delayMillis = 100
            ) { messages.add(it) }
            
            result.isFailure shouldBe true
            result.exceptionOrNull()?.message shouldContain "Update timeout"
            messages.any { it.contains("Timeout") } shouldBe true
        }
        
        test("should report progress messages") {
            val hub1 = Device.Hub(1, "Hub 1")
            hub1.ip = "192.168.1.100"
            hub1.managementToken = "token1"
            
            val beforeUpdateJson = """
                {
                    "firmwareVersions": {
                        "current": "2.3.4.150",
                        "available": "2.3.5.160"
                    }
                }
            """.trimIndent()
            
            val afterUpdateJson = """
                {
                    "firmwareVersions": {
                        "current": "2.3.5.160",
                        "available": "2.3.5.160"
                    }
                }
            """.trimIndent()
            
            val mockResponse: HttpResponse = mock()
            whenever(mockResponse.status).thenReturn(HttpStatusCode.OK)
            
            whenever(networkClient.getBody(eq("http://192.168.1.100/hub/advanced/hubInfo"), any()))
                .thenReturn(beforeUpdateJson)
                .thenReturn(afterUpdateJson)
            
            whenever(networkClient.get(argThat { contains("/management/firmwareUpdate") }, any()))
                .thenReturn(mockResponse)
            
            val messages = mutableListOf<String>()
            HubOperations.updateHubsWithPolling(
                listOf(hub1),
                networkClient,
                maxAttempts = 5,
                delayMillis = 100
            ) { messages.add(it) }
            
            messages.any { it.contains("Hubs needing update") } shouldBe true
            messages.any { it.contains("Update initiated") } shouldBe true
            messages.any { it.contains("updated from") } shouldBe true
        }
        
        test("should report version change") {
            val hub1 = Device.Hub(1, "Hub 1")
            hub1.ip = "192.168.1.100"
            hub1.managementToken = "token1"
            
            val beforeUpdateJson = """
                {
                    "firmwareVersions": {
                        "current": "2.3.4.150",
                        "available": "2.3.5.160"
                    }
                }
            """.trimIndent()
            
            val afterUpdateJson = """
                {
                    "firmwareVersions": {
                        "current": "2.3.5.160",
                        "available": "2.3.5.160"
                    }
                }
            """.trimIndent()
            
            val mockResponse: HttpResponse = mock()
            whenever(mockResponse.status).thenReturn(HttpStatusCode.OK)
            
            whenever(networkClient.getBody(eq("http://192.168.1.100/hub/advanced/hubInfo"), any()))
                .thenReturn(beforeUpdateJson)
                .thenReturn(afterUpdateJson)
            
            whenever(networkClient.get(argThat { contains("/management/firmwareUpdate") }, any()))
                .thenReturn(mockResponse)
            
            val messages = mutableListOf<String>()
            HubOperations.updateHubsWithPolling(
                listOf(hub1),
                networkClient,
                maxAttempts = 5,
                delayMillis = 100
            ) { messages.add(it) }
            
            messages.any { it.contains("updated from 2.3.4.150 to 2.3.5.160") } shouldBe true
        }
    }
})
