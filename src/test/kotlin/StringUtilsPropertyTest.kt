package jbaru.ch.telegram.hubitat

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

class StringUtilsPropertyTest : FunSpec({
    
    // **Feature: test-coverage-improvement, Property 5: Snake case to camel case conversion**
    test("snakeToCamelCase should capitalize first letter of each word after first and remove underscores").config(invocations = 100) {
        checkAll(Arb.string(1..50)) { input ->
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
