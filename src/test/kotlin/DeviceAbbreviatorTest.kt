package jbaru.ch.telegram.hubitat

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DeviceAbbreviatorTest {
    private lateinit var abbreviator: DeviceAbbreviator

    @BeforeEach
    fun setUp() {
        abbreviator = DeviceAbbreviator()
    }

    @Nested
    inner class BasicFunctionality {
        @Test
        fun `test simple abbreviation`() {
            // Add a single device name
            abbreviator.addName("Kitchen Light")
            abbreviator.abbreviate()

            // Verify the abbreviation
            val result = abbreviator.getAbbreviation("kitchen light")
            assertTrue(result.isSuccess)
            assertEquals("kl", result.getOrNull())
        }

        @Test
        fun `test multiple abbreviations without collision`() {
            // Add multiple device names that don't collide
            abbreviator.addName("Kitchen Light")
            abbreviator.addName("Bedroom Light")
            abbreviator.addName("Living Room Light")
            abbreviator.abbreviate()

            // Verify the abbreviations
            assertEquals("kl", abbreviator.getAbbreviation("kitchen light").getOrNull())
            assertEquals("bl", abbreviator.getAbbreviation("bedroom light").getOrNull())
            assertEquals("lrl", abbreviator.getAbbreviation("living room light").getOrNull())
        }
    }

    @Nested
    inner class CollisionHandling {
        @Test
        fun `test abbreviation with collision`() {
            // Add device names that would collide
            abbreviator.addName("Main Bedroom Light")
            abbreviator.addName("Main Bathroom Light")
            abbreviator.abbreviate()

            // Verify the abbreviations are unique
            val mbl1 = abbreviator.getAbbreviation("main bedroom light").getOrNull()
            val mbl2 = abbreviator.getAbbreviation("main bathroom light").getOrNull()

            assertNotNull(mbl1)
            assertNotNull(mbl2)
            assertNotEquals(mbl1, mbl2)

            // Verify the abbreviations follow the expected pattern
            assertTrue(mbl1!!.startsWith("mb"))
            assertTrue(mbl2!!.startsWith("mb"))
        }
    }

    @Nested
    inner class ErrorHandling {
        @Test
        fun `test getting abbreviation for non-existent name`() {
            abbreviator.abbreviate()

            val result = abbreviator.getAbbreviation("Non-existent Device")
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }

        @Test
        fun `test adding name after abbreviate was called`() {
            abbreviator.abbreviate()

            val exception = assertThrows<IllegalStateException> {
                abbreviator.addName("New Device")
            }

            assertTrue(exception.message!!.contains("Cannot add more devices"))
        }
    }
}
