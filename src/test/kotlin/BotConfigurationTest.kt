package jbaru.ch.telegram.hubitat

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BotConfigurationTest : FunSpec({
    
    test("BotConfiguration should be created with custom values") {
        val config = BotConfiguration(
            botToken = "test-token",
            makerApiAppId = "test-app-id",
            makerApiToken = "test-api-token",
            chatId = "12345",
            defaultHubIp = "192.168.1.100"
        )
        
        config.botToken shouldBe "test-token"
        config.makerApiAppId shouldBe "test-app-id"
        config.makerApiToken shouldBe "test-api-token"
        config.chatId shouldBe "12345"
        config.defaultHubIp shouldBe "192.168.1.100"
    }
    
    test("BotConfiguration should use default values for optional fields") {
        val config = BotConfiguration(
            botToken = "test-token",
            makerApiAppId = "test-app-id",
            makerApiToken = "test-api-token"
        )
        
        config.chatId shouldBe ""
        config.defaultHubIp shouldBe "hubitat.local"
    }
})
