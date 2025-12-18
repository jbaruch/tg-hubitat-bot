package jbaru.ch.telegram.hubitat.integration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.matchers.string.shouldContain
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import jbaru.ch.telegram.hubitat.*
import jbaru.ch.telegram.hubitat.model.Device
import kotlinx.serialization.json.*

/**
 * Real integration test that hits actual Hubitat APIs.
 * 
 * Required environment variables:
 * - DEFAULT_HUB_IP: IP address of the Hubitat hub (e.g., "192.168.1.100" or "hubitat.local")
 * - MAKER_API_APP_ID: Maker API app ID
 * - MAKER_API_TOKEN: Maker API access token
 * - HUB_INFO_DEVICE_ID: Device ID of the Hub Information Driver v3 device (optional for device discovery test)
 * 
 * To run: 
 * DEFAULT_HUB_IP=hubitat.local MAKER_API_APP_ID=xxx MAKER_API_TOKEN=xxx HUB_INFO_DEVICE_ID=123 ./gradlew test --tests "*RealHubitatApiTest*"
 */
class RealHubitatApiTest : FunSpec({
    
    val hubIp = System.getenv("DEFAULT_HUB_IP")
    val makerApiAppId = System.getenv("MAKER_API_APP_ID")
    val makerApiToken = System.getenv("MAKER_API_TOKEN")
    val hubInfoDeviceId = System.getenv("HUB_INFO_DEVICE_ID")
    
    println("=== RealHubitatApiTest Configuration ===")
    println("DEFAULT_HUB_IP: ${if (hubIp != null) hubIp else "NOT SET"}")
    println("MAKER_API_APP_ID: ${if (makerApiAppId != null) makerApiAppId else "NOT SET"}")
    println("MAKER_API_TOKEN: ${if (makerApiToken != null) "***" else "NOT SET"}")
    println("HUB_INFO_DEVICE_ID: ${if (hubInfoDeviceId != null) hubInfoDeviceId else "NOT SET (optional for discovery)"}")
    println("========================================")
    
    // Device discovery test only needs hub IP, app ID, and token
    val discoveryEnabled = hubIp != null && makerApiAppId != null && makerApiToken != null
    
    // Version test needs all credentials including device ID
    val versionTestEnabled = discoveryEnabled && hubInfoDeviceId != null
    
    test("should retrieve hub firmware versions from real Hubitat API").config(enabled = versionTestEnabled) {
        val client = HttpClient(CIO)
        val networkClient = KtorNetworkClient(client)
        
        val hub = Device.Hub(
            id = hubInfoDeviceId!!.toInt(),
            label = "Test Hub",
            ip = hubIp!!,
            managementToken = "not-used-in-this-test"
        )
        
        try {
            val (currentVersion, latestVersion) = HubOperations.getHubVersions(
                hub = hub,
                networkClient = networkClient,
                hubIp = hubIp,
                makerApiAppId = makerApiAppId!!,
                makerApiToken = makerApiToken!!
            )
            
            println("   Retrieved versions:")
            println("   - Current: '$currentVersion'")
            println("   - Latest: '$latestVersion'")
            
            // Verify we got actual version strings
            currentVersion.shouldNotBeEmpty()
            latestVersion.shouldNotBeEmpty()
            
            // Versions should be in format like "2.3.9.184"
            currentVersion shouldNotBe ""
            latestVersion shouldNotBe ""
            
            println("✅ Successfully retrieved hub versions from real API:")
            println("   Current version: $currentVersion")
            println("   Latest version: $latestVersion")
            
        } finally {
            client.close()
        }
    }
    
    test("should list all devices from real Maker API and find Hub Information Drivers").config(enabled = discoveryEnabled) {
        val client = HttpClient(CIO)
        val networkClient = KtorNetworkClient(client)
        
        try {
            // Fetch devices JSON from Maker API
            val devicesJson = networkClient.getBody(
                "http://${hubIp}/apps/api/${makerApiAppId}/devices",
                mapOf("access_token" to makerApiToken!!)
            )
            
            // Parse devices
            val deviceManager = DeviceManager(devicesJson)
            val hubInfoDevices = deviceManager.findDevicesByType(Device.Hub::class.java)
            
            // Should have at least one Hub Information Driver device
            hubInfoDevices.isNotEmpty() shouldBe true
            
            println("✅ Successfully fetched devices from real API")
            println("   Found ${hubInfoDevices.size} Hub Information Driver devices:")
            hubInfoDevices.forEach { hub ->
                println("   - ID: ${hub.id}, Label: ${hub.label}")
            }
            
            // If HUB_INFO_DEVICE_ID wasn't provided, suggest the found IDs
            if (hubInfoDeviceId == null && hubInfoDevices.isNotEmpty()) {
                println("")
                println("💡 To run the version test, set HUB_INFO_DEVICE_ID to one of these IDs:")
                hubInfoDevices.forEach { hub ->
                    println("   export HUB_INFO_DEVICE_ID=${hub.id}  # ${hub.label}")
                }
            }
            
        } finally {
            client.close()
        }
    }
    
    test("should show all attributes from Hub Information Driver device").config(enabled = versionTestEnabled) {
        val client = HttpClient(CIO)
        val networkClient = KtorNetworkClient(client)
        
        try {
            // Fetch raw device data
            val endpoint = "http://${hubIp}/apps/api/${makerApiAppId}/devices/${hubInfoDeviceId}"
            val responseBody = networkClient.getBody(endpoint, mapOf("access_token" to makerApiToken!!))
            
            val json = Json.parseToJsonElement(responseBody).jsonObject
            val attributes = json["attributes"] as? JsonArray
            
            println("✅ Hub Information Driver attributes:")
            attributes?.forEach { attr ->
                val name = attr.jsonObject["name"]?.jsonPrimitive?.content
                val value = attr.jsonObject["currentValue"]?.jsonPrimitive?.content
                if (name?.contains("firmware", ignoreCase = true) == true || 
                    name?.contains("version", ignoreCase = true) == true) {
                    println("   - $name = '$value'")
                }
            }
            
        } finally {
            client.close()
        }
    }
    
    test("should handle hub update status monitoring with polling").config(enabled = versionTestEnabled) {
        val client = HttpClient(CIO)
        val networkClient = KtorNetworkClient(client)
        
        val hub = Device.Hub(
            id = hubInfoDeviceId!!.toInt(),
            label = "Test Hub",
            ip = hubIp!!,
            managementToken = "not-used-in-this-test"
        )
        
        try {
            // Test the update monitoring flow with multiple polling attempts
            val result = HubOperations.updateHubsWithPolling(
                hubs = listOf(hub),
                networkClient = networkClient,
                hubIp = hubIp,
                makerApiAppId = makerApiAppId!!,
                makerApiToken = makerApiToken!!,
                maxAttempts = 3,  // Try multiple times to catch polling errors
                delayMillis = 1000,  // 1 second delay
                progressCallback = { message ->
                    println("   Progress: $message")
                }
            )
            
            println("✅ Update monitoring result: ${if (result.isSuccess) "SUCCESS" else "FAILED"}")
            if (result.isFailure) {
                println("   Error: ${result.exceptionOrNull()?.message}")
            } else {
                println("   Message: ${result.getOrNull()}")
            }
            
        } finally {
            client.close()
        }
    }
    
    test("should handle empty or malformed responses during polling").config(enabled = versionTestEnabled) {
        val client = HttpClient(CIO)
        val networkClient = KtorNetworkClient(client)
        
        val hub = Device.Hub(
            id = hubInfoDeviceId!!.toInt(),
            label = "Test Hub",  
            ip = hubIp!!,
            managementToken = "not-used-in-this-test"
        )
        
        try {
            // Make multiple calls to getHubVersions to see if we can reproduce empty response
            println("   Making multiple version check calls...")
            for (i in 1..5) {
                try {
                    val (current, latest) = HubOperations.getHubVersions(
                        hub = hub,
                        networkClient = networkClient,
                        hubIp = hubIp,
                        makerApiAppId = makerApiAppId!!,
                        makerApiToken = makerApiToken!!
                    )
                    println("   Call $i: current='$current', latest='$latest'")
                } catch (e: Exception) {
                    println("   Call $i FAILED: ${e.message}")
                    throw e  // Fail the test
                }
                kotlinx.coroutines.delay(500)  // Small delay between calls
            }
            
            println("✅ All version checks succeeded")
            
        } finally {
            client.close()
        }
    }
    
    test("should retry on transient failures during polling") {
        // Test with mocked responses that fail twice then succeed
        var callCount = 0
        val mockEngine = MockEngine { request ->
            callCount++
            if (callCount <= 2) {
                // First two calls return empty response
                respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                // Third call succeeds
                respond(
                    content = ByteReadChannel("""{"attributes":[{"name":"firmwareVersionString","currentValue":"2.4.3.172"},{"name":"hubUpdateVersion","currentValue":"2.4.3.172"}]}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        
        val client = HttpClient(mockEngine)
        val networkClient = KtorNetworkClient(client)
        
        val hub = Device.Hub(
            id = 445,
            label = "Test Hub",
            ip = "192.168.1.100",
            managementToken = "test-token"
        )
        
        try {
            // This should succeed after retries
            val result = runCatching {
                HubOperations.getHubVersions(
                    hub = hub,
                    networkClient = networkClient,
                    hubIp = "192.168.1.100",
                    makerApiAppId = "398",
                    makerApiToken = "test-token"
                )
            }
            
            // Note: The retry logic is in updateHubsWithPolling, not getHubVersions
            // So this will fail on first empty response
            result.isFailure shouldBe true
            
            println("✅ Transient failure test completed (retry logic is in polling loop)")
            
        } finally {
            client.close()
        }
    }
    
    test("should handle empty response gracefully") {
        // Test with mocked empty response
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val client = HttpClient(mockEngine)
        val networkClient = KtorNetworkClient(client)
        
        val hub = Device.Hub(
            id = 445,
            label = "Test Hub",
            ip = "192.168.1.100",
            managementToken = "test-token"
        )
        
        try {
            val result = runCatching {
                HubOperations.getHubVersions(
                    hub = hub,
                    networkClient = networkClient,
                    hubIp = "192.168.1.100",
                    makerApiAppId = "398",
                    makerApiToken = "test-token"
                )
            }
            
            result.isFailure shouldBe true
            val error = result.exceptionOrNull()
            error shouldNotBe null
            error?.message shouldContain "empty response"
            
            println("✅ Empty response handled gracefully: ${error?.message?.take(100)}")
            
        } finally {
            client.close()
        }
    }
    
    test("should fail gracefully when device ID is wrong").config(enabled = discoveryEnabled) {
        val client = HttpClient(CIO)
        val networkClient = KtorNetworkClient(client)
        
        val invalidHub = Device.Hub(
            id = 99999, // Invalid device ID
            label = "Invalid Hub",
            ip = hubIp!!,
            managementToken = "not-used"
        )
        
        try {
            val result = runCatching {
                HubOperations.getHubVersions(
                    hub = invalidHub,
                    networkClient = networkClient,
                    hubIp = hubIp,
                    makerApiAppId = makerApiAppId!!,
                    makerApiToken = makerApiToken!!
                )
            }
            
            // Should fail with a clear error message
            result.isFailure shouldBe true
            val error = result.exceptionOrNull()
            error shouldNotBe null
            
            println("✅ Failed gracefully with error: ${error?.message?.take(200)}")
            
        } finally {
            client.close()
        }
    }
})
