package jbaru.ch.telegram.hubitat

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Message
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import jbaru.ch.telegram.hubitat.model.Device
import org.mockito.kotlin.*

class CommandHandlersTest : FunSpec({
    
    lateinit var bot: Bot
    lateinit var deviceManager: DeviceManager
    lateinit var networkClient: NetworkClient
    val makerApiAppId = "test-app-id"
    val makerApiToken = "test-token"
    val defaultHubIp = "hubitat.local"
    
    beforeEach {
        bot = mock()
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
                bot, message, deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )
            
            result shouldBe "OK"
        }
        
        test("should return error when command has missing arguments") {
            val message = mock<Message> {
                on { text } doReturn "/on"
            }
            
            val result = CommandHandlers.handleDeviceCommand(
                bot, message, deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )
            
            result shouldContain "Please specify a device name"
        }
        
        test("should return error when command has wrong number of arguments") {
            val message = mock<Message> {
                on { text } doReturn "/push button 1 extra_arg"
            }
            val device = Device.VirtualButton(1, "Button")
            whenever(deviceManager.findDevice(eq("button"), eq("push")))
                .thenReturn(Result.success(device))
            
            val result = CommandHandlers.handleDeviceCommand(
                bot, message, deviceManager, networkClient,
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
                bot, message, deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )
            
            result shouldContain "No device found"
        }
        
        test("should return error when command is not supported by device") {
            val message = mock<Message> {
                on { text } doReturn "/invalid_command kitchen_light"
            }
            val device = Device.VirtualSwitch(1, "Kitchen Light")
            whenever(deviceManager.findDevice(eq("kitchen_light"), eq("invalidCommand")))
                .thenReturn(Result.success(device))
            
            val result = CommandHandlers.handleDeviceCommand(
                bot, message, deviceManager, networkClient,
                makerApiAppId, makerApiToken, defaultHubIp
            )
            
            result shouldContain "not supported by device"
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
            
            result shouldBe "OK"
        }
        
        test("should return error status when HSM command fails") {
            val mockResponse = mock<HttpResponse> {
                on { status } doReturn HttpStatusCode.InternalServerError
            }
            whenever(networkClient.get(any(), any())).thenReturn(mockResponse)
            
            val result = CommandHandlers.handleCancelAlertsCommand(
                networkClient, makerApiAppId, makerApiToken, defaultHubIp
            )
            
            result shouldBe "Internal Server Error"
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
    }
})
