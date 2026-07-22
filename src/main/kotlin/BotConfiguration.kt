package jbaru.ch.telegram.hubitat

import java.lang.System.getenv

data class BotConfiguration(
    val botToken: String,
    val makerApiAppId: String,
    val makerApiToken: String,
    val chatId: String = "",
    val defaultHubIp: String = "hubitat.local",
    val allowedChatIds: Set<Long> = emptySet()
) {
    /**
     * With an allowlist configured, only listed chats may command the bot
     * (fail closed). With no allowlist at all, the bot stays open to any chat
     * for backward compatibility - Main logs a loud warning in that case.
     */
    fun isChatAllowed(chatId: Long): Boolean =
        allowedChatIds.isEmpty() || chatId in allowedChatIds

    companion object {
        fun fromEnvironment(): BotConfiguration {
            val chatId = getenv("CHAT_ID") ?: ""
            return BotConfiguration(
                botToken = getenv("BOT_TOKEN") ?: throw IllegalStateException("BOT_TOKEN not set"),
                makerApiAppId = getenv("MAKER_API_APP_ID") ?: throw IllegalStateException("MAKER_API_APP_ID not set"),
                makerApiToken = getenv("MAKER_API_TOKEN") ?: throw IllegalStateException("MAKER_API_TOKEN not set"),
                chatId = chatId,
                defaultHubIp = getenv("DEFAULT_HUB_IP") ?: "hubitat.local",
                allowedChatIds = parseAllowedChatIds(chatId, getenv("ALLOWED_CHAT_IDS"))
            )
        }

        /**
         * The allowlist is the union of ALLOWED_CHAT_IDS (comma-separated) and
         * CHAT_ID. Both are validated at startup so a typo fails fast here,
         * not after all devices have already loaded.
         */
        internal fun parseAllowedChatIds(chatIdEnv: String?, allowlistEnv: String?): Set<Long> {
            val ids = mutableSetOf<Long>()
            if (!chatIdEnv.isNullOrBlank()) {
                ids.add(
                    chatIdEnv.trim().toLongOrNull()
                        ?: throw IllegalStateException("CHAT_ID is not a valid chat id: '$chatIdEnv'")
                )
            }
            allowlistEnv?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.forEach { entry ->
                ids.add(
                    entry.toLongOrNull()
                        ?: throw IllegalStateException("ALLOWED_CHAT_IDS contains an invalid chat id: '$entry'")
                )
            }
            return ids
        }
    }
}
