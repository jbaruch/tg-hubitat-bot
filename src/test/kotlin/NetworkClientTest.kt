package jbaru.ch.telegram.hubitat

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel

class NetworkClientTest : FunSpec({
    
    test("get should make successful request") {
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""{"status": "ok"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = KtorNetworkClient(HttpClient(mockEngine))
        
        val response = client.get("http://test.com", mapOf("key" to "value"))
        
        response.status shouldBe HttpStatusCode.OK
    }
    
    test("getBody should return response body") {
        val expectedBody = """{"status": "ok"}"""
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel(expectedBody),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = KtorNetworkClient(HttpClient(mockEngine))
        
        val body = client.getBody("http://test.com")
        
        body shouldBe expectedBody
    }
})
