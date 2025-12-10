package jbaru.ch.telegram.hubitat

import java.lang.System.getenv

data class BotConfiguration(
    val botToken: String,
    val makerApiAppId: String,
    val makerApiToken: String,
    val chatId: String = "",
    val defaultHubIp: String = "hubitat.local"
) {
    companion object {
        fun fromEnvironment(): BotConfiguration {
            return BotConfiguration(
                botToken = getenv("BOT_TOKEN") ?: throw IllegalStateException("BOT_TOKEN not set"),
                makerApiAppId = getenv("MAKER_API_APP_ID") ?: throw IllegalStateException("MAKER_API_APP_ID not set"),
                makerApiToken = getenv("MAKER_API_TOKEN") ?: throw IllegalStateException("MAKER_API_TOKEN not set"),
                chatId = getenv("CHAT_ID") ?: "",
                defaultHubIp = getenv("DEFAULT_HUB_IP") ?: "hubitat.local"
            )
        }
    }
}
