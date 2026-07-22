import jbaru.ch.telegram.hubitat.DeviceManager
import jbaru.ch.telegram.hubitat.model.Device
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeviceManagerTest {

    private lateinit var deviceManager: DeviceManager

    @BeforeEach
    fun setUp() {
        // Simplified JSON data for devices
        val deviceListJson = """
            [
                {"id": 1, "label": "Living Room Lights", "type": "Room Lights Activator Bulb"},
                {"id": 2, "label": "Kitchen Lights", "type": "Room Lights Activator Dimmer"},
                {"id": 3, "label": "Bedroom Light", "type": "Room Lights Activator Switch"},
                {"id": 4, "label": "Garage Door", "type": "Virtual Switch"},
                {"id": 5, "label": "Baruch Office Light", "type": "Room Lights Activator Shade"},
                {"id": 6, "label": "Master Bedroom Shades", "type": "Room Lights Activator Shade"},
                {"id": 7, "label": "Master Bedroom Lights", "type": "Room Lights Activator Switch"},
                {"id": 8, "label": "Master Bathroom Lights", "type": "Room Lights Activator Switch"},
                {"id": 9, "label": "Guest Bedroom Lights", "type": "Room Lights Activator Switch"}
            ]
        """.trimIndent()

        deviceManager = DeviceManager(deviceListJson)
    }

    @Test
    fun `test finding devices by full name`() {
        val result = deviceManager.findDevice("Living Room Lights", "on")
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.id)
    }

    @Test
    fun `test finding devices by short name`() {
        val result = deviceManager.findDevice("Kitchen", "off")
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.id)
    }

    @Test
    fun `test finding devices by abbreviation`() {
        val result = deviceManager.findDevice("lrl", "on")
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.id)
    }

    @Test
    fun `test not finding non-existent device`() {
        val result = deviceManager.findDevice("NonExistentDevice", "on")
        assertTrue(result.isFailure)
    }

    @Test
    fun `test sending correct command to device`() {
        val result = deviceManager.findDevice("Bedroom Light", "on")
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull()?.id)
    }

    @Test
    fun `test sending incorrect command to device`() {
        val result = deviceManager.findDevice("Bedroom Light", "open")
        assertTrue(result.isFailure)
        assertEquals("Command 'open' is not supported by device 'Bedroom Light'", result.exceptionOrNull()?.message)
    }

    @Test
    fun `test finding device but unsupported command`() {
        val result = deviceManager.findDevice("Garage Door", "dim")
        assertTrue(result.isFailure)
        assertEquals("Command 'dim' is not supported by device 'Garage Door'", result.exceptionOrNull()?.message)
    }

    @Test
    fun `test shade device with correct command`() {
        val result = deviceManager.findDevice("Baruch Office Light", "open")
        assertTrue(result.isSuccess)
        assertEquals(5, result.getOrNull()?.id)
    }

    @Test
    fun `test shade device with incorrect command`() {
        val result = deviceManager.findDevice("Master Bedroom Shades", "on")
        assertTrue(result.isFailure)
        assertEquals("Command 'on' is not supported by device 'Master Bedroom Shades'", result.exceptionOrNull()?.message)
    }

    @Test
    fun `test conflicting abbreviation does not exist`() {
        val result = deviceManager.findDevice("mbl", "on")
        assertTrue(result.isFailure)
        assertEquals("No device found for query: mbl", result.exceptionOrNull()?.message)
    }

    @Test
    fun `test finding shortest possible abbreviation`() {
        val masterBedroomLights = deviceManager.findDevice("mbel", "on")
        assertTrue(masterBedroomLights.isSuccess)
        assertEquals(7, masterBedroomLights.getOrNull()?.id)

        val guestBedroomLights = deviceManager.findDevice("gbl", command = "on")
        assertTrue(guestBedroomLights.isSuccess)
        assertEquals(9, guestBedroomLights.getOrNull()?.id)
    }
    
    @Test
    fun `test finding devices by type`() {
        val bulbs = deviceManager.findDevicesByType(Device.RoomLightsActivatorBulb::class.java)
        assertEquals(1, bulbs.size)
        assertEquals("Living Room Lights", bulbs.first().label)

        val switches = deviceManager.findDevicesByType(Device.VirtualSwitch::class.java)
        assertEquals(1, switches.size)
        assertEquals("Garage Door", switches.first().label)
    }

    @Test
    fun `test unknown device type is skipped with a warning instead of crashing`() {
        // A newly-installed driver with no @Serializable subclass must not take
        // the whole device list (and the bot) down at boot.
        val json = """
            [
                {"id": 1, "label": "Living Room Lights", "type": "Room Lights Activator Bulb"},
                {"id": 612, "label": "Backyard String Lights", "type": "Some Brand New Driver"},
                {"id": 4, "label": "Garage Door", "type": "Virtual Switch"}
            ]
        """.trimIndent()

        val manager = DeviceManager(json)
        val (count, warnings) = manager.refreshDevices(json)

        // The two known devices load; the unknown one is skipped, not fatal.
        assertEquals(2, count)
        assertTrue(warnings.any { it.contains("Some Brand New Driver") && it.contains("Backyard String Lights") })

        // Known devices remain usable.
        assertTrue(manager.findDevice("Living Room Lights", "on").isSuccess)
        assertTrue(manager.findDevice("Garage Door", "on").isSuccess)
        // The unknown device is absent.
        assertTrue(manager.findDevice("Backyard String Lights", "on").isFailure)
    }

    @Test
    fun `test malformed device element is skipped with a warning`() {
        // A structurally-broken entry (missing required fields) is skipped too.
        val json = """
            [
                {"id": 4, "label": "Garage Door", "type": "Virtual Switch"},
                {"type": "Virtual Switch"}
            ]
        """.trimIndent()

        val (count, warnings) = DeviceManager(json).refreshDevices(json)

        assertEquals(1, count)
        assertTrue(warnings.any { it.startsWith("WARNING Skipping unsupported device") })
    }

    @Test
    fun `test refresh updates the device command set`() {
        // Boot with only sensors: no actuator commands exist yet.
        val sensorsOnly = """
            [
                {"id": 1, "label": "Front Door", "type": "Generic Zigbee Contact Sensor"}
            ]
        """.trimIndent()
        val withActuator = """
            [
                {"id": 1, "label": "Front Door", "type": "Generic Zigbee Contact Sensor"},
                {"id": 2, "label": "Kitchen Lights", "type": "Virtual Switch"}
            ]
        """.trimIndent()

        val manager = DeviceManager(sensorsOnly)
        assertFalse(manager.isDeviceCommand("on"))

        manager.refreshDevices(withActuator)

        assertTrue(manager.isDeviceCommand("on"))
        assertTrue(manager.findDevice("kitchen lights", "on").isSuccess)
    }

    @Test
    fun `test duplicate labels surface in refresh warnings`() {
        val json = """
            [
                {"id": 1, "label": "Hall Light", "type": "Virtual Switch"},
                {"id": 2, "label": "Hall Light", "type": "Virtual Switch"}
            ]
        """.trimIndent()

        val (count, warnings) = DeviceManager(json).refreshDevices(json)

        assertEquals(2, count)
        assertTrue(warnings.any { it.contains("Duplicate key found in cache") })
    }

    @Test
    fun `test list tables escape backtick and backslash in labels`() {
        // The only two characters that can break a MarkdownV2 code fence.
        val json = """
            [
                {"id": 1, "label": "Weird `Tick` Device", "type": "Virtual Switch"},
                {"id": 2, "label": "Back\\slash Device", "type": "Virtual Switch"}
            ]
        """.trimIndent()

        val tables = DeviceManager(json).listByType()
        val actuators = tables.getValue("Actuators")

        assertTrue(actuators.contains("Weird \\`Tick\\` Device"))
        assertTrue(actuators.contains("Back\\\\slash Device"))
        assertFalse(actuators.contains("Weird `Tick` Device"))
    }
}
