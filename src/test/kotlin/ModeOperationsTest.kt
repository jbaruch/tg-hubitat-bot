package jbaru.ch.telegram.hubitat

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.mockito.kotlin.*

class ModeOperationsTest : FunSpec({
    
    lateinit var networkClient: NetworkClient
    val makerApiAppId = "test-app-id"
    val makerApiToken = "test-token"
    val hubIp = "hubitat.local"
    
    beforeEach {
        networkClient = mock()
    }
    
    context("getAllModes") {
        test("should successfully retrieve and parse modes") {
            val modesJson = """
                [
                    {"name": "Home", "id": 1, "active": true},
                    {"name": "Away", "id": 2, "active": false},
                    {"name": "Night", "id": 3, "active": false}
                ]
            """.trimIndent()
            
            whenever(networkClient.getBody(
                eq("http://hubitat.local/apps/api/test-app-id/modes"),
                eq(mapOf("access_token" to "test-token"))
            )).thenReturn(modesJson)
            
            val result = ModeOperations.getAllModes(
                networkClient, makerApiAppId, makerApiToken, hubIp
            )
            
            result.isSuccess shouldBe true
            val modes = result.getOrNull()!!
            modes.size shouldBe 3
            modes[0].name shouldBe "Home"
            modes[0].id shouldBe 1
            modes[0].active shouldBe true
            modes[1].name shouldBe "Away"
            modes[1].active shouldBe false
        }
        
        test("should handle network errors") {
            whenever(networkClient.getBody(any(), any()))
                .thenThrow(RuntimeException("Connection failed"))
            
            val result = ModeOperations.getAllModes(
                networkClient, makerApiAppId, makerApiToken, hubIp
            )
            
            result.isFailure shouldBe true
            result.exceptionOrNull()?.message shouldContain "Connection failed"
        }
        
        test("should handle malformed JSON") {
            whenever(networkClient.getBody(any(), any()))
                .thenReturn("invalid json")
            
            val result = ModeOperations.getAllModes(
                networkClient, makerApiAppId, makerApiToken, hubIp
            )
            
            result.isFailure shouldBe true
        }
    }
    
    context("getCurrentMode") {
        test("should find and return the active mode") {
            val modesJson = """
                [
                    {"name": "Home", "id": 1, "active": true},
                    {"name": "Away", "id": 2, "active": false},
                    {"name": "Night", "id": 3, "active": false}
                ]
            """.trimIndent()
            
            whenever(networkClient.getBody(any(), any())).thenReturn(modesJson)
            
            val result = ModeOperations.getCurrentMode(
                networkClient, makerApiAppId, makerApiToken, hubIp
            )
            
            result.isSuccess shouldBe true
            val mode = result.getOrNull()!!
            mode.name shouldBe "Home"
            mode.id shouldBe 1
            mode.active shouldBe true
        }
        
        test("should handle case when no active mode exists") {
            val modesJson = """
                [
                    {"name": "Home", "id": 1, "active": false},
                    {"name": "Away", "id": 2, "active": false}
                ]
            """.trimIndent()
            
            whenever(networkClient.getBody(any(), any())).thenReturn(modesJson)
            
            val result = ModeOperations.getCurrentMode(
                networkClient, makerApiAppId, makerApiToken, hubIp
            )
            
            result.isFailure shouldBe true
            result.exceptionOrNull()?.message shouldContain "No active mode found"
        }
        
        test("should propagate network errors") {
            whenever(networkClient.getBody(any(), any()))
                .thenThrow(RuntimeException("Network error"))
            
            val result = ModeOperations.getCurrentMode(
                networkClient, makerApiAppId, makerApiToken, hubIp
            )
            
            result.isFailure shouldBe true
            result.exceptionOrNull()?.message shouldContain "Network error"
        }
    }
    
    context("setMode") {
        test("should successfully change mode with valid name") {
            val modesJson = """
                [
                    {"name": "Home", "id": 1, "active": true},
                    {"name": "Away", "id": 2, "active": false},
                    {"name": "Night", "id": 3, "active": false}
                ]
            """.trimIndent()
            
            whenever(networkClient.getBody(any(), any())).thenReturn(modesJson)
            
            val mockResponse = mock<io.ktor.client.statement.HttpResponse> {
                on { status } doReturn io.ktor.http.HttpStatusCode.OK
            }
            whenever(networkClient.get(
                eq("http://hubitat.local/apps/api/test-app-id/modes/2"),
                eq(mapOf("access_token" to "test-token"))
            )).thenReturn(mockResponse)
            
            val result = ModeOperations.setMode(
                networkClient, makerApiAppId, makerApiToken, hubIp, "Away"
            )
            
            result.isSuccess shouldBe true
            result.getOrNull() shouldContain "Away"
        }
        
        test("should handle case-insensitive mode name matching") {
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
            
            val result = ModeOperations.setMode(
                networkClient, makerApiAppId, makerApiToken, hubIp, "away"
            )
            
            result.isSuccess shouldBe true
        }
        
        test("should return error when mode name is not found") {
            val modesJson = """
                [
                    {"name": "Home", "id": 1, "active": true},
                    {"name": "Away", "id": 2, "active": false}
                ]
            """.trimIndent()
            
            whenever(networkClient.getBody(any(), any())).thenReturn(modesJson)
            
            val result = ModeOperations.setMode(
                networkClient, makerApiAppId, makerApiToken, hubIp, "InvalidMode"
            )
            
            result.isFailure shouldBe true
            result.exceptionOrNull()?.message shouldContain "Mode not found"
        }
        
        test("should handle API failures") {
            val modesJson = """
                [
                    {"name": "Home", "id": 1, "active": true},
                    {"name": "Away", "id": 2, "active": false}
                ]
            """.trimIndent()
            
            whenever(networkClient.getBody(any(), any())).thenReturn(modesJson)
            whenever(networkClient.get(any(), any()))
                .thenThrow(RuntimeException("API error"))
            
            val result = ModeOperations.setMode(
                networkClient, makerApiAppId, makerApiToken, hubIp, "Away"
            )
            
            result.isFailure shouldBe true
            result.exceptionOrNull()?.message shouldContain "API error"
        }
    }
})
