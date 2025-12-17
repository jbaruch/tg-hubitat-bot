package jbaru.ch.telegram.hubitat

import io.kotest.core.spec.style.FunSpec
import io.kotest.core.Tag
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import jbaru.ch.telegram.hubitat.model.Device
import org.mockito.kotlin.*

object RealHubTest : Tag()

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
        test("should retrieve current and available firmware versions from Maker API") {
            val hub = Device.Hub(1, "Test Hub")
            
            val makerApiResponse = """
                {
                    "attributes": [
                        {"name": "firmwareVersionString", "currentValue": "2.3.4.150"},
                        {"name": "hubUpdateVersion", "currentValue": "2.3.5.160"}
                    ]
                }
            """.trimIndent()
            
            whenever(networkClient.getBody(eq("http://hubitat.local/apps/api/test-app-id/devices/1"), any()))
                .thenReturn(makerApiResponse)
            
            val (current, available) = HubOperations.getHubVersions(hub, networkClient, hubIp, makerApiAppId, makerApiToken)
            
            current shouldBe "2.3.4.150"
            available shouldBe "2.3.5.160"
        }
        
        test("should handle response with missing firmware attributes") {
            val hub = Device.Hub(1, "Test Hub")
            
            val makerApiResponse = """
                {
                    "attributes": [
                        {"name": "someOtherAttribute", "currentValue": "value"}
                    ]
                }
            """.trimIndent()
            
            whenever(networkClient.getBody(eq("http://hubitat.local/apps/api/test-app-id/devices/1"), any()))
                .thenReturn(makerApiResponse)
            
            val (current, available) = HubOperations.getHubVersions(hub, networkClient, hubIp, makerApiAppId, makerApiToken)
            
            current shouldBe ""
            available shouldBe ""
        }
        
        test("should handle network error") {
            val hub = Device.Hub(1, "Test Hub")
            
            whenever(networkClient.getBody(eq("http://hubitat.local/apps/api/test-app-id/devices/1"), any()))
                .thenThrow(RuntimeException("Network error"))
            
            try {
                HubOperations.getHubVersions(hub, networkClient, hubIp, makerApiAppId, makerApiToken)
                throw AssertionError("Expected exception to be thrown")
            } catch (e: RuntimeException) {
                e.message shouldContain "Network error"
            }
        }
        
        test("should provide diagnostic info when receiving malformed JSON") {
            val hub = Device.Hub(1, "Test Hub")
            
            val malformedJson = """{"invalid": "json" missing bracket"""
            
            whenever(networkClient.getBody(eq("http://hubitat.local/apps/api/test-app-id/devices/1"), any()))
                .thenReturn(malformedJson)
            
            try {
                HubOperations.getHubVersions(hub, networkClient, hubIp, makerApiAppId, makerApiToken)
                throw AssertionError("Expected exception to be thrown")
            } catch (e: Exception) {
                e.message shouldContain "Test Hub"
                e.message shouldContain "http://hubitat.local/apps/api/test-app-id/devices/1"
            }
        }
        
        test("should handle empty response body") {
            val hub = Device.Hub(1, "Test Hub")
            
            whenever(networkClient.getBody(eq("http://hubitat.local/apps/api/test-app-id/devices/1"), any()))
                .thenReturn("")
            
            try {
                HubOperations.getHubVersions(hub, networkClient, hubIp, makerApiAppId, makerApiToken)
                throw AssertionError("Expected exception to be thrown")
            } catch (e: Exception) {
                e.message shouldContain "Test Hub"
                e.message shouldContain "http://hubitat.local/apps/api/test-app-id/devices/1"
            }
        }
        
        test("should truncate long response preview to 200 characters") {
            val hub = Device.Hub(1, "Test Hub")
            
            val longResponse = "x".repeat(500)
            
            whenever(networkClient.getBody(eq("http://hubitat.local/apps/api/test-app-id/devices/1"), any()))
                .thenReturn(longResponse)
            
            try {
                HubOperations.getHubVersions(hub, networkClient, hubIp, makerApiAppId, makerApiToken)
                throw AssertionError("Expected exception to be thrown")
            } catch (e: Exception) {
                e.message shouldContain "..."
                e.message shouldContain "Response preview:"
            }
        }
    }
    
    context("updateHubsWithPolling") {
        test("should report all hubs up to date when no updates needed") {
            val hub1 = Device.Hub(1, "Hub 1")
            hub1.ip = "192.168.1.100"
            hub1.managementToken = "token1"
            
            val makerApiResponse = """
                {
                    "attributes": [
                        {"name": "firmwareVersionString", "currentValue": "2.3.4.150"},
                        {"name": "hubUpdateVersion", "currentValue": "2.3.4.150"}
                    ]
                }
            """.trimIndent()
            
            whenever(networkClient.getBody(eq("http://hubitat.local/apps/api/test-app-id/devices/1"), any()))
                .thenReturn(makerApiResponse)
            
            val messages = mutableListOf<String>()
            val result = HubOperations.updateHubsWithPolling(
                listOf(hub1),
                networkClient,
                hubIp,
                makerApiAppId,
                makerApiToken,
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
            
            val beforeUpdateResponse = """
                {
                    "attributes": [
                        {"name": "firmwareVersionString", "currentValue": "2.3.4.150"},
                        {"name": "hubUpdateVersion", "currentValue": "2.3.5.160"}
                    ]
                }
            """.trimIndent()
            
            val afterUpdateResponse = """
                {
                    "attributes": [
                        {"name": "firmwareVersionString", "currentValue": "2.3.5.160"},
                        {"name": "hubUpdateVersion", "currentValue": "2.3.5.160"}
                    ]
                }
            """.trimIndent()
            
            val mockResponse: HttpResponse = mock()
            whenever(mockResponse.status).thenReturn(HttpStatusCode.OK)
            
            whenever(networkClient.getBody(eq("http://hubitat.local/apps/api/test-app-id/devices/1"), any()))
                .thenReturn(beforeUpdateResponse)
                .thenReturn(afterUpdateResponse)
            
            whenever(networkClient.get(argThat { contains("/management/firmwareUpdate") }, any()))
                .thenReturn(mockResponse)
            
            val messages = mutableListOf<String>()
            val result = HubOperations.updateHubsWithPolling(
                listOf(hub1),
                networkClient,
                hubIp,
                makerApiAppId,
                makerApiToken,
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
            
            val makerApiResponse = """
                {
                    "attributes": [
                        {"name": "firmwareVersionString", "currentValue": "2.3.4.150"},
                        {"name": "hubUpdateVersion", "currentValue": "2.3.5.160"}
                    ]
                }
            """.trimIndent()
            
            val mockResponse: HttpResponse = mock()
            whenever(mockResponse.status).thenReturn(HttpStatusCode.OK)
            
            whenever(networkClient.getBody(eq("http://hubitat.local/apps/api/test-app-id/devices/1"), any()))
                .thenReturn(makerApiResponse)
            
            whenever(networkClient.get(argThat { contains("/management/firmwareUpdate") }, any()))
                .thenReturn(mockResponse)
            
            val messages = mutableListOf<String>()
            val result = HubOperations.updateHubsWithPolling(
                listOf(hub1),
                networkClient,
                hubIp,
                makerApiAppId,
                makerApiToken,
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
            
            val beforeUpdateResponse = """
                {
                    "attributes": [
                        {"name": "firmwareVersionString", "currentValue": "2.3.4.150"},
                        {"name": "hubUpdateVersion", "currentValue": "2.3.5.160"}
                    ]
                }
            """.trimIndent()
            
            val afterUpdateResponse = """
                {
                    "attributes": [
                        {"name": "firmwareVersionString", "currentValue": "2.3.5.160"},
                        {"name": "hubUpdateVersion", "currentValue": "2.3.5.160"}
                    ]
                }
            """.trimIndent()
            
            val mockResponse: HttpResponse = mock()
            whenever(mockResponse.status).thenReturn(HttpStatusCode.OK)
            
            whenever(networkClient.getBody(eq("http://hubitat.local/apps/api/test-app-id/devices/1"), any()))
                .thenReturn(beforeUpdateResponse)
                .thenReturn(afterUpdateResponse)
            
            whenever(networkClient.get(argThat { contains("/management/firmwareUpdate") }, any()))
                .thenReturn(mockResponse)
            
            val messages = mutableListOf<String>()
            HubOperations.updateHubsWithPolling(
                listOf(hub1),
                networkClient,
                hubIp,
                makerApiAppId,
                makerApiToken,
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
            
            val beforeUpdateResponse = """
                {
                    "attributes": [
                        {"name": "firmwareVersionString", "currentValue": "2.3.4.150"},
                        {"name": "hubUpdateVersion", "currentValue": "2.3.5.160"}
                    ]
                }
            """.trimIndent()
            
            val afterUpdateResponse = """
                {
                    "attributes": [
                        {"name": "firmwareVersionString", "currentValue": "2.3.5.160"},
                        {"name": "hubUpdateVersion", "currentValue": "2.3.5.160"}
                    ]
                }
            """.trimIndent()
            
            val mockResponse: HttpResponse = mock()
            whenever(mockResponse.status).thenReturn(HttpStatusCode.OK)
            
            whenever(networkClient.getBody(eq("http://hubitat.local/apps/api/test-app-id/devices/1"), any()))
                .thenReturn(beforeUpdateResponse)
                .thenReturn(afterUpdateResponse)
            
            whenever(networkClient.get(argThat { contains("/management/firmwareUpdate") }, any()))
                .thenReturn(mockResponse)
            
            val messages = mutableListOf<String>()
            HubOperations.updateHubsWithPolling(
                listOf(hub1),
                networkClient,
                hubIp,
                makerApiAppId,
                makerApiToken,
                maxAttempts = 5,
                delayMillis = 100
            ) { messages.add(it) }
            
            messages.any { it.contains("updated from 2.3.4.150 to 2.3.5.160") } shouldBe true
        }
    }
    
    context("Real Hub Integration Test").config(tags = setOf(RealHubTest), enabled = false) {
        test("should call real hub API and reproduce HTML parsing error") {
            // This test requires a real Hubitat hub to be available
            // Set the HUB_IP environment variable to run this test
            // Enable this test by removing 'enabled = false' from the context config
            // Example: HUB_IP=192.168.1.100 ./gradlew test --tests "*HubOperationsTest*"
            
            val hubIp = System.getenv("HUB_IP") ?: "192.168.1.100"
            
            val realClient = HttpClient(CIO)
            val networkClient = KtorNetworkClient(realClient)
            
            val hub = Device.Hub(1, "Real Test Hub")
            hub.ip = hubIp
            
            try {
                // This should now PASS with the correct Maker API endpoint
                val (current, available) = HubOperations.getHubVersions(
                    hub, networkClient, hubIp, makerApiAppId, makerApiToken
                )
                
                println("Current version: $current")
                println("Available version: $available")
                
                // If we get here, the endpoint returned valid JSON
                current.isNotEmpty() shouldBe true
            } catch (e: Exception) {
                // If this fails, check the error message for diagnostic info
                println("Error occurred: ${e.message}")
                throw e
            } finally {
                realClient.close()
            }
        }
    }
})
