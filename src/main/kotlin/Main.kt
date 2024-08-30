package jbaru.ch.telegram.hubitat

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.cio.*
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import jbaru.ch.telegram.hubitat.factories.DeviceRepositoryFactoryImpl
import jbaru.ch.telegram.hubitat.domain.*
import jbaru.ch.telegram.hubitat.models.Constants.Companion.CANCEL_ALERTS
import jbaru.ch.telegram.hubitat.models.Constants.Companion.CLOSE
import jbaru.ch.telegram.hubitat.models.Constants.Companion.OFF
import jbaru.ch.telegram.hubitat.models.Constants.Companion.ON
import jbaru.ch.telegram.hubitat.models.Constants.Companion.OPEN
import jbaru.ch.telegram.hubitat.models.Constants.Companion.UPDATE
import kotlinx.coroutines.runBlocking
import java.lang.System.getenv


internal val BOT_TOKEN = getenv("BOT_TOKEN") ?: throw IllegalStateException("BOT_TOKEN not set")
internal val MAKER_API_APP_ID = getenv("MAKER_API_APP_ID") ?: throw IllegalStateException("MAKER_API_APP_ID not set")
internal val MAKER_API_TOKEN = getenv("MAKER_API_TOKEN") ?: throw IllegalStateException("MAKER_API_TOKEN not set")

fun main() {
    val client = HttpClient(CIO) {}
    val deviceRepository = runBlocking {
        DeviceRepositoryFactoryImpl().create(
            client.get("http://hubitat.local/apps/api/${MAKER_API_APP_ID}/devices") {
                parameter("access_token", MAKER_API_TOKEN)
            }.body()
        )
    }
    val initHubs = InitHubsCommandImpl()
    val hubs = runBlocking { initHubs(InitHubsCommand.Param(client, deviceRepository)) }

    val bot = bot {
        token = BOT_TOKEN

        suspend fun CommandHandlerEnvironment.parseCommandWithArgs(command: String) {
            when (args.isEmpty()) {
                true -> "Specify what do you want to $command"
                false -> RunCommandOnDeviceImpl().invoke(
                    RunCommandOnDevice.Param(
                        deviceRepository,
                        client,
                        command,
                        args.joinToString(" "),
                    )
                )
            }.also {
                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = it)
            }
        }

        dispatch {
            command(ON) { parseCommandWithArgs(ON) }
            command(OFF) { parseCommandWithArgs(OFF) }
            command(OPEN) { parseCommandWithArgs(OPEN) }
            command(CLOSE) { parseCommandWithArgs(CLOSE) }

            command(CANCEL_ALERTS) {
                bot.sendMessage(
                    chatId = ChatId.fromId(message.chat.id),
                    text = RunCommandOnHsmImpl()
                        .invoke(
                            RunCommandOnHsm.Param(
                                client, "cancelAlerts"
                            )
                        )
                )
            }

            command(UPDATE) {
                bot.sendMessage(
                    chatId = ChatId.fromId(message.chat.id),
                    text = UpdateHubsCommandImpl().invoke(
                        UpdateHubsCommand.Param(
                            hubs,
                            client,
                        )
                    ).fold(
                        onSuccess = { it },
                        onFailure = {
                            it.printStackTrace()
                            it.message.toString()
                        }
                    ))
            }
        }
    }

    println("Init successful, $deviceRepository devices loaded, start polling")
    bot.startPolling()
}

