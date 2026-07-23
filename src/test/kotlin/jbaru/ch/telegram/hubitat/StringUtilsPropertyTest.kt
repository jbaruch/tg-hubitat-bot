package jbaru.ch.telegram.hubitat
import io.kotest.property.arbitrary.string

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll

// Property tests run with a FIXED seed: the exploration stays property-based
// but the generated cases are deterministic and reproducible - no
// self-generated randomness in CI, no jacoco coverage-gate flakiness.
private const val FIXED_SEED = 20260722L

class StringUtilsPropertyTest : FunSpec({
    
    // **Feature: test-coverage-improvement, Property 5: Snake case to camel case conversion**
    test("snakeToCamelCase capitalizes each word after the first and removes underscores")
        .config(invocations = 100) {
        checkAll(PropTestConfig(seed = FIXED_SEED), Arb.string(1..50)) { input ->
            val snakeCase = input.replace(" ", "_").lowercase()
            val result = snakeCase.snakeToCamelCase()
            
            // Should not contain underscores
            result shouldNotContain "_"
            
            // Should start with lowercase (first word unchanged)
            val words = snakeCase.split("_")
            if (words.isNotEmpty() && words[0].isNotEmpty()) {
                result.startsWith(words[0]) shouldBe true
            }
            
            // Each word after the first should be capitalized
            if (words.size > 1) {
                words.drop(1).forEach { word ->
                    if (word.isNotEmpty()) {
                        result.contains(word.replaceFirstChar(Char::titlecase)) shouldBe true
                    }
                }
            }
        }
    }
})
