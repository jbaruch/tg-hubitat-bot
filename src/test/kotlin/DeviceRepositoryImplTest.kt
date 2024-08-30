import jbaru.ch.telegram.hubitat.DeviceRepository
import jbaru.ch.telegram.hubitat.commands.DeviceRepositoryFactoryImpl
import jbaru.ch.telegram.hubitat.model.Device
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeviceRepositoryImplTest {

    private lateinit var deviceManagerImpl: DeviceRepository

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
                {"id": 6, "label": "Master Bedroom Shades", "type": "Room Lights Activator Shade"}
            ]
        """.trimIndent()

        deviceManagerImpl = DeviceRepositoryFactoryImpl().create(deviceListJson)
    }

    @Test
    fun `test finding devices by full name`() {
        val result = deviceManagerImpl.findDevice("Living Room Lights", "on")
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.id)
    }

    @Test
    fun `test finding devices by short name`() {
        val result = deviceManagerImpl.findDevice("Kitchen", "off")
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.id)
    }

    @Test
    fun `test finding devices by abbreviation`() {
        val result = deviceManagerImpl.findDevice("lrl", "on")
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.id)
    }

    @Test
    fun `test not finding non-existent device`() {
        val result = deviceManagerImpl.findDevice("NonExistentDevice", "on")
        assertTrue(result.isFailure)
    }

    @Test
    fun `test sending correct command to device`() {
        val result = deviceManagerImpl.findDevice("Bedroom Light", "on")
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull()?.id)
    }

    @Test
    fun `test sending incorrect command to device`() {
        val result = deviceManagerImpl.findDevice("Bedroom Light", "open")
        assertTrue(result.isFailure)
        assertEquals("Command 'open' is not supported by device 'Bedroom Light'", result.exceptionOrNull()?.message)
    }

    @Test
    fun `test finding device but unsupported command`() {
        val result = deviceManagerImpl.findDevice("Garage Door", "dim")
        assertTrue(result.isFailure)
        assertEquals("Command 'dim' is not supported by device 'Garage Door'", result.exceptionOrNull()?.message)
    }

    @Test
    fun `test shade device with correct command`() {
        val result = deviceManagerImpl.findDevice("Baruch Office Light", "open")
        assertTrue(result.isSuccess)
        assertEquals(5, result.getOrNull()?.id)
    }

    @Test
    fun `test shade device with incorrect command`() {
        val result = deviceManagerImpl.findDevice("Master Bedroom Shades", "on")
        assertTrue(result.isFailure)
        assertEquals("Command 'on' is not supported by device 'Master Bedroom Shades'", result.exceptionOrNull()?.message)
    }

    @Test
    fun `test finding devices by type`() {
        val bulbs = deviceManagerImpl.findDevicesByType(Device.RoomLightsActivatorBulb::class.java)
        assertEquals(1, bulbs.size)
        assertEquals("Living Room Lights", bulbs.first().label)

        val switches = deviceManagerImpl.findDevicesByType(Device.VirtualSwitch::class.java)
        assertEquals(1, switches.size)
        assertEquals("Garage Door", switches.first().label)
    }
}