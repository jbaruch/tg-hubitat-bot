package jbaru.ch.telegram.hubitat

import jbaru.ch.telegram.hubitat.model.Device
import jbaru.ch.telegram.hubitat.model.Hub
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class DeviceManagerTest {
    private lateinit var deviceManager: DeviceManager
    private lateinit var jsonContent: String

    @BeforeEach
    fun setUp() {
        val jsonFile = File("src/test/resources/hubitatDevice.json")
        assertTrue(jsonFile.exists(), "Test JSON file not found")
        jsonContent = jsonFile.readText()
        deviceManager = DeviceManager(jsonContent)
    }

    @Nested
    inner class DeviceLookupTests {
        @Test
        fun `find device by exact name and command`() {
            val result = deviceManager.findDevice("Button Device 1", "push")
            assertTrue(result.isSuccess)
            assertEquals(640, result.getOrNull()?.id)
        }

        @Test
        fun `find device by partial name returns error`() {
            val result = deviceManager.findDevice("nonexistent", "push")
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("No device found") == true)
        }

        @Test
        fun `find device with unsupported command returns error`() {
            val result = deviceManager.findDevice("Button Device 1", "unsupported")
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Command 'unsupported' is not supported") == true)
        }

        @Test
        fun `test finding devices by full name`() {
            val result = deviceManager.findDevice("Button Device 1", "push")
            assertTrue(result.isSuccess)
            assertEquals(640, result.getOrNull()?.id)
        }

        @Test
        fun `test finding devices by short name`() {
            val result = deviceManager.findDevice("Button Device 1", "push")
            assertTrue(result.isSuccess)
            assertEquals(640, result.getOrNull()?.id)
        }

        @Test
        fun `test finding devices by abbreviation`() {
            val result = deviceManager.findDevice("bd1", "push")
            assertTrue(result.isSuccess)
            assertEquals(640, result.getOrNull()?.id)
        }

        @Test
        fun `test not finding non-existent device`() {
            val result = deviceManager.findDevice("NonExistentDevice", "push")
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("No device found for query: NonExistentDevice") == true)
        }
    }

    @Nested
    inner class DeviceTypeTests {
        @Test
        fun `find devices by type returns correct devices`() {
            val virtualButtons = deviceManager.findDevicesByType(Device.VirtualButton::class.java)
            assertFalse(virtualButtons.isEmpty())
        }

        @Test
        fun `find devices by type with specific command`() {
            val result = deviceManager.findDevice("Button Device 1", "push")
            assertNotNull(result.getOrNull())
            assertTrue(result.getOrNull() is Device.VirtualButton)
        }

        @Test
        fun `list devices by type returns correct grouping`() {
            val devicesByType = deviceManager.listByType()
            assertFalse(devicesByType.isEmpty())
            assertTrue(devicesByType.containsKey("Buttons"))
            assertTrue(devicesByType.containsKey("Hubs"))
        }
    }

    @Nested
    inner class DeviceCommandTests {
        @Test
        fun `verify supported commands`() {
            assertTrue(deviceManager.isDeviceCommand("push"))
            assertTrue(deviceManager.isDeviceCommand("hold"))
            assertTrue(deviceManager.isDeviceCommand("deepReboot"))
            assertFalse(deviceManager.isDeviceCommand("nonexistentcommand"))
        }
    }

    @Nested
    inner class DeviceRefreshTests {
        @Test
        fun `refresh devices returns correct count`() {
            val (count, warnings) = deviceManager.refreshDevices(jsonContent)
            assertTrue(count > 0)
            assertTrue(warnings.isEmpty())
        }

        @Test
        fun `refresh devices with invalid json returns error`() {
            assertThrows<SerializationException> {
                deviceManager.refreshDevices("{invalid}")
            }
        }
    }

    @Nested
    inner class HubDeviceTests {
        @Test
        fun `find hub devices returns correct type`() {
            val hubs = deviceManager.findDevicesByType(Hub::class.java)
            assertFalse(hubs.isEmpty())
        }

        @Test
        fun `hub deep reboot command is supported`() {
            val hub = deviceManager.findDevicesByType(Hub::class.java).first()
            assertTrue(hub.supportedOps.containsKey("deepReboot"))
        }
    }
}