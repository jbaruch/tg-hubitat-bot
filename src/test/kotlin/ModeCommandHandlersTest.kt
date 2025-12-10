package jbaru.ch.telegram.hubitat

import com.github.kotlintelegrambot.entities.Message
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.mockito.kotlin.*

class ModeCommandHandlersTest : FunSpec({
    
    lateinit var networkClient: NetworkClient
    val makerApiAppId = "test-app-id"
    val makerApiToken = "test-token"
    val hubIp = "hubitat.local"
    
    beforeEach {
        networkClient = mock()
    }
    
    context("handleGetModeCommand") {
        test("should return current mode successfully") {
            val modesJson = """
                [
                    {"name": "Home", "id": 1, "active": true},
                    {"name": "Away", "id": 2, "active": false}
                ]
            """.trimIndent()
            
            whenever(networkClient.getBody(any(), any())).thenReturn(modesJson)
            
            val result = CommandHandlers.handleGetModeCommand(
                networkClient, makerApiAppId, makerApiToken, hubIp
            )
            
            result shouldContain "Home"
            result shouldContain "Current mode"
        }
        
        test("should return error message on failure") {
            whenever(networkClient.getBody(any(), any()))
                .thenThrow(RuntimeException("Connection failed"))
            
            val result = CommandHandlers.handleGetModeCommand(
                networkClient, makerApiAppId, makerApiToken, hubIp
            )
            
            result shouldContain "Error"
            result shouldContain "Connection failed"
        }
    }
    
    context("handleListModesCommand") {
        test("should list all modes with active mode indicated") {
            val modesJson = """
                [
                    {"name": "Home", "id": 1, "active": true},
                    {"name": "Away", "id": 2, "active": false},
                    {"name": "Night", "id": 3, "active": false}
                ]
            """.trimIndent()
            
            whenever(networkClient.getBody(any(), any())).thenReturn(modesJson)
            
            val result = CommandHandlers.handleListModesCommand(
                networkClient, makerApiAppId, makerApiToken, hubIp
            )
            
            result shouldContain "Home"
            result shouldContain "Away"
            result shouldContain "Night"
            result shouldContain "active"
        }
        
        test("should return error message on failure") {
            whenever(networkClient.getBody(any(), any()))
                .thenThrow(RuntimeException("Network error"))
            
            val result = CommandHandlers.handleListModesCommand(
                networkClient, makerApiAppId, makerApiToken, hubIp
            )
            
            result shouldContain "Error"
            result shouldContain "Network error"
        }
    }
    
    context("handleSetModeCommand") {
        test("should successfully change mode") {
            val message = mock<Message> {
                on { text } doReturn "/set_mode Away"
            }
            
            val modesJson = """
                [
                    {"name": "Home", "id": 1, "active": true},
                    {"name": "Away", "id": 2, "active": false}
                ]
            """.trimIndent()
            
            whenever(networkClient.getBody(any(), any())).thenReturn(modesJson)
            
            val mockResponse = mock<io.ktor.client.statement.HttpResponse> {
                on { status } doReturn io.ktor.http.HttpStatusCode.OK
            }
            whenever(networkClient.get(any(), any())).thenReturn(mockResponse)
            
            val result = CommandHandlers.handleSetModeCommand(
                message, networkClient, makerApiAppId, makerApiToken, hubIp
            )
            
            result shouldContain "Away"
            result shouldContain "changed"
        }
        
        test("should return error when mode name is missing") {
            val message = mock<Message> {
                on { text } doReturn "/set_mode"
            }
            
            val result = CommandHandlers.handleSetModeCommand(
                message, networkClient, makerApiAppId, makerApiToken, hubIp
            )
            
            result shouldContain "Please specify a mode name"
        }
        
        test("should return error with suggestions when mode is invalid") {
            val message = mock<Message> {
                on { text } doReturn "/set_mode InvalidMode"
            }
            
            val modesJson = """
                [
                    {"name": "Home", "id": 1, "active": true},
                    {"name": "Away", "id": 2, "active": false}
                ]
            """.trimIndent()
            
            whenever(networkClient.getBody(any(), any())).thenReturn(modesJson)
            
            val result = CommandHandlers.handleSetModeCommand(
                message, networkClient, makerApiAppId, makerApiToken, hubIp
            )
            
            result shouldContain "Mode not found"
            result shouldContain "Home"
            result shouldContain "Away"
        }
        
        test("should format confirmation message correctly") {
            val message = mock<Message> {
                on { text } doReturn "/set_mode Night"
            }
            
            val modesJson = """
                [
                    {"name": "Home", "id": 1, "active": true},
                    {"name": "Night", "id": 3, "active": false}
                ]
            """.trimIndent()
            
            whenever(networkClient.getBody(any(), any())).thenReturn(modesJson)
            
            val mockResponse = mock<io.ktor.client.statement.HttpResponse> {
                on { status } doReturn io.ktor.http.HttpStatusCode.OK
            }
            whenever(networkClient.get(any(), any())).thenReturn(mockResponse)
            
            val result = CommandHandlers.handleSetModeCommand(
                message, networkClient, makerApiAppId, makerApiToken, hubIp
            )
            
            result shouldContain "Night"
        }
    }
})
