package jbaru.ch.telegram.hubitat

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class DeviceAbbreviatorPropertyTest : FunSpec({
    
    // **Feature: test-coverage-improvement, Property 4: Abbreviation uniqueness**
    test("abbreviation algorithm should produce unique abbreviations for distinct names").config(invocations = 50) {
        val abbreviator = DeviceAbbreviator()
        val testNames = listOf("kitchen light", "living room light", "bedroom light")
        
        testNames.forEach { abbreviator.addName(it) }
        abbreviator.abbreviate()
        
        val abbreviations = testNames.map { name ->
            abbreviator.getAbbreviation(name).getOrThrow()
        }
        
        // All abbreviations should be unique
        abbreviations.size shouldBe abbreviations.distinct().size
    }
})
