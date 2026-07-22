package jbaru.ch.telegram.hubitat

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import jbaru.ch.telegram.hubitat.model.Device
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

object CommandHandlers {

    private val logger = LoggerFactory.getLogger(CommandHandlers::class.java)

    suspend fun handleDeviceCommand(
        bot: Bot,
        message: Message,
        deviceManager: DeviceManager,
        networkClient: NetworkClient,
        makerApiAppId: String,
        makerApiToken: String,
        defaultHubIp: String
    ): String {
        val parts = message.text?.split(" ") ?: return "Please specify a device name for the command."
        if (parts.size < 2) {
            return "Please specify a device name for the command."
        }

        // Strip the group-chat mention form (/on@BotName) to the bare command.
        val snakeCaseCommand = parts[0].removePrefix("/").substringBefore("@")
        val camelCaseCommand = snakeCaseCommand.snakeToCamelCase()
        val tokens = parts.drop(1)
        val fullQuery = tokens.joinToString(" ")

        // Device labels are multi-word ("Kitchen Lights", "Front Door Button"),
        // so the device/args boundary is not fixed. Try the longest possible
        // name first and shrink until a known device with the right trailing
        // argument count matches; the longest match wins so a device whose name
        // prefixes another's never steals the query.
        var mismatchError: String? = null
        var unsupportedError: String? = null

        for (i in tokens.size downTo 1) {
            val name = tokens.take(i).joinToString(" ")
            val args = tokens.drop(i)
            deviceManager.findDevice(name, camelCaseCommand).fold(
                onSuccess = { device ->
                    // findDevice only succeeds when the device supports the
                    // command, so the op is guaranteed present here.
                    val argCount = device.supportedOps.getValue(camelCaseCommand)
                    if (args.size == argCount) {
                        return runDeviceCommand(
                            device, camelCaseCommand, args,
                            networkClient, makerApiAppId, makerApiToken, defaultHubIp
                        )
                    } else if (mismatchError == null) {
                        mismatchError =
                            "Invalid number of arguments for /$snakeCaseCommand. Expected $argCount argument(s)."
                    }
                },
                onFailure = {
                    // findDevice signals "device exists but can't do this" with
                    // IllegalArgumentException; anything else is "not found".
                    if (it is IllegalArgumentException && unsupportedError == null) {
                        unsupportedError = it.message
                    }
                }
            )
        }

        val error = mismatchError ?: unsupportedError ?: "No device found for query: $fullQuery"
        logger.warn("Device command '{}' failed: {}", snakeCaseCommand, error)
        return error
    }
    
    suspend fun handleListCommand(deviceManager: DeviceManager): Map<String, String> {
        return deviceManager.listByType()
    }
    
    suspend fun handleRefreshCommand(
        deviceManager: DeviceManager,
        networkClient: NetworkClient,
        makerApiAppId: String,
        makerApiToken: String,
        defaultHubIp: String
    ): Pair<Int, List<String>> {
        val devicesJson = networkClient.getBody(
            "http://${defaultHubIp}/apps/api/${makerApiAppId}/devices",
            mapOf("access_token" to makerApiToken)
        )
        return deviceManager.refreshDevices(devicesJson)
    }
    
    suspend fun handleCancelAlertsCommand(
        networkClient: NetworkClient,
        makerApiAppId: String,
        makerApiToken: String,
        defaultHubIp: String
    ): String {
        val response = networkClient.get(
            "http://${defaultHubIp}/apps/api/${makerApiAppId}/hsm/cancelAlerts",
            mapOf("access_token" to makerApiToken)
        )
        return response.status.description
    }
    
