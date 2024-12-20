package jbaru.ch.telegram.hubitat

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode.MARKDOWN_V2
import com.github.kotlintelegrambot.extensions.filters.Filter
import com.github.kotlintelegrambot.logging.LogLevel
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import jbaru.ch.telegram.hubitat.model.Device
import jbaru.ch.telegram.hubitat.model.Hub
import jbaru.ch.telegram.hubitat.model.HubPowerManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.lang.System.getenv
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

private val BOT_TOKEN = getenv("BOT_TOKEN") ?: throw IllegalStateException("BOT_TOKEN not set")
private val MAKER_API_APP_ID = getenv("MAKER_API_APP_ID") ?: throw IllegalStateException("MAKER_API_APP_ID not set")
private val MAKER_API_TOKEN = getenv("MAKER_API_TOKEN") ?: throw IllegalStateException("MAKER_API_TOKEN not set")
internal val CHAT_ID = getenv("CHAT_ID") ?: throw IllegalStateException("CHAT_ID environment variable is not set")
private val DEFAULT_HUB_IP = getenv("DEFAULT_HUB_IP") ?: "hubitat.local"
private val EWELINK_EMAIL = getenv("EWELINK_EMAIL") ?: throw IllegalStateException("EWELINK_EMAIL not set")
private val EWELINK_PASSWORD = getenv("EWELINK_PASSWORD") ?: throw IllegalStateException("EWELINK_PASSWORD not set")


internal var hubs: List<Hub> = emptyList()

internal var client = HttpClient(CIO)

private lateinit var deviceManager: DeviceManager
internal lateinit var bot: Bot

fun main() {

    deviceManager = runBlocking {
        DeviceManager(getDevicesJson())
    }
    hubs = runBlocking { initHubs() }

    val eweLinkSession = EweLinkSession(
        email = EWELINK_EMAIL, password = EWELINK_PASSWORD
    )

    val hubPowerManager = HubPowerManager(eweLinkSession)

    // Configure power control for hubs
    hubs.forEach { hub ->
        try {
            hubPowerManager.configureHubPower(hub)
            println("Configured deep reboot capability for ${hub.label}")
        } catch (e: IllegalStateException) {
            println("Note: ${hub.label} will not have deep reboot capability: ${e.message}")
        }
    }

    bot = bot {
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
                updateHubs(
                    sendMessage = { msg ->
                        bot.sendMessage(
                            chatId = ChatId.fromId(message.chat.id),
                            text = msg
                        )
                    }
                ).fold(
                    onSuccess = { result ->
                        bot.sendMessage(
                            chatId = ChatId.fromId(message.chat.id),
                            text = result
                        )
                    },
                    onFailure = { error ->
                        error.printStackTrace()
                        bot.sendMessage(
                            chatId = ChatId.fromId(message.chat.id),
                            text = error.message.toString()
                        )
                    }
                )
            }
            command("refresh") {
                val refreshResults = deviceManager.refreshDevices(getDevicesJson())
                bot.sendMessage(
                    chatId = ChatId.fromId(message.chat.id),
                    text = "Refresh finished, ${refreshResults.first} devices loaded. Warnings: ${refreshResults.second}"
                )
            }
            command("list") {
                val chatId = ChatId.fromId(message.chat.id)
                deviceManager.listByType().forEach { (type, table) ->
                    bot.sendMessage(
                        chatId = chatId,
                        text = "*$type*:\n$table",
                        parseMode = MARKDOWN_V2
                    )
                }
            }

            command("deep_reboot") {
                val chatId = ChatId.fromId(message.chat.id)
                if (message.text?.split(" ")?.size != 2) {
                    bot.sendMessage(
                        chatId = chatId,
                        text = "Usage: /deep_reboot <hub_name>"
                    )
                    return@command
                }

                val hubName = message.text!!.split(" ")[1]
                val hubResult = deviceManager.findDevice(hubName, "deepReboot")

                when {
                    hubResult.isFailure -> {
                        bot.sendMessage(
                            chatId = chatId,
                            text = hubResult.exceptionOrNull()?.message ?: "Unknown error finding hub"
                        )
                        return@command
                    }
                    hubResult.getOrNull() !is Hub -> {
                        bot.sendMessage(
                            chatId = chatId,
                            text = "Device '${hubName}' is not a hub"
                        )
                        return@command
                    }
                }

                val hub = hubResult.getOrNull() as Hub
                if (hub.powerControl == null) {
                    bot.sendMessage(
                        chatId = chatId,
                        text = "Hub ${hub.label} does not support deep reboot (no power control configured)"
                    )
                    return@command
                }

                try {
                    bot.sendMessage(
                        chatId = chatId,
                        text = "Starting deep reboot sequence for ${hub.label}..."
                    )

                    runBlocking {
                        hub.deepReboot(client) { progressMessage ->
                            println(progressMessage)  // Console log
                            bot.sendMessage(         // Bot message
                                chatId = chatId,
                                text = progressMessage
                            )
                        }
                    }
                } catch (e: Exception) {
                    val errorMessage = when(e) {
                        is UnsupportedOperationException -> e.message ?: "Operation not supported"
                        else -> "Error during deep reboot: ${e.message}"
                    }
                    println("Deep reboot error for ${hub.label}: ${e.message}")
                    e.printStackTrace()
                    bot.sendMessage(
                        chatId = chatId,
                        text = errorMessage
                    )
                }
            }

            command("get_open_sensors") {
                val openSensors =
                    deviceManager.findDevicesByType(Device.ContactSensor::class.java).mapNotNull { sensor ->
                            val currentValue = getDeviceAttribute(sensor, "contact")
                            if (currentValue == "open") {
                                sensor.label
                            } else {
                                null
                            }
                        }.joinToString(separator = "\n")

                val response = if (openSensors.isNotEmpty()) {
                    "Open Sensors:\n$openSensors"
                } else {
                    "No open sensors found."
                }

                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = response)
            }
        }
    }

    println("Init successful, $deviceManager devices loaded, start polling")
    if (CHAT_ID != "") {
        bot.sendMessage(
            chatId = ChatId.fromId(CHAT_ID.toLong()),
            text = "Init successful, $deviceManager devices loaded, start polling"
        )
    }
    bot.startPolling()
}

