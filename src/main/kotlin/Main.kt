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
import java.util.Locale

private val BOT_TOKEN = getenv("BOT_TOKEN") ?: throw IllegalStateException("BOT_TOKEN not set")
private val MAKER_API_APP_ID = getenv("MAKER_API_APP_ID") ?: throw IllegalStateException("MAKER_API_APP_ID not set")
private val MAKER_API_TOKEN = getenv("MAKER_API_TOKEN") ?: throw IllegalStateException("MAKER_API_TOKEN not set")
private val CHAT_ID = getenv("CHAT_ID") ?: ""
private val DEFAULT_HUB_IP = getenv("DEFAULT_HUB_IP") ?: "hubitat.local"

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

        dispatch {

            message(DeviceCommandFilter()) {
                handleDeviceCommand(bot, message)
            }

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
            command("list") {
                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = deviceManager.list(), parseMode = MARKDOWN_V2)
            }
            command("shutdown_restart") {
                //find hubs with z-wave on and iterate on  them

                //var zWaveHubs =  deviceManager.findZWaveEnabledHubs()
                //zWaveHubs.foreach{
    //                runDeviceCommand(it, "shutdown")
    //                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = "Shutting down, please wait for graceful shutdown.")
    //                TimeUnit.MINUTES.sleep(1)
    //                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = "Cutting power, please wait for radios reset.")
    //                TimeUnit.MINUTES.sleep(1)
    //                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = "Restarting hub.")
                //}
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

fun String.snakeToCamelCase(): String {
    return split("_").mapIndexed { index, s ->
        if (index == 0) s else replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }.joinToString("")
}

private suspend fun getDevicesJson(): String = client.get("http://${DEFAULT_HUB_IP}/apps/api/${MAKER_API_APP_ID}/devices") {
    parameter("access_token", MAKER_API_TOKEN)
}.body()


suspend fun handleDeviceCommand(bot: Bot, message: Message) {
    val parts = message.text?.split(" ") ?: return
    if (parts.size < 2) {
        bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = "Please specify a device name for the command.")
        return
    }

    val snakeCaseCommand = parts[0].removePrefix("/")
    val camelCaseCommand = snakeCaseCommand.snakeToCamelCase()
    val deviceName = parts[1]
    val args = parts.drop(2)

    val result = deviceManager.findDevice(deviceName, camelCaseCommand).fold(
        onSuccess = { device ->
            val argCount = device.supportedOps[camelCaseCommand]
            if (argCount == null) {
                "Command '/$snakeCaseCommand' is not supported by device '${device.label}'"
            } else if (args.size != argCount) {
                "Invalid number of arguments for /$snakeCaseCommand. Expected $argCount argument(s)."
            } else {
                runDeviceCommand(device, camelCaseCommand, args)
            }
        },
        onFailure = {
            it.printStackTrace()
            it.message.toString()
        }
    )

    bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = result)
}

suspend fun runDeviceCommand(device: Device, command: String, args: List<String>): String {
    val commandPath = buildString {
        append("/apps/api/${MAKER_API_APP_ID}/devices/${device.id}/$command")
        if (args.isNotEmpty()) {
            append("/${args.joinToString("/")}")
        }
    }

    return client.get("http://${DEFAULT_HUB_IP}$commandPath") {
        parameter("access_token", MAKER_API_TOKEN)
    }.status.description
}

suspend fun runCommandOnHsm(command: String): String {
    return client.get("http://${DEFAULT_HUB_IP}/apps/api/${MAKER_API_APP_ID}/hsm/$command") {
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
            Json.parseToJsonElement(client.get("http://${DEFAULT_HUB_IP}/apps/api/${MAKER_API_APP_ID}/devices/${hub.id}") {
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


class DeviceCommandFilter : Filter {
    override fun Message.predicate(): Boolean {
        val command = text?.split(" ")?.firstOrNull()?.removePrefix("/")
        return command != null && deviceManager.isDeviceCommand(command.snakeToCamelCase())
    }
}
