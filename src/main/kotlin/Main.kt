package jbaru.ch.telegram.hubitat

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import jbaru.ch.telegram.hubitat.model.Device
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.lang.System.getenv
import com.github.kotlintelegrambot.logging.LogLevel
import com.github.kotlintelegrambot.entities.ParseMode.MARKDOWN_V2
import com.github.kotlintelegrambot.extensions.filters.Filter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText

private val logger = org.slf4j.LoggerFactory.getLogger("jbaru.ch.telegram.hubitat.Main")
private lateinit var config: BotConfiguration
private lateinit var hubs: List<Device.Hub>
private val client = HttpClient(CIO) {
    // Never let a hung or rebooting hub block a command handler forever.
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
    }
}
private lateinit var networkClient: NetworkClient
private lateinit var deviceManager: DeviceManager
// Resolved via getMe() at startup; used to validate /cmd@BotName mentions.
@Volatile private var botUsername: String? = null

fun main() {
    config = BotConfiguration.fromEnvironment()
    networkClient = KtorNetworkClient(client)

    deviceManager = runBlocking {
        DeviceManager(getDevicesJson())
    }
    hubs = runBlocking { 
        HubOperations.initializeHubs(
            deviceManager, networkClient, config.defaultHubIp,
            config.makerApiAppId, config.makerApiToken
        )
    }

    val bot = bot {
        token = config.botToken
        logLevel = LogLevel.Network.Basic

        dispatch {

            message(DeviceCommandFilter({ deviceManager })) {
                if (!isAuthorized(message)) return@message
                replyTo(bot, message) {
                    CommandHandlers.handleDeviceCommand(
                        bot, message, deviceManager, networkClient,
                        config.makerApiAppId, config.makerApiToken, config.defaultHubIp
                    )
                }
            }

            command("cancel_alerts") {
                if (!isAuthorized(message)) return@command
                replyTo(bot, message) {
                    CommandHandlers.handleCancelAlertsCommand(
                        networkClient, config.makerApiAppId, config.makerApiToken, config.defaultHubIp
                    )
                }
            }

            command("update") {
                if (!isAuthorized(message)) return@command
                replyTo(bot, message) {
                    HubOperations.updateHubsWithPolling(
                        hubs,
                        networkClient,
                        config.defaultHubIp,
                        config.makerApiAppId,
                        config.makerApiToken,
                        progressCallback = { progressMessage ->
                            bot.sendMessage(
                                chatId = ChatId.fromId(message.chat.id),
                                text = progressMessage
                            )
                        }
                    ).fold(
                        onSuccess = { it },
                        onFailure = {
                            // The per-hub detail already reached the chat via
                            // progressCallback; the exception text can carry
                            // internal URLs, so it stays in the logs.
                            logger.error("Hub update failed", it)
                            "Hub update failed. See the progress messages above; details are in the bot logs."
                        }
                    )
                }
            }

            command("firmware") {
                if (!isAuthorized(message)) return@command
                val chatId = ChatId.fromId(message.chat.id)
                val messages = runBlocking {
                    try {
                        FirmwareOperations.checkFirmware(hubs, networkClient)
                    } catch (e: Exception) {
                        logger.error("Firmware check failed", e)
                        listOf("Firmware check failed: ${e.message}")
                    }
                }
                messages.forEach { bot.sendMessage(chatId = chatId, text = it) }
            }

            command("refresh") {
                if (!isAuthorized(message)) return@command
                replyTo(bot, message) {
                    val results = CommandHandlers.handleRefreshCommand(
                        deviceManager, networkClient,
                        config.makerApiAppId, config.makerApiToken, config.defaultHubIp
                    )
                    // Re-initialize hubs too, so a hub added/changed since boot is
                    // picked up (and its ip/managementToken refreshed) without a restart.
                    hubs = HubOperations.initializeHubs(
                        deviceManager, networkClient, config.defaultHubIp,
                        config.makerApiAppId, config.makerApiToken
                    )
                    "Refresh finished, ${results.first} devices loaded. Warnings: ${results.second}"
                }
            }

            command("list") {
                if (!isAuthorized(message)) return@command
                val chatId = ChatId.fromId(message.chat.id)
                try {
                    val deviceLists = runBlocking {
                        CommandHandlers.handleListCommand(deviceManager)
                    }
                    deviceLists.forEach { (type, table) ->
                        bot.sendMessage(
                            chatId = chatId,
                            text = "*$type*:\n$table",
                            parseMode = MARKDOWN_V2
                        )
                    }
                } catch (e: Exception) {
                    // outer-boundary-process-contract: Telegram dispatcher
                    // boundary (multi-message handler, so it cannot use
                    // replyTo). Silent-failure shape: an escaping exception
                    // dies in the dispatcher and the user gets no reply.
                    // Emitted response: a short generic error; details stay in
                    // the logs. Propagation would break the every-command-
                    // answers contract.
                    logger.error("List command failed", e)
                    bot.sendMessage(chatId = chatId, text = "Something went wrong listing devices. Check the bot logs for details.")
                }
            }

            command("get_open_sensors") {
                if (!isAuthorized(message)) return@command
                replyTo(bot, message) {
                    CommandHandlers.handleGetOpenSensorsCommand(
                        deviceManager, networkClient,
                        config.makerApiAppId, config.makerApiToken, config.defaultHubIp
                    )
                }
            }

            command("get_mode") {
                if (!isAuthorized(message)) return@command
                replyTo(bot, message) {
                    CommandHandlers.handleGetModeCommand(
                        networkClient, config.makerApiAppId,
                        config.makerApiToken, config.defaultHubIp
                    )
                }
            }

            command("list_modes") {
                if (!isAuthorized(message)) return@command
                replyTo(bot, message) {
                    CommandHandlers.handleListModesCommand(
                        networkClient, config.makerApiAppId,
                        config.makerApiToken, config.defaultHubIp
                    )
                }
            }

            command("set_mode") {
                if (!isAuthorized(message)) return@command
                replyTo(bot, message) {
                    CommandHandlers.handleSetModeCommand(
                        message, networkClient, config.makerApiAppId,
                        config.makerApiToken, config.defaultHubIp
                    )
                }
            }
        }
    }

    botUsername = bot.getMe().getOrNull()?.username
    if (botUsername == null) {
        logger.warn("Could not resolve the bot's username via getMe(); mention-form commands (/cmd@BotName) will be ignored")
    }

    if (config.allowedChatIds.isEmpty()) {
        logger.warn(
            "No ALLOWED_CHAT_IDS or CHAT_ID configured - the bot will accept commands from ANY chat. " +
                "Set ALLOWED_CHAT_IDS to lock it down."
        )
    }

    val startupMessage = "Init successful, ${deviceManager.deviceCount} devices loaded, start polling"
    logger.info(startupMessage)
    if (config.chatId.isNotEmpty()) {
        bot.sendMessage(
            chatId = ChatId.fromId(config.chatId.toLong()),
            text = startupMessage
        )
    }
    bot.startPolling()
}

