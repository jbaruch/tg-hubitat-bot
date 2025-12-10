package jbaru.ch.telegram.hubitat

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import org.mockito.kotlin.*

class ModeOperationsPropertyTest : FunSpec({
    
    // **Feature: modes-control, Property 1: Mode list display indicates active mode**
    // **Validates: Requirements 2.2**
    test("mode list display should always indicate the active mode").config(invocations = 100) {
        checkAll(Arb.modeInfoList()) { modes ->
            val networkClient = mock<NetworkClient>()
            val modesJson = modes.toJson()
            
            whenever(networkClient.getBody(any(), any())).thenReturn(modesJson)
            
            val result = CommandHandlers.handleListModesCommand(
                networkClient, "test-app", "test-token", "test-hub"
            )
            
            // Find the active mode
            val activeMode = modes.find { it.active }
            if (activeMode != null) {
                // The result should contain the active mode name with "(active)" indicator
                result shouldContain activeMode.name
                result shouldContain "active"
            }
        }
    }
    
    // **Feature: modes-control, Property 2: Successful mode retrieval includes mode name**
    // **Validates: Requirements 1.3**
    test("successful getCurrentMode should include the mode name in response").config(invocations = 100) {
        checkAll(Arb.modeInfoList()) { modes ->
            val networkClient = mock<NetworkClient>()
            val modesJson = modes.toJson()
            
            whenever(networkClient.getBody(any(), any())).thenReturn(modesJson)
            
            val activeMode = modes.find { it.active }
            if (activeMode != null) {
                val result = CommandHandlers.handleGetModeCommand(
                    networkClient, "test-app", "test-token", "test-hub"
                )
                
                // The response should contain the active mode name
                result shouldContain activeMode.name
            }
        }
    }
    
    // **Feature: modes-control, Property 3: Set mode with valid name succeeds**
    // **Validates: Requirements 3.1**
    test("setMode with any valid mode name should succeed").config(invocations = 100) {
        checkAll(Arb.modeInfoList()) { modes ->
            val networkClient = mock<NetworkClient>()
            val modesJson = modes.toJson()
            
            whenever(networkClient.getBody(any(), any())).thenReturn(modesJson)
            
            val mockResponse = mock<io.ktor.client.statement.HttpResponse> {
                on { status } doReturn io.ktor.http.HttpStatusCode.OK
            }
            whenever(networkClient.get(any(), any())).thenReturn(mockResponse)
            
            // Pick a random mode from the list
            if (modes.isNotEmpty()) {
                val targetMode = modes.random()
                
                val result = ModeOperations.setMode(
                    networkClient, "test-app", "test-token", "test-hub", targetMode.name
                )
                
                // Should succeed for any valid mode name
                result.isSuccess shouldBe true
            }
        }
    }
    
    // **Feature: modes-control, Property 4: Invalid mode name rejection**
    // **Validates: Requirements 3.3**
    test("setMode with invalid mode name should fail").config(invocations = 100) {
        checkAll(Arb.modeInfoList(), Arb.string(1..20)) { modes, invalidName ->
            val networkClient = mock<NetworkClient>()
            val modesJson = modes.toJson()
            
            whenever(networkClient.getBody(any(), any())).thenReturn(modesJson)
            
            // Only test if the invalid name is not in the modes list
            val isActuallyInvalid = modes.none { it.name.equals(invalidName, ignoreCase = true) }
            
            if (isActuallyInvalid) {
                val result = ModeOperations.setMode(
                    networkClient, "test-app", "test-token", "test-hub", invalidName
                )
                
                // Should fail for invalid mode names
                result.isFailure shouldBe true
            }
        }
    }
    
    // **Feature: modes-control, Property 5: Successful mode change confirmation includes mode name**
    // **Validates: Requirements 3.4**
    test("successful setMode confirmation should include the new mode name").config(invocations = 100) {
        checkAll(Arb.modeInfoList()) { modes ->
            val networkClient = mock<NetworkClient>()
            val modesJson = modes.toJson()
            
            whenever(networkClient.getBody(any(), any())).thenReturn(modesJson)
            
            val mockResponse = mock<io.ktor.client.statement.HttpResponse> {
                on { status } doReturn io.ktor.http.HttpStatusCode.OK
            }
            whenever(networkClient.get(any(), any())).thenReturn(mockResponse)
            
            if (modes.isNotEmpty()) {
                val targetMode = modes.random()
                
                val result = ModeOperations.setMode(
                    networkClient, "test-app", "test-token", "test-hub", targetMode.name
                )
                
                // The success message should contain the mode name
                result.getOrNull() shouldContain targetMode.name
            }
        }
    }
})

// Helper function to generate arbitrary ModeInfo lists
private fun Arb.Companion.modeInfoList(): Arb<List<ModeInfo>> {
    return arbitrary { rs ->
        val size = Arb.int(1..5).bind()
        val modes = (1..size).map { id ->
            val name = Arb.stringPattern("[A-Z][a-z]+").bind()
            val active = id == 1 // Make the first one active
            ModeInfo(id, name, active)
        }
        modes
    }
}

// Helper function to convert ModeInfo list to JSON
private fun List<ModeInfo>.toJson(): String {
    return this.joinToString(
        prefix = "[",
        postfix = "]",
        separator = ","
    ) { mode ->
        """{"name":"${mode.name}","id":${mode.id},"active":${mode.active}}"""
    }
}
