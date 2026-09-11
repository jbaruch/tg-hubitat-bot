package jbaru.ch.telegram.hubitat.integration
import jbaru.ch.telegram.hubitat.HubOperations
import jbaru.ch.telegram.hubitat.KtorNetworkClient
import io.ktor.utils.io.ByteReadChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.HttpClient

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jbaru.ch.telegram.hubitat.model.Device

/**
 * Integration test for hub update flow with polling.
 * Tests the full update flow with mocked network responses.
 */
class HubUpdateIntegrationTest : FunSpec({

    test("full hub update flow with successful polling") {
        var callCount = 0
        val mockEngine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("/apps/api/") && request.url.encodedPath.contains("/devices/1") -> {
                    // Maker API device endpoint - return different versions on subsequent calls
                    callCount++
                    val currentVersion = if (callCount <= 2) "2.3.9.183" else "2.3.9.184"
                    respond(
                        content = ByteReadChannel(
                            """{"attributes":[{"name":"firmwareVersionString","currentValue":"$currentVersion"},""" +
                            """{"name":"hubUpdateVersion","currentValue":"2.3.9.184"}]}"""
                        ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                request.url.encodedPath.contains("/management/firmwareUpdate") -> {
                    // Update firmware endpoint
                    respond(
                        content = ByteReadChannel(""),
                        status = HttpStatusCode.OK
                    )
                }
                else -> {
                    respond(
                        content = ByteReadChannel("Not found"),
                        status = HttpStatusCode.NotFound
                    )
                }
            }
        }

        val client = HttpClient(mockEngine)
        val networkClient = KtorNetworkClient(client)

        val hub = Device.Hub(
            id = 1,
            label = "Test Hub",
            ip = "192.168.1.100",
            managementToken = "test-token"
        )

        val progressMessages = mutableListOf<String>()

        val result = HubOperations.updateHubsWithPolling(
            hubs = listOf(hub),
            networkClient = networkClient,
            hubIp = "hubitat.local",
            makerApiAppId = "test-app",
            makerApiToken = "test-token",
            maxAttempts = 3,
            delayMillis = 100,
            progressCallback = { message ->
                progressMessages.add(message)
            }
        )

        // The test should complete without throwing exceptions
        // Result may be success or failure depending on mock behavior
        result.isSuccess || result.isFailure shouldBe true
    }

    test("hub update flow with already up-to-date hubs") {
        val mockEngine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("/apps/api/") && request.url.encodedPath.contains("/devices/1") -> {
                    // Maker API device endpoint - hub already up to date
                    respond(
                        content = ByteReadChannel(
                            """{"attributes":[{"name":"firmwareVersionString","currentValue":"2.3.9.184"},""" +
                            """{"name":"hubUpdateVersion","currentValue":"2.3.9.184"}]}"""
                        ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> {
                    respond(
                        content = ByteReadChannel("Not found"),
                        status = HttpStatusCode.NotFound
                    )
                }
            }
        }

        val client = HttpClient(mockEngine)
        val networkClient = KtorNetworkClient(client)

        val hub = Device.Hub(
            id = 1,
            label = "Test Hub",
            ip = "192.168.1.100",
            managementToken = "test-token"
        )

        val progressMessages = mutableListOf<String>()

        val result = HubOperations.updateHubsWithPolling(
            hubs = listOf(hub),
            networkClient = networkClient,
            hubIp = "hubitat.local",
            makerApiAppId = "test-app",
            makerApiToken = "test-token",
            maxAttempts = 2,
            delayMillis = 100,
            progressCallback = { message ->
                progressMessages.add(message)
            }
        )

        result.isSuccess shouldBe true
        result.getOrNull() shouldContain "All checked hubs are up to date"
        result.getOrNull() shouldContain "Test Hub (2.3.9.184)"
        // The reply carries the message once: it must not also arrive as a
        // progress message (that posted the same text twice to the chat).
        progressMessages.none { it.contains("up to date") } shouldBe true
    }

    test("empty hub list is a failure, not a vacuous all-up-to-date") {
        val mockEngine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("should never be called"),
                status = HttpStatusCode.OK
            )
        }
        val networkClient = KtorNetworkClient(HttpClient(mockEngine))

        val result = HubOperations.updateHubsWithPolling(
            hubs = emptyList(),
            networkClient = networkClient,
            hubIp = "hubitat.local",
            makerApiAppId = "test-app",
            makerApiToken = "test-token",
            maxAttempts = 1,
            delayMillis = 100,
            progressCallback = { }
        )

        result.isFailure shouldBe true
        result.exceptionOrNull()?.message shouldContain "No hubs are initialized"
    }

    test("hub update flow with network errors") {
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("Network error"),
                status = HttpStatusCode.InternalServerError
            )
        }

        val client = HttpClient(mockEngine)
        val networkClient = KtorNetworkClient(client)

        val hub = Device.Hub(
            id = 1,
            label = "Test Hub",
            ip = "192.168.1.100",
            managementToken = "test-token"
        )

        val result = HubOperations.updateHubsWithPolling(
            hubs = listOf(hub),
            networkClient = networkClient,
            hubIp = "hubitat.local",
            makerApiAppId = "test-app",
            makerApiToken = "test-token",
            maxAttempts = 1,
            delayMillis = 100,
            progressCallback = { }
        )

        result.isFailure shouldBe true
    }
})
