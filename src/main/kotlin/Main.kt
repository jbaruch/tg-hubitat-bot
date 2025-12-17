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

private lateinit var config: BotConfiguration
private lateinit var hubs: List<Device.Hub>
private val client = HttpClient(CIO)
private lateinit var networkClient: NetworkClient
private lateinit var deviceManager: DeviceManager

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

            message(DeviceCommandFilter()) {
                val result = runBlocking {
                    CommandHandlers.handleDeviceCommand(
                        bot, message, deviceManager, networkClient,
                        config.makerApiAppId, config.makerApiToken, config.defaultHubIp
                    )
                }
                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = result)
            }

            command("cancel_alerts") {
                val result = runBlocking {
                    CommandHandlers.handleCancelAlertsCommand(
                        networkClient, config.makerApiAppId, config.makerApiToken, config.defaultHubIp
                    )
                }
                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = result)
            }
            
            command("update") {
                val result = runBlocking {
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
                    )
                }
                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = result.fold(
                    onSuccess = { it },
                    onFailure = {
                        it.printStackTrace()
                        it.message.toString()
                    }
                ))
            }
            
            command("refresh") {
                val refreshResults = runBlocking {
                    CommandHandlers.handleRefreshCommand(
                        deviceManager, networkClient,
                        config.makerApiAppId, config.makerApiToken, config.defaultHubIp
                    )
                }
                bot.sendMessage(
                    chatId = ChatId.fromId(message.chat.id),
                    text = "Refresh finished, ${refreshResults.first} devices loaded. Warnings: ${refreshResults.second}"
                )
            }
            
            command("list") {
                val chatId = ChatId.fromId(message.chat.id)
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
            }

            command("get_open_sensors") {
                val response = runBlocking {
                    CommandHandlers.handleGetOpenSensorsCommand(
                        deviceManager, networkClient,
                        config.makerApiAppId, config.makerApiToken, config.defaultHubIp
                    )
                }
                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = response)
            }
            
            command("get_mode") {
                val result = runBlocking {
                    CommandHandlers.handleGetModeCommand(
                        networkClient, config.makerApiAppId,
                        config.makerApiToken, config.defaultHubIp
                    )
                }
                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = result)
            }
            
            command("list_modes") {
                val result = runBlocking {
                    CommandHandlers.handleListModesCommand(
                        networkClient, config.makerApiAppId,
                        config.makerApiToken, config.defaultHubIp
                    )
                }
                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = result)
            }
            
            command("set_mode") {
                val result = runBlocking {
                    CommandHandlers.handleSetModeCommand(
                        message, networkClient, config.makerApiAppId,
                        config.makerApiToken, config.defaultHubIp
                    )
                }
                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = result)
            }
        }
    }

    println("Init successful, $deviceManager devices loaded, start polling")
    if (config.chatId.isNotEmpty()) {
        bot.sendMessage(
            chatId = ChatId.fromId(config.chatId.toLong()),
            text = "Init successful, $deviceManager devices loaded, start polling"
        )
    }
    bot.startPolling()
}

private suspend fun getDevicesJson(): String =
    networkClient.getBody(
        "http://${config.defaultHubIp}/apps/api/${config.makerApiAppId}/devices",
        mapOf("access_token" to config.makerApiToken)
    )


class DeviceCommandFilter : Filter {
    override fun Message.predicate(): Boolean {
        val command = text?.split(" ")?.firstOrNull()?.removePrefix("/")
        return command != null && deviceManager.isDeviceCommand(command.snakeToCamelCase())
    }
}
