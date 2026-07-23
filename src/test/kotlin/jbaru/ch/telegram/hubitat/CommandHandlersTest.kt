package jbaru.ch.telegram.hubitat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

import com.github.kotlintelegrambot.entities.Message
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import jbaru.ch.telegram.hubitat.model.Device

class CommandHandlersTest : FunSpec({

    lateinit var deviceManager: DeviceManager
    lateinit var networkClient: NetworkClient
    val makerApiAppId = "test-app-id"
    val makerApiToken = "test-token"
    val defaultHubIp = "hubitat.local"

    beforeEach {
        deviceManager = mock()
        networkClient = mock()
    }

    context("handleDeviceCommand") {
        test("should execute command successfully") {
            val message = mock<Message> {
                on { text } doReturn "/on kitchen_light"
            }
            val device = Device.VirtualSwitch(1, "Kitchen Light")
            whenever(deviceManager.findDevice(eq("kitchen_light"), eq("on")))
                .thenReturn(Result.success(device))

            val mockResponse = mock<HttpResponse> {
                on { status } doReturn HttpStatusCode.OK
            }
            whenever(networkClient.get(any(), any())).thenReturn(mockResponse)

            val result = CommandHandlers.handleDeviceCommand(
                message, deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            result shouldBe "Done: Kitchen Light → on"
        }

        test("should return error when command has missing arguments") {
            val message = mock<Message> {
                on { text } doReturn "/on"
            }

            val result = CommandHandlers.handleDeviceCommand(
                message, deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            result shouldContain "Please specify a device name"
        }

        test("should return error when command has wrong number of arguments") {
            val message = mock<Message> {
                on { text } doReturn "/push button 1 extra_arg"
            }
            val device = Device.VirtualButton(1, "Button")
            whenever(deviceManager.findDevice(any(), any()))
                .thenReturn(Result.failure(Exception("No device found for query: probe")))
            whenever(deviceManager.findDevice(eq("button"), eq("push")))
                .thenReturn(Result.success(device))

            val result = CommandHandlers.handleDeviceCommand(
                message, deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            result shouldContain "Invalid number of arguments"
        }

        test("should return error when device not found") {
            val message = mock<Message> {
                on { text } doReturn "/on unknown_device"
            }
            whenever(deviceManager.findDevice(eq("unknown_device"), eq("on")))
                .thenReturn(Result.failure(Exception("No device found for query: unknown_device")))

            val result = CommandHandlers.handleDeviceCommand(
                message, deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            result shouldContain "No device found"
        }

        test("should return error when command is not supported by device") {
            val message = mock<Message> {
                on { text } doReturn "/invalid_command kitchen_light"
            }
            // findDevice signals an unsupported command with
            // IllegalArgumentException, matching the real DeviceManager.
            whenever(deviceManager.findDevice(eq("kitchen_light"), eq("invalidCommand")))
                .thenReturn(
                    Result.failure(
                        IllegalArgumentException("Command 'invalidCommand' is not supported by device 'Kitchen Light'")
                    )
                )

            val result = CommandHandlers.handleDeviceCommand(
                message, deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            result shouldContain "not supported by device"
        }

        test("should return error when message text is null") {
            val message = mock<Message> {
                on { text } doReturn null
            }

            val result = CommandHandlers.handleDeviceCommand(
                message, deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            result shouldContain "Please specify a device name"
        }

        test("should execute command that takes arguments") {
            // Covers the args-present path in runDeviceCommand.
            val message = mock<Message> {
                on { text } doReturn "/push button 1"
            }
            val device = Device.VirtualButton(1, "Button")
            whenever(deviceManager.findDevice(any(), any()))
                .thenReturn(Result.failure(Exception("No device found for query: probe")))
            whenever(deviceManager.findDevice(eq("button"), eq("push")))
                .thenReturn(Result.success(device))

            val mockResponse = mock<HttpResponse> {
                on { status } doReturn HttpStatusCode.OK
            }
            whenever(networkClient.get(argThat { contains("/devices/1/push/1") }, any()))
                .thenReturn(mockResponse)

            val result = CommandHandlers.handleDeviceCommand(
                message, deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            result shouldBe "Done: Button → push 1"
        }

        test("should execute set_level on a dimmer") {
            val message = mock<Message> {
                on { text } doReturn "/set_level kitchen 50"
            }
            val device = Device.RoomLightsActivatorDimmer(5, "Kitchen Lights")
            whenever(deviceManager.findDevice(any(), any()))
                .thenReturn(Result.failure(Exception("No device found for query: probe")))
            whenever(deviceManager.findDevice(eq("kitchen"), eq("setLevel")))
                .thenReturn(Result.success(device))

            val mockResponse = mock<HttpResponse> {
                on { status } doReturn HttpStatusCode.OK
            }
            whenever(networkClient.get(argThat { contains("/devices/5/setLevel/50") }, any()))
                .thenReturn(mockResponse)

            val result = CommandHandlers.handleDeviceCommand(
                message, deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            result shouldBe "Done: Kitchen Lights → set_level 50"
        }

        test("should report failure when the hub returns a non-2xx status") {
            val message = mock<Message> {
                on { text } doReturn "/on kitchen_light"
            }
            val device = Device.VirtualSwitch(1, "Kitchen Light")
            whenever(deviceManager.findDevice(eq("kitchen_light"), eq("on")))
                .thenReturn(Result.success(device))

            val mockResponse = mock<HttpResponse> {
                on { status } doReturn HttpStatusCode.NotFound
            }
            whenever(networkClient.get(any(), any())).thenReturn(mockResponse)

            val result = CommandHandlers.handleDeviceCommand(
                message, deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            result shouldContain "Failed: Kitchen Light"
            result shouldContain "404"
        }
    }

    context("handleDeviceCommand with multi-word device names") {
        test("resolves a full multi-word name with no args") {
            val message = mock<Message> {
                on { text } doReturn "/on kitchen lights"
            }
            val device = Device.VirtualSwitch(1, "Kitchen Lights")
            whenever(deviceManager.findDevice(any(), any()))
                .thenReturn(Result.failure(Exception("No device found for query: probe")))
            whenever(deviceManager.findDevice(eq("kitchen lights"), eq("on")))
                .thenReturn(Result.success(device))

            val mockResponse = mock<HttpResponse> {
                on { status } doReturn HttpStatusCode.OK
            }
            whenever(networkClient.get(any(), any())).thenReturn(mockResponse)

            val result = CommandHandlers.handleDeviceCommand(
                message, deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            result shouldBe "Done: Kitchen Lights → on"
        }

        test("resolves a multi-word name followed by trailing args") {
            val message = mock<Message> {
                on { text } doReturn "/push front door button 1"
            }
            val device = Device.VirtualButton(7, "Front Door Button")
            whenever(deviceManager.findDevice(any(), any()))
                .thenReturn(Result.failure(Exception("No device found for query: probe")))
            whenever(deviceManager.findDevice(eq("front door button"), eq("push")))
                .thenReturn(Result.success(device))

            val mockResponse = mock<HttpResponse> {
                on { status } doReturn HttpStatusCode.OK
            }
            whenever(networkClient.get(argThat { contains("/devices/7/push/1") }, any()))
                .thenReturn(mockResponse)

            val result = CommandHandlers.handleDeviceCommand(
                message, deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            result shouldBe "Done: Front Door Button → push 1"
        }

        test("prefers the longest matching name over a shorter prefix") {
            val message = mock<Message> {
                on { text } doReturn "/push master bedroom button 2"
            }
            val longer = Device.VirtualButton(2, "Master Bedroom Button")
            val shorter = Device.VirtualButton(3, "Master")
            whenever(deviceManager.findDevice(any(), any()))
                .thenReturn(Result.failure(Exception("No device found for query: probe")))
            whenever(deviceManager.findDevice(eq("master"), eq("push")))
                .thenReturn(Result.success(shorter))
            whenever(deviceManager.findDevice(eq("master bedroom button"), eq("push")))
                .thenReturn(Result.success(longer))

            val mockResponse = mock<HttpResponse> {
                on { status } doReturn HttpStatusCode.OK
            }
            whenever(networkClient.get(argThat { contains("/devices/2/push/2") }, any()))
                .thenReturn(mockResponse)

            val result = CommandHandlers.handleDeviceCommand(
                message, deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            result shouldBe "Done: Master Bedroom Button → push 2"
        }

        test("trailing and doubled whitespace does not produce phantom device names") {
            val message = mock<Message> {
                on { text } doReturn "/on  kitchen  lights "
            }
            val device = Device.VirtualSwitch(1, "Kitchen Lights")
            whenever(deviceManager.findDevice(any(), any()))
                .thenReturn(Result.failure(Exception("No device found for query: probe")))
            whenever(deviceManager.findDevice(eq("kitchen lights"), eq("on")))
                .thenReturn(Result.success(device))

            val mockResponse = mock<HttpResponse> {
                on { status } doReturn HttpStatusCode.OK
            }
            whenever(networkClient.get(any(), any())).thenReturn(mockResponse)

            val result = CommandHandlers.handleDeviceCommand(
                message, deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            result shouldBe "Done: Kitchen Lights → on"
        }

        test("a command with only trailing whitespace asks for a device name") {
            val message = mock<Message> {
                on { text } doReturn "/on "
            }

            val result = CommandHandlers.handleDeviceCommand(
                message, deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            result shouldContain "Please specify a device name"
        }

        test("reports the full query when no split matches a device") {
            val message = mock<Message> {
                on { text } doReturn "/on unknown thing"
            }
            whenever(deviceManager.findDevice(any(), any()))
                .thenReturn(Result.failure(Exception("No device found for query: probe")))

            val result = CommandHandlers.handleDeviceCommand(
                message, deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            result shouldBe "No device found for query: unknown thing"
        }
    }

    context("handleListCommand") {
        test("should return device list grouped by type") {
            val expectedMap = mapOf(
                "Actuators" to "device list",
                "Sensors" to "sensor list"
            )
            whenever(deviceManager.listByType()).thenReturn(expectedMap)

            val result = CommandHandlers.handleListCommand(deviceManager)

            result shouldBe expectedMap
        }

        test("should handle empty device list") {
            whenever(deviceManager.listByType()).thenReturn(emptyMap())

            val result = CommandHandlers.handleListCommand(deviceManager)

            result shouldBe emptyMap()
        }
    }

    context("handleRefreshCommand") {
        test("should refresh devices successfully") {
            val devicesJson = """[{"id": 1, "label": "Test Device"}]"""
            whenever(networkClient.getBody(any(), any())).thenReturn(devicesJson)
            whenever(deviceManager.refreshDevices(devicesJson))
                .thenReturn(Pair(1, emptyList()))

            val result = CommandHandlers.handleRefreshCommand(
                deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            result.first shouldBe 1
            result.second shouldBe emptyList()
        }

        test("should return warnings when refresh has issues") {
            val devicesJson = """[{"id": 1, "label": "Test Device"}]"""
            val warnings = listOf("WARNING: Duplicate key")
            whenever(networkClient.getBody(any(), any())).thenReturn(devicesJson)
            whenever(deviceManager.refreshDevices(devicesJson))
                .thenReturn(Pair(1, warnings))

            val result = CommandHandlers.handleRefreshCommand(
                deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            result.first shouldBe 1
            result.second shouldBe warnings
        }
    }

    context("handleCancelAlertsCommand") {
        test("should cancel alerts successfully") {
            val mockResponse = mock<HttpResponse> {
                on { status } doReturn HttpStatusCode.OK
            }
            whenever(networkClient.get(any(), any())).thenReturn(mockResponse)

            val result = CommandHandlers.handleCancelAlertsCommand(
                networkClient, makerApiAppId, makerApiToken, defaultHubIp
            )

            result shouldBe "HSM alerts cancelled."
        }

        test("should return error status when HSM command fails") {
            val mockResponse = mock<HttpResponse> {
                on { status } doReturn HttpStatusCode.InternalServerError
            }
            whenever(networkClient.get(any(), any())).thenReturn(mockResponse)

            val result = CommandHandlers.handleCancelAlertsCommand(
                networkClient, makerApiAppId, makerApiToken, defaultHubIp
            )

            result shouldContain "Failed to cancel alerts"
            result shouldContain "500"
        }
    }

    context("handleGetOpenSensorsCommand") {
        test("should return list of open sensors") {
            val sensor1 = Device.GenericZigbeeContactSensor(1, "Front Door")
            val sensor2 = Device.GenericZigbeeContactSensor(2, "Back Door")
            whenever(deviceManager.findDevicesByType(Device.ContactSensor::class.java))
                .thenReturn(listOf(sensor1, sensor2))

            whenever(networkClient.getBody(argThat { contains("/devices/1/attribute/contact") }, any()))
                .thenReturn("""{"value": "open"}""")
            whenever(networkClient.getBody(argThat { contains("/devices/2/attribute/contact") }, any()))
                .thenReturn("""{"value": "closed"}""")

            val result = CommandHandlers.handleGetOpenSensorsCommand(
                deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            result shouldContain "Front Door"
            result shouldContain "Open Sensors"
        }

        test("should return message when no open sensors found") {
            val sensor = Device.GenericZigbeeContactSensor(1, "Front Door")
            whenever(deviceManager.findDevicesByType(Device.ContactSensor::class.java))
                .thenReturn(listOf(sensor))

            whenever(networkClient.getBody(any(), any()))
                .thenReturn("""{"value": "closed"}""")

            val result = CommandHandlers.handleGetOpenSensorsCommand(
                deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            result shouldBe "No open sensors found."
        }

        test("should keep reporting when one sensor read fails") {
            val sensor1 = Device.GenericZigbeeContactSensor(1, "Front Door")
            val sensor2 = Device.GenericZigbeeContactSensor(2, "Back Door")
            whenever(deviceManager.findDevicesByType(Device.ContactSensor::class.java))
                .thenReturn(listOf(sensor1, sensor2))

            whenever(networkClient.getBody(argThat { contains("/devices/1/attribute/contact") }, any()))
                .thenReturn("""{"value": "open"}""")
            whenever(networkClient.getBody(argThat { contains("/devices/2/attribute/contact") }, any()))
                .thenThrow(IllegalStateException("Hub request failed: HTTP 503"))

            val result = CommandHandlers.handleGetOpenSensorsCommand(
                deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            result shouldContain "Front Door"
            result shouldContain "Could not read: Back Door"
        }

        test("should truncate an oversized reply under the Telegram limit") {
            val sensors = (1..200).map {
                Device.GenericZigbeeContactSensor(it, "Sensor With A Fairly Long Name Number $it")
            }
            whenever(deviceManager.findDevicesByType(Device.ContactSensor::class.java))
                .thenReturn(sensors)
            whenever(networkClient.getBody(any(), any()))
                .thenReturn("""{"value": "open"}""")

            val result = CommandHandlers.handleGetOpenSensorsCommand(
                deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            (result.length <= 4000) shouldBe true
            result shouldContain "truncated"
        }

        test("should treat a sensor with no value attribute as not open") {
            // Covers the '?: \"Unknown\"' fallback in getDeviceAttribute.
            val sensor = Device.GenericZigbeeContactSensor(1, "Front Door")
            whenever(deviceManager.findDevicesByType(Device.ContactSensor::class.java))
                .thenReturn(listOf(sensor))

            whenever(networkClient.getBody(any(), any()))
                .thenReturn("""{"notValue": "whatever"}""")

            val result = CommandHandlers.handleGetOpenSensorsCommand(
                deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )

            result shouldBe "No open sensors found."
        }
    }
})
