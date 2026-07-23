package jbaru.ch.telegram.hubitat

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import io.ktor.http.isSuccess
import jbaru.ch.telegram.hubitat.model.Device
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

object CommandHandlers {

    private val logger = LoggerFactory.getLogger(CommandHandlers::class.java)
    private const val MAX_MESSAGE_LENGTH = 3900

    suspend fun handleDeviceCommand(
        bot: Bot,
        message: Message,
        deviceManager: DeviceManager,
        networkClient: NetworkClient,
        makerApiAppId: String,
        makerApiToken: String,
        defaultHubIp: String
    ): String {
        // Trim + split on runs of whitespace: "/on " or doubled spaces must not
        // produce empty tokens that the multi-token name search would probe.
        val parts = message.text?.trim()?.split(Regex("\\s+")) ?: emptyList()
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
        return if (response.status.isSuccess()) {
            "HSM alerts cancelled."
        } else {
            "Failed to cancel alerts: HTTP ${response.status}. " +
                "Check that Maker API control of HSM is allowed and the hub is reachable, then retry."
        }
    }
    
    suspend fun handleGetOpenSensorsCommand(
        deviceManager: DeviceManager,
        networkClient: NetworkClient,
        makerApiAppId: String,
        makerApiToken: String,
        defaultHubIp: String
    ): String = coroutineScope {
        // One HTTP call per sensor: run them concurrently (capped so a large
        // fleet doesn't stampede the hub), and never let one unreachable
        // sensor take down the whole report - it is listed as unreadable
        // instead.
        val semaphore = Semaphore(8)
        val states = deviceManager.findDevicesByType(Device.ContactSensor::class.java)
            .map { sensor ->
                async {
                    semaphore.withPermit {
                        try {
                            sensor to getDeviceAttribute(
                                sensor, "contact", networkClient, makerApiAppId, makerApiToken, defaultHubIp
                            )
                        } catch (e: CancellationException) {
                            // Never swallow structured-concurrency cancellation.
                            throw e
                        }
                        // The expected per-sensor failures - network (timeouts
                        // included: ktor's HttpRequestTimeoutException is an
                        // IOException), a non-2xx from getBody, and malformed
                        // JSON - mark the sensor "Could not read" instead of
                        // aborting the whole report.
                        catch (e: IOException) {
                            logUnreadableSensor(sensor.label, e); sensor to null
                        } catch (e: IllegalStateException) {
                            logUnreadableSensor(sensor.label, e); sensor to null
                        } catch (e: SerializationException) {
                            logUnreadableSensor(sensor.label, e); sensor to null
                        } catch (e: IllegalArgumentException) {
                            logUnreadableSensor(sensor.label, e); sensor to null
                        }
                    }
                }
            }.awaitAll()

        val openSensors = states.filter { it.second == "open" }.joinToString("\n") { it.first.label }
        val unreadable = states.filter { it.second == null }.joinToString(", ") { it.first.label }

        val reply = buildString {
            append(
                if (openSensors.isNotEmpty()) "Open Sensors:\n$openSensors" else "No open sensors found."
            )
            if (unreadable.isNotEmpty()) {
                append("\nCould not read: $unreadable")
            }
        }
        // Telegram caps messages at 4096 chars; a large install can exceed it.
        if (reply.length <= MAX_MESSAGE_LENGTH) reply
        else reply.take(MAX_MESSAGE_LENGTH) + "\n… (truncated)"
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
    
    private fun logUnreadableSensor(label: String, e: Exception) {
        logger.warn("Could not read contact state of '{}': {}", label, e.message)
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
        // A raw HTTP reason phrase ("Not Found") reads like noise, and a non-2xx
        // used to be reported in the same voice as success. Say what happened,
        // in the snake_case form the user actually typed.
        val displayCommand = command.camelToSnakeCase()
        val argSuffix = if (args.isEmpty()) "" else " ${args.joinToString(" ")}"
        return if (response.status.isSuccess()) {
            "Done: ${device.label} → $displayCommand$argSuffix"
        } else {
            "Failed: ${device.label} → $displayCommand$argSuffix returned HTTP ${response.status}. " +
                "Check that the device is reachable and exposed in Maker API, then retry."
        }
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
