package jbaru.ch.telegram.hubitat

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class StringUtilsTest : FunSpec({
    
    test("snakeToCamelCase with single word should return unchanged") {
        "hello".snakeToCamelCase() shouldBe "hello"
    }
    
    test("snakeToCamelCase with empty string should return empty") {
        "".snakeToCamelCase() shouldBe ""
    }
    
    test("snakeToCamelCase with multiple underscores should handle correctly") {
        "hello_world_test".snakeToCamelCase() shouldBe "helloWorldTest"
    }
    
    test("snakeToCamelCase with two words should capitalize second") {
        "hello_world".snakeToCamelCase() shouldBe "helloWorld"
    }
    
    test("snakeToCamelCase with consecutive underscores should handle empty tokens") {
        "hello__world".snakeToCamelCase() shouldBe "helloWorld"
    }
})