fun String.snakeToCamelCase(): String {
    return split("_").mapIndexed { index, s ->
        if (index == 0) s else s.replaceFirstChar(Char::titlecase)
    }.joinToString("")
}

private suspend fun getDevicesJson(): String =
    client.get("http://${DEFAULT_HUB_IP}/apps/api/${MAKER_API_APP_ID}/devices") {
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

    val result = deviceManager.findDevice(deviceName, camelCaseCommand).fold(onSuccess = { device ->
        val argCount = device.supportedOps[camelCaseCommand]
        if (argCount == null) {
            "Command '/$snakeCaseCommand' is not supported by device '${device.label}'"
        } else if (args.size != argCount) {
            "Invalid number of arguments for /$snakeCaseCommand. Expected $argCount argument(s)."
        } else {
            runDeviceCommand(device, camelCaseCommand, args)
        }
    }, onFailure = {
        it.printStackTrace()
        it.message.toString()
    })

    bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = result)
}


suspend fun runDeviceQuery(device: Device, path: String, args: List<String> = emptyList()): HttpResponse {
    val fullPath = buildString {
        append("/apps/api/${MAKER_API_APP_ID}/devices/${device.id}/$path")
        if (args.isNotEmpty()) {
            append("/${args.joinToString("/")}")
        }
    }

    return client.get("http://${DEFAULT_HUB_IP}$fullPath") {
        parameter("access_token", MAKER_API_TOKEN)
    }
}

suspend fun runDeviceCommand(device: Device, command: String, args: List<String>): String {
    val response = runDeviceQuery(device, command, args)
    return response.status.description
}

suspend fun getDeviceAttribute(device: Device, attribute: String): String {
    val response = runDeviceQuery(device, "attribute/$attribute")
    val body = response.bodyAsText()
    val json = Json.parseToJsonElement(body).jsonObject
    return json["value"]?.jsonPrimitive?.content ?: "Unknown"
}

suspend fun runCommandOnHsm(command: String): String {
    return client.get("http://${DEFAULT_HUB_IP}/apps/api/${MAKER_API_APP_ID}/hsm/$command") {
        parameter("access_token", MAKER_API_TOKEN)
    }.status.description
}

