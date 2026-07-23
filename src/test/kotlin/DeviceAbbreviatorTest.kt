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
        fun `test lookup is case-insensitive like storage`() {
            abbreviator.addName("Kitchen Light")
            abbreviator.abbreviate()

            assertEquals("kl", abbreviator.getAbbreviation("Kitchen Light").getOrThrow())
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

        @Test
        fun `test names that can never diverge terminate with failed abbreviations`() {
            // "az" collides with "ab" on the initial "a" and forces it to fully
            // expand to "ab" - which is exactly "a b"'s abbreviation, and both
            // are fully expanded so they can never diverge. Before the
            // stuck-group detection this looped forever and hung the boot.
            val stuck = DeviceAbbreviator()
            stuck.addName("a b")
            stuck.addName("ab")
            stuck.addName("az")
            stuck.addName("Kitchen Lights")

            stuck.abbreviate()

            val failure = stuck.getAbbreviation("a b")
            assertTrue(failure.isFailure)
            // The failure names the real cause, not "was not added".
            assertTrue(failure.exceptionOrNull()!!.message!!.contains("cannot be abbreviated"))
            assertTrue(stuck.getAbbreviation("ab").isFailure)
            // Unrelated names still abbreviate normally.
            assertEquals("az", stuck.getAbbreviation("az").getOrThrow())
            assertEquals("kl", stuck.getAbbreviation("kitchen lights").getOrThrow())
        }

        @Test
        fun `test exhausted first token position does not doom a group a later token can diverge`() {
            // After "az" forces "ab" to full expansion, the colliding group
            // {a b, a bx, a by, ab} differs at token 0 - but every token-0
            // abbreviation is already fully expanded there. Expansion must move
            // on to token 1, where "bx"/"by" can still diverge; only the truly
            // irreconcilable pair ("a b" vs "ab") may be dropped.
            val abbr = DeviceAbbreviator()
            abbr.addName("a b")
            abbr.addName("a bx")
            abbr.addName("a by")
            abbr.addName("ab")
            abbr.addName("az")

            abbr.abbreviate()

            assertEquals("abx", abbr.getAbbreviation("a bx").getOrThrow())
            assertEquals("aby", abbr.getAbbreviation("a by").getOrThrow())
            assertEquals("az", abbr.getAbbreviation("az").getOrThrow())
            assertTrue(abbr.getAbbreviation("a b").isFailure)
            assertTrue(abbr.getAbbreviation("ab").isFailure)
        }

        @Test
        fun `test stuck group does not block other collisions from resolving`() {
            val stuck = DeviceAbbreviator()
            stuck.addName("a b")
            stuck.addName("ab")
            stuck.addName("az")
            stuck.addName("Main Bedroom Lights")
            stuck.addName("Main Bathroom Lights")

            stuck.abbreviate()

            assertTrue(stuck.getAbbreviation("a b").isFailure)
            assertTrue(stuck.getAbbreviation("ab").isFailure)
            val bedroom = stuck.getAbbreviation("main bedroom lights").getOrThrow()
            val bathroom = stuck.getAbbreviation("main bathroom lights").getOrThrow()
            assertTrue(bedroom != bathroom)
        }
    }
}
