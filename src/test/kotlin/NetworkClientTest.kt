package jbaru.ch.telegram.hubitat

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel

class NetworkClientTest : FunSpec({
    
    context("KtorNetworkClient") {
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

        test("getBody returns the body on success") {
            val mockEngine = MockEngine {
                respond(
                    content = ByteReadChannel("""{"status": "success"}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type" to listOf("application/json"))
                )
            }

            val client = KtorNetworkClient(HttpClient(mockEngine))
            client.getBody("http://test.com/api") shouldBe """{"status": "success"}"""
        }

        test("getBody throws on a non-2xx status instead of returning the error page") {
            val mockEngine = MockEngine {
                respond(
                    content = ByteReadChannel("<html>Not Found</html>"),
                    status = HttpStatusCode.NotFound
                )
            }

            val client = KtorNetworkClient(HttpClient(mockEngine))
            val exception = shouldThrow<IllegalStateException> {
                client.getBody("http://test.com/api")
            }
            exception.message shouldContain "404"
        }
    }

    context("secret redaction") {
        test("redact masks sensitive param values but keeps the rest") {
            val redacted = KtorNetworkClient.redact(
                mapOf("access_token" to "secret123", "token" to "mgmt456", "mode" to "Away")
            )

            redacted["access_token"] shouldBe "[REDACTED]"
            redacted["token"] shouldBe "[REDACTED]"
            redacted["mode"] shouldBe "Away"
        }

        test("redact leaves a map with no sensitive keys unchanged") {
            val params = mapOf("mode" to "Home", "id" to "5")
            KtorNetworkClient.redact(params) shouldBe params
        }

        test("redactSecrets scrubs credential query params from exception text") {
            val msg = "Request timeout has expired [url=http://hub/apps/api/398/devices?access_token=abc123&x=1]"
            val redacted = KtorNetworkClient.redactSecrets(msg)
            (redacted.contains("abc123")) shouldBe false
            redacted shouldBe "Request timeout has expired [url=http://hub/apps/api/398/devices?access_token=[REDACTED]&x=1]"
            KtorNetworkClient.redactSecrets(null) shouldBe "unknown error"
        }

        test("isSecretResponse flags the management-token endpoint") {
            KtorNetworkClient.isSecretResponse("http://192.168.30.15/hub/advanced/getManagementToken") shouldBe true
            KtorNetworkClient.isSecretResponse("http://192.168.30.15/apps/api/398/devices") shouldBe false
        }
    }
})
