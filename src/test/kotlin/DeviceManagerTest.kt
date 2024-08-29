package jbaru.ch.telegram.hubitat

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeviceManagerTest {

    private lateinit var deviceManager: DeviceManager

    @BeforeEach
    fun setUp() {
        val deviceListJson = """[
            {"id": 1, "label": "Living Room Lights"},
            {"id": 2, "label": "Kitchen Lights"},
            {"id": 3, "label": "Bedroom Light"},
            {"id": 4, "label": "Garage Door"},
            {"id": 5, "label": "Baruch Office Light"}
        ]"""
        deviceManager = DeviceManager(deviceListJson)
    }

    @Test
    fun `test find device by full name`() {
        val result = deviceManager.findDevice("Living Room Lights")
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
    }

    @Test
    fun `test find device by name without Light or Lights`() {
        val result = deviceManager.findDevice("Living Room")
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
    }

    @Test
    fun `test find device by exact match`() {
        val result = deviceManager.findDevice("Garage Door")
        assertTrue(result.isSuccess)
        assertEquals(4, result.getOrNull())
    }

    @Test
    fun `test find device with case insensitivity`() {
        val result = deviceManager.findDevice("kitchen lights")
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())
    }

    @Test
    fun `test find non-existent device`() {
        val result = deviceManager.findDevice("Non Existent Device")
        assertFalse(result.isSuccess)
    }

    @Test
    fun `test find device by camel case abbreviation`() {
        val result = deviceManager.findDevice("LRL")
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
    }

    @Test
    fun `test find device by abbreviation case insensitivity`() {
        val result = deviceManager.findDevice("kl")
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())
    }

    @Test
    fun `test find non-existent abbreviation`() {
        val result = deviceManager.findDevice("XYZ")
        assertFalse(result.isSuccess)
    }
}