suspend fun updateHubs(
    maxAttempts: Int = 20, 
    delayMillis: Long = 30000,
    sendMessage: suspend (String) -> Unit
): Result<String> {
    // First, get current and available versions for all hubs
    val initialStatus = StringBuilder("Current hub versions:\n")
    val hubsToUpdate = mutableListOf<Hub>()
    
    for (hub in hubs) {
        try {
            hub.currentVersion = getDeviceAttribute(hub, "firmwareVersionString")
            hub.updateVersion = getDeviceAttribute(hub, "hubUpdateVersion")
            initialStatus.append("${hub.label}: ${hub.currentVersion} (update available: ${hub.updateVersion})\n")
            
            if (hub.currentVersion == hub.updateVersion) {
                initialStatus.append("  - No update needed\n")
            } else {
                hubsToUpdate.add(hub)
            }
        } catch (e: Exception) {
            return Result.failure(Exception("Failed to get version info for hub ${hub.label}: ${e.message}"))
        }
    }

    sendMessage(initialStatus.toString())

    if (hubsToUpdate.isEmpty()) {
        return Result.success("No updates needed - all hubs are up to date")
    }

    sendMessage("Starting update process for ${hubsToUpdate.size} hub(s)...")

    // Initiate updates
    val statusMap = mutableMapOf<String, Pair<HttpStatusCode, String?>>()
    for (hub in hubsToUpdate) {
        try {
            val response = client.get("http://${hub.ip}/management/firmwareUpdate") {
                parameter("token", hub.managementToken)
            }
            statusMap[hub.label] = response.status to null
        } catch (e: Exception) {
            statusMap[hub.label] = HttpStatusCode.InternalServerError to e.message
        }
    }

    val failures = statusMap.filterValues { it.first != HttpStatusCode.OK }
    if (failures.isNotEmpty()) {
        val failureMessages = failures.entries.joinToString("\n") { (name, status) ->
            "Failed to update hub $name Status: ${status.first}${status.second?.let { " - $it" } ?: ""}"
        }
        val successMessages = statusMap.filterValues { it.first == HttpStatusCode.OK }.keys.joinToString("\n") { name ->
            "Successfully issued update request to hub $name"
        }
        return Result.failure(Exception("$failureMessages\n$successMessages"))
    }

    // Wait for each hub to complete update
    val updatedHubs = mutableSetOf<String>()
    var attempts = 0
    
    while (updatedHubs.size < hubsToUpdate.size && attempts < maxAttempts) {
        kotlinx.coroutines.delay(delayMillis)
        attempts++
        
        for (hub in hubsToUpdate) {
            if (hub.label !in updatedHubs) {
                try {
                    val newVersion = getDeviceAttribute(hub, "firmwareVersionString")
                    if (newVersion == hub.updateVersion) {
                        updatedHubs.add(hub.label)
                        sendMessage("Hub ${hub.label} updated from ${hub.currentVersion} to $newVersion")
                        hub.currentVersion = newVersion
                    }
                } catch (e: Exception) {
                    // Hub is probably rebooting, continue waiting
                    continue
                }
            }
        }
    }

    if (updatedHubs.size < hubsToUpdate.size) {
        val notUpdated = hubsToUpdate.filter { it.label !in updatedHubs }
            .joinToString("\n") { "${it.label} (still at version ${it.currentVersion})" }
        return Result.failure(Exception("Timeout waiting for hubs to complete update after ${maxAttempts * (delayMillis / 1000)} seconds\nNot updated:\n$notUpdated"))
    }

    return Result.success("All hubs updated successfully")
}

private suspend fun initHubs(): List<Hub> {
    val hubs = deviceManager.findDevicesByType(Hub::class.java)
    for (hub in hubs) {
        val json: Map<String, JsonElement> =
            Json.parseToJsonElement(client.get("http://${DEFAULT_HUB_IP}/apps/api/${MAKER_API_APP_ID}/devices/${hub.id}") {
                parameter("access_token", MAKER_API_TOKEN)
            }.body<String>()).jsonObject

        val ip = (json["attributes"] as JsonArray).find {
            it.jsonObject["name"]!!.jsonPrimitive.content == "localIP"
        }!!.jsonObject["currentValue"]!!.jsonPrimitive.content
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
