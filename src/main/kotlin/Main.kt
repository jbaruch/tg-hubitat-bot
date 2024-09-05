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



private val BOT_TOKEN = getenv("BOT_TOKEN") ?: throw IllegalStateException("BOT_TOKEN not set")
private val MAKER_API_APP_ID = getenv("MAKER_API_APP_ID") ?: throw IllegalStateException("MAKER_API_APP_ID not set")
private val MAKER_API_TOKEN = getenv("MAKER_API_TOKEN") ?: throw IllegalStateException("MAKER_API_TOKEN not set")
private val CHAT_ID = getenv("CHAT_ID") ?: ""

private lateinit var hubs: List<Device.Hub>

private val client = HttpClient(CIO)

private lateinit var deviceManager: DeviceManager

fun main() {

    deviceManager = runBlocking {
        DeviceManager(getDevicesJson())
    }
    hubs = runBlocking { initHubs() }

    val bot = bot {
        token = BOT_TOKEN
        logLevel = LogLevel.Network.Basic
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
            command("reboot") {parseCommandWithArgs("reboot") }

            command("cancel_alerts") {
                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = runCommandOnHsm("cancelAlerts"))
            }

            command("update") {
                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = updateHubs().fold(
                    onSuccess = { it },
                    onFailure = {
                        it.printStackTrace()
                        it.message.toString()
                    }
                ))
            }
            command("refresh") {
                val refreshResults = deviceManager.refreshDevices(getDevicesJson())
                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = "Refresh finished, ${refreshResults.first} devices loaded. Warnings: ${refreshResults.second}")
            }
        }
    }

    println("Init successful, $deviceManager devices loaded, start polling")
    if(CHAT_ID != "") {
        bot.sendMessage(
            chatId = ChatId.fromId(CHAT_ID.toLong()),
            text = "Init successful, $deviceManager devices loaded, start polling"
        )
    }
    bot.startPolling()
}

private suspend fun getDevicesJson(): String = client.get("http://hubitat.local/apps/api/${MAKER_API_APP_ID}/devices") {
    parameter("access_token", MAKER_API_TOKEN)
}.body()

suspend fun runCommandOnDevice(command: String, device: String): String =
    deviceManager.findDevice(device, command).fold(
        onSuccess = { device ->
            client.get("http://hubitat.local/apps/api/${MAKER_API_APP_ID}/devices/${device.id}/$command") {
                parameter("access_token", MAKER_API_TOKEN)
            }.status.description
        },
        onFailure = {
            it.printStackTrace()
            it.message.toString()
        }
    )

suspend fun runCommandOnHsm(command: String): String {
    return client.get("http://hubitat.local/apps/api/${MAKER_API_APP_ID}/hsm/$command") {
        parameter("access_token", MAKER_API_TOKEN)
    }.status.description
}

suspend fun updateHubs(): Result<String> {
    val statusMap = mutableMapOf<String, HttpStatusCode>()

    for (hub in hubs) {
        try {
            val response = client.get("http://${hub.ip}/management/firmwareUpdate") {
                parameter("token", hub.managementToken)
            }
            statusMap[hub.label] = response.status
        } catch (_: Exception) {
            statusMap[hub.label] = HttpStatusCode.InternalServerError
        }
    }
    val failures = statusMap.filterValues { it != HttpStatusCode.OK }
    return if (failures.isEmpty()) {
        Result.success("All hub updates initialized successfully.")
    } else {
        val failureMessages = failures.entries.joinToString("\n") { (name, status) ->
            "Failed to update hub $name Status: $status"
        }
        val successMessages = statusMap.filterValues { it == HttpStatusCode.OK }
            .entries.joinToString("\n") { (name, _) ->
                "Successfully issued update request to hub $name"
            }
        Result.failure(Exception("$failureMessages\n$successMessages"))
    }
}

private suspend fun initHubs(): List<Device.Hub> {
    val hubs = deviceManager.findDevicesByType(Device.Hub::class.java)
    for (hub in hubs) {
        val json: Map<String, JsonElement> =
            Json.parseToJsonElement(client.get("http://hubitat.local/apps/api/${MAKER_API_APP_ID}/devices/${hub.id}") {
                parameter("access_token", MAKER_API_TOKEN)
            }.body<String>()).jsonObject

        val ip = (json["attributes"] as JsonArray).find {
            it.jsonObject["name"]!!.jsonPrimitive.content.toString() == "localIP"
        }!!.jsonObject["currentValue"]!!.jsonPrimitive.content.toString()
        hub.ip = ip
        hub.managementToken = client.get("http://${ip}/hub/advanced/getManagementToken").body()
    }
    return hubs
}