    suspend fun handleGetOpenSensorsCommand(
        deviceManager: DeviceManager,
        networkClient: NetworkClient,
        makerApiAppId: String,
        makerApiToken: String,
        defaultHubIp: String
    ): String {
        val openSensors = deviceManager.findDevicesByType(Device.ContactSensor::class.java)
            .mapNotNull { sensor ->
                val currentValue = getDeviceAttribute(sensor, "contact", networkClient, makerApiAppId, makerApiToken, defaultHubIp)
                if (currentValue == "open") {
                    sensor.label
                } else {
                    null
                }
            }.joinToString(separator = "\n")

        return if (openSensors.isNotEmpty()) {
            "Open Sensors:\n$openSensors"
        } else {
            "No open sensors found."
        }
    }
    
    suspend fun handleGetModeCommand(
        networkClient: NetworkClient,
        makerApiAppId: String,
        makerApiToken: String,
        hubIp: String
    ): String {
        return ModeOperations.getCurrentMode(
            networkClient, makerApiAppId, makerApiToken, hubIp
        ).fold(
            onSuccess = { mode -> "Current mode: ${mode.name}" },
            onFailure = { e -> "Error getting current mode: ${e.message}" }
        )
    }
    
    suspend fun handleListModesCommand(
        networkClient: NetworkClient,
        makerApiAppId: String,
        makerApiToken: String,
        hubIp: String
    ): String {
        return ModeOperations.getAllModes(
            networkClient, makerApiAppId, makerApiToken, hubIp
        ).fold(
            onSuccess = { modes ->
                val modeList = modes.joinToString("\n") { mode ->
                    if (mode.active) {
                        "• ${mode.name} (active)"
                    } else {
                        "• ${mode.name}"
                    }
                }
                "Available modes:\n$modeList"
            },
            onFailure = { e -> "Error listing modes: ${e.message}" }
        )
    }
    
    suspend fun handleSetModeCommand(
        message: Message,
        networkClient: NetworkClient,
        makerApiAppId: String,
        makerApiToken: String,
        hubIp: String
    ): String {
        val parts = message.text?.split(" ") ?: return "Please specify a mode name."
        if (parts.size < 2) {
            return "Please specify a mode name. Usage: /set_mode <mode_name>"
        }
        
        val modeName = parts.drop(1).joinToString(" ")
        
        return ModeOperations.setMode(
            networkClient, makerApiAppId, makerApiToken, hubIp, modeName
        ).fold(
            onSuccess = { successMessage -> "Mode changed to $modeName successfully." },
            onFailure = { e ->
                if (e.message?.contains("Mode not found") == true) {
                    // Get available modes to suggest
                    val modesResult = ModeOperations.getAllModes(
                        networkClient, makerApiAppId, makerApiToken, hubIp
                    )
                    val availableModes = modesResult.getOrNull()?.joinToString(", ") { it.name } ?: ""
                    "Mode not found: $modeName. Available modes: $availableModes"
                } else {
                    "Error setting mode: ${e.message}"
                }
            }
        )
    }
    
    private suspend fun runDeviceCommand(
        device: Device,
        command: String,
        args: List<String>,
        networkClient: NetworkClient,
        makerApiAppId: String,
        makerApiToken: String,
        defaultHubIp: String
    ): String {
        val fullPath = buildString {
            append("/apps/api/${makerApiAppId}/devices/${device.id}/$command")
            if (args.isNotEmpty()) {
                append("/${args.joinToString("/")}")
            }
        }

        val response = networkClient.get(
            "http://${defaultHubIp}$fullPath",
            mapOf("access_token" to makerApiToken)
        )
        return response.status.description
    }
    
    private suspend fun getDeviceAttribute(
        device: Device,
        attribute: String,
        networkClient: NetworkClient,
        makerApiAppId: String,
        makerApiToken: String,
        defaultHubIp: String
    ): String {
        val fullPath = "/apps/api/${makerApiAppId}/devices/${device.id}/attribute/$attribute"
        val body = networkClient.getBody(
            "http://${defaultHubIp}$fullPath",
            mapOf("access_token" to makerApiToken)
        )
        val json = Json.parseToJsonElement(body).jsonObject
        return json["value"]?.jsonPrimitive?.content ?: "Unknown"
    }
}
