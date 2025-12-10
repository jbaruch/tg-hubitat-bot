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
}
