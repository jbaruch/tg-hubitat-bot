package jbaru.ch.telegram.hubitat

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import java.lang.System.getenv


private val HUBITAT_TOKEN = getenv("HUBITAT_TOKEN") ?: throw IllegalStateException("HUBITAT_TOKEN not set")
private val BOT_TOKEN = getenv("BOT_TOKEN") ?: throw IllegalStateException("BOT_TOKEN not set")
private val MAKER_URL = getenv("MAKER_URL") ?: throw IllegalStateException("MAKER_URL not set")

private val client = HttpClient(CIO)

private val deviceManager =
    runBlocking { DeviceManager(client.get("${MAKER_URL}/devices?access_token=$HUBITAT_TOKEN").body()) }

fun main() {
    val bot = bot {
        token = BOT_TOKEN

        dispatch {
            command("on") {
                (if (args.isEmpty()) {
                    "Specify what do you want to turn on"
                } else {
                    execute("on", args.joinToString(" "))
                }).also {
                    bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = it)
                }
            }

            command("off") {
                (if (args.isEmpty()) {
                    "Specify what do you want to turn off"
                } else {
                    execute("off", args.joinToString(" "))
                }).also {
                    bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = it)
                }
            }

            command("open") {
                (if (args.isEmpty()) {
                    "Specify what do you want to open"
                } else {
                    execute("on", args.joinToString(" "))
                }).also {
                    bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = it)
                }
            }

            command("close") {
                (if (args.isEmpty()) {
                    "Specify what do you want to close"
                } else {
                    execute("off", args.joinToString(" "))
                }).also {
                    bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = it)
                }
            }

        }
    }

    bot.startPolling()
}

suspend fun execute(command: String, device: String): String {
    deviceManager.findDeviceId(device)
        .onFailure {
            return it.message.toString()
        }.onSuccess { deviceId ->
            return client.get("${MAKER_URL}/devices/" + deviceId + "/$command?access_token=$HUBITAT_TOKEN").status.description
        }
    return ""
}

