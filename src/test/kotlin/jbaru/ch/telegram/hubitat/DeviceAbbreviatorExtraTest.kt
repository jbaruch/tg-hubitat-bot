package jbaru.ch.telegram.hubitat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DeviceAbbreviatorExtraTest {
    private lateinit var abbreviator: DeviceAbbreviator

    @BeforeEach
    fun setUp() {
        abbreviator = DeviceAbbreviator()
    }

    @Nested
    inner class PunctuationCases {
        @Test
        fun `handles hyphens and apostrophes`() {
            abbreviator.addName("Kid's Room Light")
            abbreviator.addName("Living-Room Light")
            abbreviator.abbreviate()

            val kids = abbreviator.getAbbreviation("kid's room light")
            val living = abbreviator.getAbbreviation("living-room light")

            assertTrue(kids.isSuccess)
            assertTrue(living.isSuccess)
            assertEquals("krl", kids.getOrNull())
            assertEquals("ll", living.getOrNull())
        }
    }

    @Nested
    inner class WhitespaceCases {
        @Test
        fun `double spaces do not crash abbreviation`() {
            abbreviator.addName("Kitchen  Lights")
            abbreviator.abbreviate()

            // Stored under the original lowercase key path used by callers;
            // tokens collapsed so abbreviation is still kl.
            val result = abbreviator.getAbbreviation("kitchen  lights")
            assertTrue(result.isSuccess)
            assertEquals("kl", result.getOrNull())
        }

        @Test
        fun `blank name is rejected rather than hanging or crashing later`() {
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                abbreviator.addName("   ")
            }
        }
    }
}
