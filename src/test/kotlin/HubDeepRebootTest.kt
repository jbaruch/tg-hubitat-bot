package jbaru.ch.telegram.hubitat

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import jbaru.ch.telegram.hubitat.model.Hub
import jbaru.ch.telegram.hubitat.model.PowerControl
import jbaru.ch.telegram.hubitat.model.RebootConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.io.File

class HubDeepRebootTest {
    private lateinit var mockPowerControl: PowerControl
    private lateinit var deviceManager: DeviceManager
    private lateinit var jsonContent: String
    private lateinit var client: HttpClient

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupEnv() {
            System.setProperty("BOT_TOKEN", "test_token")
            System.setProperty("MAKER_API_APP_ID", "test_app_id")
            System.setProperty("MAKER_API_TOKEN", "test_api_token")
            System.setProperty("DEFAULT_HUB_IP", "hubitat.local")
            System.setProperty("EWELINK_EMAIL", "test@example.com")
            System.setProperty("EWELINK_PASSWORD", "test_password")
        }
    }

    @BeforeEach
    fun setUp() {
        mockPowerControl = mock()
        val jsonFile = File("src/test/resources/hubitatDevice.json")
        assertTrue(jsonFile.exists(), "Test JSON file not found")
        jsonContent = jsonFile.readText()
        deviceManager = DeviceManager(jsonContent)
        client = HttpClient(CIO)
        jbaru.ch.telegram.hubitat.client = client
        jbaru.ch.telegram.hubitat.deviceManager = deviceManager
    }

    @Test
    fun `deep reboot succeeds with configured power control`() = runBlocking {
        val messages = mutableListOf<String>()
        val device = deviceManager.findDevice("Hub Info 1", "deepReboot").getOrNull()
        assertTrue(device is Hub)
        val hub = device as Hub
        hub.powerControl = mockPowerControl

        whenever(mockPowerControl.deepReboot(any(), any(), any<suspend (String) -> Unit>(), any(), any())).thenReturn(Unit)

        val result = deepRebootHub("Hub Info 1") { msg -> messages.add(msg) }

        assertTrue(result.isSuccess)
        assertEquals("Starting deep reboot sequence for Hub Info 1...", messages[0])
        verify(mockPowerControl).deepReboot(eq(hub), eq(client), any(), any(), eq(RebootConfig()))
    }

    @Test
    fun `deep reboot fails with unconfigured power control`() = runBlocking {
        val messages = mutableListOf<String>()
        val result = deepRebootHub("Hub Info 1") { msg -> messages.add(msg) }

        assertTrue(result.isFailure)
        assertEquals("Configuring power control for Hub Info 1...", messages[0])
        assertInstanceOf(IllegalStateException::class.java, result.exceptionOrNull())
        assertEquals("Failed to configure power control for hub Hub Info 1", result.exceptionOrNull()?.message)
    }

    @Test
    fun `deep reboot fails with non-existent hub`() = runBlocking {
        val messages = mutableListOf<String>()
        val result = deepRebootHub("NonExistentHub") { msg -> messages.add(msg) }

        assertTrue(result.isFailure)
        assertInstanceOf(Exception::class.java, result.exceptionOrNull())
        assertEquals("No device found for query: NonExistentHub", result.exceptionOrNull()?.message)
    }

    @Test
    fun `deep reboot fails with non-hub device`() = runBlocking {
        val messages = mutableListOf<String>()
        val result = deepRebootHub("Button Device 1") { msg -> messages.add(msg) }

        assertTrue(result.isFailure)
        assertInstanceOf(IllegalArgumentException::class.java, result.exceptionOrNull())
        assertEquals("Command 'deepReboot' is not supported by device 'Button Device 1'", result.exceptionOrNull()?.message)
    }

    @Test
    fun `deep reboot fails when power control throws error`() = runBlocking {
        val messages = mutableListOf<String>()
        val device = deviceManager.findDevice("Hub Info 1", "deepReboot").getOrNull()
        assertTrue(device is Hub)
        val hub = device as Hub
        hub.powerControl = mockPowerControl

        val errorMessage = "Failed to initiate hub shutdown: Error"
        whenever(mockPowerControl.deepReboot(any(), any(), any<suspend (String) -> Unit>(), any(), any())).thenAnswer { invocation ->
            val callback = invocation.getArgument<suspend (String) -> Unit>(2)
            runBlocking { callback("❌ Deep reboot failed: $errorMessage") }
            throw RuntimeException(errorMessage)
        }

        val result = deepRebootHub("Hub Info 1") { msg -> messages.add(msg) }

        assertTrue(result.isFailure)
        assertEquals("Starting deep reboot sequence for Hub Info 1...", messages[0])
        assertEquals("❌ Deep reboot failed: $errorMessage", messages[1])
        assertInstanceOf(RuntimeException::class.java, result.exceptionOrNull())
        assertEquals(errorMessage, result.exceptionOrNull()?.message)
        verify(mockPowerControl).deepReboot(eq(hub), eq(client), any(), any(), eq(RebootConfig()))
    }
}
