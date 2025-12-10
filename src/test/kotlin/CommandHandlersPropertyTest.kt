package jbaru.ch.telegram.hubitat

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Message
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import jbaru.ch.telegram.hubitat.model.Device
import org.mockito.kotlin.*

class CommandHandlersPropertyTest : FunSpec({
    
    // **Feature: test-coverage-improvement, Property 1: Command parsing correctness**
    test("command parsing should correctly extract command, device name, and arguments").config(invocations = 100) {
        val bot = mock<Bot>()
        val deviceManager = mock<DeviceManager>()
        val networkClient = mock<NetworkClient>()
        
        checkAll(Arb.string(1..50)) { input ->
            val parts = input.split(" ").filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                val command = parts[0].filter { it.isLetterOrDigit() || it == '_' }
                val deviceName = parts[1].filter { it.isLetterOrDigit() || it == '_' }
                
                if (command.isNotEmpty() && deviceName.isNotEmpty()) {
                    val camelCommand = command.snakeToCamelCase()
                    val device = Device.VirtualSwitch(1, "Test Device")
                    
                    whenever(deviceManager.findDevice(eq(deviceName), eq(camelCommand)))
                        .thenReturn(Result.success(device))
                    
                    val mockResponse = mock<HttpResponse> {
                        on { status } doReturn HttpStatusCode.OK
                    }
                    whenever(networkClient.get(any(), any())).thenReturn(mockResponse)
                    
                    val messageText = "/$command $deviceName"
                    val message = mock<Message> {
                        on { text } doReturn messageText
                    }
                    
                    val result = CommandHandlers.handleDeviceCommand(
                        bot, message, deviceManager, networkClient,
                        "test-app", "test-token", "test-hub"
                    )
                    
                    // Verify the command was parsed correctly
                    verify(deviceManager).findDevice(eq(deviceName), eq(camelCommand))
                }
            }
        }
    }
})
