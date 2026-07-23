package jbaru.ch.telegram.hubitat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import io.kotest.property.arbitrary.string

import com.github.kotlintelegrambot.entities.Message
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import jbaru.ch.telegram.hubitat.model.Device

// Property tests run with a FIXED seed: the exploration stays property-based
// but the generated cases are deterministic and reproducible - no
// self-generated randomness in CI, no jacoco coverage-gate flakiness.
private const val FIXED_SEED = 20260722L

// Bounded iterations: the default 1000 made mockito's per-mock invocation
// recording balloon to hundreds of MB across a checkAll run.
private const val PROP_ITERATIONS = 100

class CommandHandlersPropertyTest : FunSpec({
    
    // **Feature: test-coverage-improvement, Property 1: Command parsing correctness**
    test("command parsing should correctly extract command, device name, and arguments").config(invocations = 100) {
        val deviceManager = mock<DeviceManager>()
        val networkClient = mock<NetworkClient>()
        
        checkAll(PropTestConfig(seed = FIXED_SEED, iterations = PROP_ITERATIONS), Arb.string(1..50)) { input ->
            val parts = input.split(" ").filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                val command = parts[0].filter { it.isLetterOrDigit() || it == '_' }
                val deviceName = parts[1].filter { it.isLetterOrDigit() || it == '_' }
                
                if (command.isNotEmpty() && deviceName.isNotEmpty()) {
                    val camelCommand = command.snakeToCamelCase()

                    // findDevice's contract is success-implies-supported, so an
                    // arbitrary generated command must stub the failure path;
                    // the property under test is the parsing, verified below.
                    whenever(deviceManager.findDevice(any(), any()))
                        .thenReturn(Result.failure(Exception("No device found for query: $deviceName")))

                    val messageText = "/$command $deviceName"
                    val message = mock<Message> {
                        on { text } doReturn messageText
                    }
                    
                    val result = CommandHandlers.handleDeviceCommand(
                        message, deviceManager, networkClient,
                        "test-app", "test-token", "test-hub"
                    )
                    
                    // Verify the command was parsed correctly
                    verify(deviceManager).findDevice(eq(deviceName), eq(camelCommand))
                }
            }
        }
    }
})
