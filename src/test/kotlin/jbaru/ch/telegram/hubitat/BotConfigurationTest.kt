package jbaru.ch.telegram.hubitat

import io.kotest.assertions.throwables.shouldThrow
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
        config.allowedChatIds shouldBe emptySet()
    }

    test("empty allowlist allows any chat (backward compatible)") {
        val config = BotConfiguration("t", "a", "m")
        config.isChatAllowed(12345L) shouldBe true
        config.isChatAllowed(-99L) shouldBe true
    }

    test("configured allowlist fails closed for unlisted chats") {
        val config = BotConfiguration("t", "a", "m", allowedChatIds = setOf(111L, 222L))
        config.isChatAllowed(111L) shouldBe true
        config.isChatAllowed(222L) shouldBe true
        config.isChatAllowed(333L) shouldBe false
    }

    test("parseAllowedChatIds unions CHAT_ID and ALLOWED_CHAT_IDS") {
        BotConfiguration.parseAllowedChatIds("111", "222, 333") shouldBe setOf(111L, 222L, 333L)
    }

    test("parseAllowedChatIds handles missing values") {
        BotConfiguration.parseAllowedChatIds(null, null) shouldBe emptySet()
        BotConfiguration.parseAllowedChatIds("", null) shouldBe emptySet()
        BotConfiguration.parseAllowedChatIds(null, "42") shouldBe setOf(42L)
        BotConfiguration.parseAllowedChatIds("-100123", null) shouldBe setOf(-100123L)
    }

    test("parseAllowedChatIds fails fast on garbage CHAT_ID") {
        shouldThrow<IllegalStateException> {
            BotConfiguration.parseAllowedChatIds("not-a-number", null)
        }
    }

    test("parseAllowedChatIds fails fast on garbage allowlist entry") {
        shouldThrow<IllegalStateException> {
            BotConfiguration.parseAllowedChatIds(null, "111,oops")
        }
    }
})
