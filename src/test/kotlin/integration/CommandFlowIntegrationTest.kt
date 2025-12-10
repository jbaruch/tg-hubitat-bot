package jbaru.ch.telegram.hubitat.integration

import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.User
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import jbaru.ch.telegram.hubitat.*
import org.mockito.kotlin.mock

/**
 * Integration test for command flow from message to response.
 * Tests full flow with real DeviceManager and mocked network.
 */
class CommandFlowIntegrationTest : FunSpec({
    
    val devicesJson = """
        [
            {"id": 1, "label": "Living Room Light", "type": "Virtual Switch"},
            {"id": 2, "label": "Kitchen Light", "type": "Virtual Switch"},
            {"id": 3, "label": "Front Door", "type": "Generic Zigbee Contact Sensor"},
            {"id": 4, "label": "Back Door", "type": "Generic Zigbee Contact Sensor"}
        ]
    """.trimIndent()
    
    test("device command flow - successful execution") {
        val mockEngine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("/devices") -> {
                    // Device list endpoint
                    respond(
                        content = ByteReadChannel(devicesJson),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                request.url.encodedPath.contains("/devices/1/on") -> {
                    // Device command endpoint
                    respond(
                        content = ByteReadChannel(""),
                        status = HttpStatusCode.OK
                    )
                }
                else -> {
                    respond(
                        content = ByteReadChannel("Not found"),
                        status = HttpStatusCode.NotFound
                    )
                }
            }
        }
        
        val client = HttpClient(mockEngine)
        val networkClient = KtorNetworkClient(client)
        val deviceManager = DeviceManager(devicesJson)
        
        val bot = mock<com.github.kotlintelegrambot.Bot>()
        val message = Message(
            messageId = 1,
            date = 0,
            chat = Chat(id = 123, type = "private"),
            text = "/on lrl" // Using abbreviation for Living Room Light
        )
        
        val result = CommandHandlers.handleDeviceCommand(
            bot = bot,
            message = message,
            deviceManager = deviceManager,
            networkClient = networkClient,
            makerApiAppId = "test-app",
            makerApiToken = "test-token",
            defaultHubIp = "192.168.1.100"
        )
        
        result shouldBe "OK"
    }
    
    test("device command flow - device not found") {
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel(devicesJson),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val client = HttpClient(mockEngine)
        val networkClient = KtorNetworkClient(client)
        val deviceManager = DeviceManager(devicesJson)
        
        val bot = mock<com.github.kotlintelegrambot.Bot>()
        val message = Message(
            messageId = 1,
            date = 0,
            chat = Chat(id = 123, type = "private"),
            text = "/on xyz" // Non-existent device abbreviation
        )
        
        val result = CommandHandlers.handleDeviceCommand(
            bot = bot,
            message = message,
            deviceManager = deviceManager,
            networkClient = networkClient,
            makerApiAppId = "test-app",
            makerApiToken = "test-token",
            defaultHubIp = "192.168.1.100"
        )
        
        result.shouldContain("No device found")
    }
    
    test("list command flow - returns formatted device lists") {
        val deviceManager = DeviceManager(devicesJson)
        
        val deviceLists = CommandHandlers.handleListCommand(deviceManager)
        
        deviceLists.isNotEmpty() shouldBe true
        // Check that we have device lists
        deviceLists.values.any { it.contains("Living Room Light") } shouldBe true
        deviceLists.values.any { it.contains("Kitchen Light") } shouldBe true
    }
    
    test("refresh command flow - successful refresh") {
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel(devicesJson),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val client = HttpClient(mockEngine)
        val networkClient = KtorNetworkClient(client)
        val deviceManager = DeviceManager(devicesJson)
        
        val (count, warnings) = CommandHandlers.handleRefreshCommand(
            deviceManager = deviceManager,
            networkClient = networkClient,
            makerApiAppId = "test-app",
            makerApiToken = "test-token",
            defaultHubIp = "192.168.1.100"
        )
        
        count shouldBe 4
        warnings.isEmpty() shouldBe true
    }
    
    test("cancel alerts command flow - successful cancellation") {
        val mockEngine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("/hsm/cancelAlerts") -> {
                    respond(
                        content = ByteReadChannel(""),
                        status = HttpStatusCode.OK
                    )
                }
                else -> {
                    respond(
                        content = ByteReadChannel("Not found"),
                        status = HttpStatusCode.NotFound
                    )
                }
            }
        }
        
        val client = HttpClient(mockEngine)
        val networkClient = KtorNetworkClient(client)
        
        val result = CommandHandlers.handleCancelAlertsCommand(
            networkClient = networkClient,
            makerApiAppId = "test-app",
            makerApiToken = "test-token",
            defaultHubIp = "192.168.1.100"
        )
        
        result shouldBe "OK"
    }
    
    test("get open sensors command flow - with open sensors") {
        val mockEngine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("/devices/3/attribute/contact") -> {
                    respond(
                        content = ByteReadChannel("""{"value":"open"}"""),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                request.url.encodedPath.contains("/devices/4/attribute/contact") -> {
                    respond(
                        content = ByteReadChannel("""{"value":"closed"}"""),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> {
                    respond(
                        content = ByteReadChannel("Not found"),
                        status = HttpStatusCode.NotFound
                    )
                }
            }
        }
        
        val client = HttpClient(mockEngine)
        val networkClient = KtorNetworkClient(client)
        val deviceManager = DeviceManager(devicesJson)
        
        val result = CommandHandlers.handleGetOpenSensorsCommand(
            deviceManager = deviceManager,
            networkClient = networkClient,
            makerApiAppId = "test-app",
            makerApiToken = "test-token",
            defaultHubIp = "192.168.1.100"
        )
        
        result.shouldContain("Front Door")
        result.shouldNotContain("Back Door")
    }
    
    test("get open sensors command flow - no open sensors") {
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""{"value":"closed"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val client = HttpClient(mockEngine)
        val networkClient = KtorNetworkClient(client)
        val deviceManager = DeviceManager(devicesJson)
        
        val result = CommandHandlers.handleGetOpenSensorsCommand(
            deviceManager = deviceManager,
            networkClient = networkClient,
            makerApiAppId = "test-app",
            makerApiToken = "test-token",
            defaultHubIp = "192.168.1.100"
        )
        
        result shouldBe "No open sensors found."
    }
})
