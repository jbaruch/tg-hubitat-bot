package jbaru.ch.telegram.hubitat

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import jbaru.ch.telegram.hubitat.model.Hub
import kotlinx.coroutines.runBlocking
import java.lang.System.getenv


private val BOT_TOKEN = getenv("BOT_TOKEN") ?: throw IllegalStateException("BOT_TOKEN not set")
private val MAKER_URL = getenv("MAKER_URL") ?: throw IllegalStateException("MAKER_URL not set")

private val hubs = initHubs()

private val client = HttpClient(CIO)

private lateinit var deviceManager: DeviceManager

fun main() {
    val bot = bot {
        token = BOT_TOKEN
        deviceManager = runBlocking {
            DeviceManager(client.get("http://${hubs["Apps"]?.ip}/${MAKER_URL}/devices") {
                parameter(
                    "access_token",
                    hubs["Apps"]?.applicationToken
                )
            }.body())
        }

        suspend fun CommandHandlerEnvironment.parseCommandWithArgs(command: String) {
            (if (args.isEmpty()) {
                "Specify what do you want to $command"
            } else {
                runCommandOnDevice(command, args.joinToString(" "))
            }).also {
                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = it)
            }
        }

        dispatch {
            command("on") { parseCommandWithArgs("on") }
            command("off") { parseCommandWithArgs("off") }
            command("open") { parseCommandWithArgs("open") }
            command("close") { parseCommandWithArgs("close") }

            command("cancel_alerts") {
                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = runCommandOnHsm("cancelAlerts"))
            }

            command("update") {
                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = updateHubs().toString())
            }
        }
    }

    bot.startPolling()
}

suspend fun runCommandOnDevice(command: String, device: String): String =
    deviceManager.findDevice(device, command).fold(
        onSuccess = { device ->
            client.get("http://${hubs["Apps"]?.ip}/${MAKER_URL}/devices/${device.id}/$command") {
                parameter(
                    "access_token",
                    hubs["Apps"]?.applicationToken
                )
            }.status.description
        },
        onFailure = {
            it.message.toString()
        }
    )

suspend fun runCommandOnHsm(command: String): String {
    return client.get("http://${hubs["Apps"]?.ip}/${MAKER_URL}/hsm/$command") {
        parameter(
            "access_token",
            hubs["Apps"]?.applicationToken
        )
    }.status.description
}

suspend fun updateHubs(): Result<String> {
    val statusMap = mutableMapOf<String, HttpStatusCode>()

    hubs.values.forEach { hub ->
        try {
            val response = client.get("http://${hub.ip}/management/firmwareUpdate") {
                parameter("token", hub.managementToken)
            }
            statusMap[hub.name] = response.status
        } catch (e: Exception) {
            statusMap[hub.name] = HttpStatusCode.InternalServerError
        }
    }
    val failures = statusMap.filterValues { it != HttpStatusCode.OK }
    return if (failures.isEmpty()) {
        Result.success("All hub updates initialized successfully.")
    } else {
        val failureMessages = failures.entries.joinToString("\n") { (name, status) ->
            "Failed to update hub '${name}' at ${hubs[name]?.ip}. Status: $status"
        }
        val successMessages = statusMap.filterValues { it == HttpStatusCode.OK }
            .entries.joinToString("\n") { (name, _) ->
                "Successfully updated hub '${name}' at ${hubs[name]?.ip}"
            }
        Result.failure(Exception("$failureMessages\n$successMessages"))
    }
}

private fun initHubs(): Map<String, Hub> = mapOf<String, Hub>(
    "Devices" to Hub(
        "Devices",
        getenv("DEVICES_HUB_IP") ?: throw IllegalStateException("DEVICES_HUB_IP not set"),
        "NA",
        getenv("DEVICES_HUB_MANAGEMENT_TOKEN") ?: throw IllegalStateException("DEVICES_HUB_MANAGEMENT_TOKEN not set")
    ),
    "Apps" to Hub(
        "Apps",
        getenv("APPS_HUB_IP") ?: throw IllegalStateException("DEVICES_HUB_IP not set"),
        getenv("APPS_HUB_APPLICATION_TOKEN") ?: throw IllegalStateException("APPS_HUB_APPLICATION_TOKEN not set"),
        getenv("APPS_HUB_MANAGEMENT_TOKEN") ?: throw IllegalStateException("APPS_HUB_MANAGEMENT_TOKEN not set")
    ),
    "Bits and Pieces" to Hub(
        "Bits and Pieces",
        getenv("BITS_AND_PIECES_HUB_IP") ?: throw IllegalStateException("BITS_AND_PIECES_HUB_IP not set"),
        "NA",
        getenv("BITS_AND_PIECES_HUB_MANAGEMENT_TOKEN")
            ?: throw IllegalStateException("BITS_AND_PIECES_HUB_MANAGEMENT_TOKEN not set")
    ),
)
