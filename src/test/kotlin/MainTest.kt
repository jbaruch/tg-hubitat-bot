package jbaru.ch.telegram.hubitat

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.Message
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import jbaru.ch.telegram.hubitat.model.Hub
import jbaru.ch.telegram.hubitat.model.PowerControl
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class MainTest {
    private lateinit var mockEngine: MockEngine
    private lateinit var mockClient: HttpClient
    private lateinit var mockMessage: Message

    @BeforeEach
    fun setup() {
        // Mock the bot
        val mockBot = mock<Bot>()
        whenever(mockBot.sendMessage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(mock())
        bot = mockBot
        
        // Create mock message
        val mockChat = mock<Chat> {
            on { id } doReturn CHAT_ID.toLong()
        }
        mockMessage = mock {
            on { chat } doReturn mockChat
        }

        // Setup mock HTTP client
        mockEngine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("/apps/api") && request.url.parameters["access_token"] != null -> {
                    val path = request.url.encodedPath
                    when {
                        path.contains("firmwareVersionString") -> respond(
                            content = "2.3.4.126",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "text/plain")
                        )
                        else -> respond(
                            content = "OK",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "text/plain")
                        )
                    }
                }
                else -> respond(
                    content = "Not found",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            }
        }
        mockClient = HttpClient(mockEngine)
        client = mockClient
    }

    @Test
    fun `deepRebootHub handles non-existent hub`() = runBlocking {
        // Setup device manager with empty list
        deviceManager = DeviceManager("[]")

        val messages = mutableListOf<String>()
        val result = deepRebootHub("nonexistent") { msg -> messages.add(msg) }
        
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error?.message?.contains("No device found for query: nonexistent") == true)
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `deepRebootHub handles non-hub device`() = runBlocking {
        // Add a non-hub device to device manager
        val deviceListJson = """[{
            "id": 1,
            "label": "Test Switch",
            "type": "Virtual Switch",
            "supportedOps": {"deepReboot": 0, "on": 0, "off": 0}
        }]"""
        deviceManager = DeviceManager(deviceListJson)

        // Print the device cache contents
        println("Device cache contents:")
        deviceManager.toString().lines().forEach { println(it) }

        val messages = mutableListOf<String>()
        val result = deepRebootHub("test switch") { msg -> messages.add(msg) }
        
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertNotNull(error)
        println("Expected error message: Device 'Test Switch' is not a hub")
        println("Actual error message: ${error?.message}")
        assertTrue(error?.message?.contains("Device 'Test Switch' is not a hub") == true, "Actual error message: ${error?.message}")
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `deepRebootHub handles successful deep reboot`() = runBlocking {
        // Setup a hub with mocked power control
        val hubJson = """[{
            "id": 1,
            "label": "Test Hub",
            "type": "Hub Information Driver v3"
        }]"""
        deviceManager = DeviceManager(hubJson)
        
        // Set up power control
        deviceManager.findDevice("test hub", "deepReboot").getOrNull()?.let {
            (it as Hub).powerControl = mock()
        }

        val messages = mutableListOf<String>()
        val result = deepRebootHub("test hub") { msg -> messages.add(msg) }
        
        assertTrue(result.isSuccess)
        assertTrue(messages.isNotEmpty())
        assertEquals("Starting deep reboot sequence for Test Hub...", messages[0])
    }

    @Test
    fun `deepRebootHub handles power control configuration failure`() = runBlocking {
        // Setup a hub without power control
        val hubJson = """[{
            "id": 1,
            "label": "Test Hub",
            "type": "Hub Information Driver v3"
        }]"""
        deviceManager = DeviceManager(hubJson)
        
        val messages = mutableListOf<String>()
        val result = deepRebootHub("test hub") { msg -> messages.add(msg) }
        
        assertTrue(result.isFailure)
        assertEquals(1, messages.size)
        assertEquals("Configuring power control for Test Hub...", messages[0])
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to configure power control") == true)
    }

    @Test
    fun `deepRebootHub handles deep reboot failure`() = runBlocking {
        // Setup a hub with mocked power control that throws an exception
        val hubJson = """[{
            "id": 1,
            "label": "Test Hub",
            "type": "Hub Information Driver v3"
        }]"""
        deviceManager = DeviceManager(hubJson)
        
        // Set up failing power control
        val mockPowerControl = mock<PowerControl>()
        doAnswer { throw RuntimeException("Failed to reboot") }
            .whenever(mockPowerControl)
            .deepReboot(any(), any(), any(), any())
        
        deviceManager.findDevice("test hub", "deepReboot").getOrNull()?.let {
            (it as Hub).powerControl = mockPowerControl
        }

        val messages = mutableListOf<String>()
        val result = deepRebootHub("test hub") { msg -> messages.add(msg) }
        
        assertTrue(result.isFailure)
        assertEquals("Starting deep reboot sequence for Test Hub...", messages[0])
        assertTrue(result.exceptionOrNull()?.message?.contains("Error during deep reboot: Failed to reboot") == true)
    }
}