private suspend fun getDevicesJson(): String =
    networkClient.getBody(
        "http://${config.defaultHubIp}/apps/api/${config.makerApiAppId}/devices",
        mapOf("access_token" to config.makerApiToken)
    )


/**
 * Runs a handler body and always answers the chat: an uncaught exception from
 * a handler used to die inside the dispatcher, leaving the user staring at a
 * bot that looks dead whenever the hub or network hiccuped.
 */
private fun replyTo(bot: Bot, message: Message, block: suspend () -> String) {
    val text = try {
        runBlocking { block() }
    } catch (e: Exception) {
        // outer-boundary-process-contract: Telegram dispatcher boundary.
        // Silent-failure shape: an exception escaping a handler dies inside
        // the dispatcher thread and the user gets no reply at all - the bot
        // looks dead. Emitted response: a short generic error (exception
        // details, which can carry internal URLs, stay in the logs). Letting
        // it propagate would break the contract that every command answers
        // the chat.
        logger.error("Handler for '${message.text}' failed", e)
        val command = message.text?.split(" ")?.firstOrNull() ?: "command"
        "Something went wrong handling $command. Check the bot logs for details."
    }
    bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = text)
}

private fun isAuthorized(message: Message): Boolean {
    val allowed = config.isChatAllowed(message.chat.id)
    if (!allowed) {
        // Log only the command token: full text from a stranger is both a
        // privacy leak and a log-spam vector.
        logger.warn(
            "Dropping command from unauthorized chat {} (user {}): {}",
            message.chat.id, message.from?.id, message.text?.split(" ")?.firstOrNull()?.take(64)
        )
    }
    return allowed
}

class DeviceCommandFilter(
    private val deviceManagerProvider: () -> DeviceManager,
    private val botUsernameProvider: () -> String? = { botUsername }
) : Filter {
    override fun Message.predicate(): Boolean {
        // Only explicit commands qualify: a leading slash is required so plain
        // chat text ("on kitchen") is never interpreted as a device command.
        val firstToken = text?.split(" ")?.firstOrNull() ?: return false
        if (!firstToken.startsWith("/")) return false
        // In group chats commands arrive as /on@BotName. A mention addressed
        // to a DIFFERENT bot (privacy mode off delivers those too) must not
        // drive this one; if our own username is unknown, reject mention forms
        // rather than guess.
        val mention = firstToken.substringAfter("@", "")
        if (mention.isNotEmpty() && !mention.equals(botUsernameProvider(), ignoreCase = true)) {
            return false
        }
        val command = firstToken.removePrefix("/").substringBefore("@")
        return command.isNotEmpty() && deviceManagerProvider().isDeviceCommand(command.snakeToCamelCase())
    }
}
