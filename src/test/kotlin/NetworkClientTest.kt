package jbaru.ch.telegram.hubitat

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel

class NetworkClientTest : FunSpec({
    
    context("KtorNetworkClient") {
        test("put should make PUT request with parameters") {
            val mockEngine = MockEngine { request ->
                request.method shouldBe HttpMethod.Put
                request.url.toString() shouldBe "http://test.com/api?access_token=token123"
                
                respond(
                    content = ByteReadChannel("""{"status": "success"}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type" to listOf("application/json"))
                )
            }
            
            val client = KtorNetworkClient(HttpClient(mockEngine))
            val response = client.put(
                "http://test.com/api",
                mapOf("access_token" to "token123")
            )
            
            response.status shouldBe HttpStatusCode.OK
        }
        
        test("get should make GET request with parameters") {
            val mockEngine = MockEngine { request ->
                request.method shouldBe HttpMethod.Get
                request.url.toString() shouldBe "http://test.com/api?access_token=token123"
                
                respond(
                    content = ByteReadChannel("""{"status": "success"}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type" to listOf("application/json"))
                )
            }
            
            val client = KtorNetworkClient(HttpClient(mockEngine))
            val response = client.get(
                "http://test.com/api",
                mapOf("access_token" to "token123")
            )
            
            response.status shouldBe HttpStatusCode.OK
        }
    }
})
