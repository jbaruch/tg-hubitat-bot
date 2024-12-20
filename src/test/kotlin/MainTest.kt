package jbaru.ch.telegram.hubitat

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.Message
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import jbaru.ch.telegram.hubitat.model.Hub
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class MainTest {
    private lateinit var mockEngine: MockEngine
    private lateinit var mockClient: HttpClient
    private lateinit var mockMessage: Message

    @BeforeEach
    fun setup() {
        // Mock the bot
        bot = mock()
        whenever(bot.sendMessage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(mock())
        
        // Create mock message
        val mockChat = mock<Chat> {
            on { id } doReturn CHAT_ID.toLong()
        }
        mockMessage = mock {
            on { chat } doReturn mockChat
        }

        mockEngine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("/apps/api") && request.url.parameters["access_token"] != null -> {
                    val path = request.url.encodedPath
                    when {
                        path.contains("firmwareVersionString") -> respond(
                            content = """{"value": "2.3.6.126"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        path.contains("hubUpdateVersion") -> respond(
                            content = """{"value": "2.3.6.127"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        else -> error("Unhandled API path: $path")
                    }
                }
                request.url.encodedPath.contains("/management/firmwareUpdate") && request.url.parameters["token"] != null -> {
                    respond(
                        content = "",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> {
                    error("Unhandled ${request.url}")
                }
            }
        }
        mockClient = HttpClient(mockEngine)
        client = mockClient
        hubs = emptyList()
    }

    @Test
    fun `updateHubs handles successful update with version change`() = runBlocking {
        // Given
        val hub1 = Hub(1, "Main Hub", "token1", "192.168.1.100")
        val hub2 = Hub(2, "Backup Hub", "token2", "192.168.1.101")
        hubs = listOf(hub1, hub2)

        // Configure mock engine to simulate successful version change
        var callCount = 0
        mockEngine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("/apps/api") && request.url.parameters["access_token"] != null -> {
                    val path = request.url.encodedPath
                    when {
                        path.contains("firmwareVersionString") -> {
                            if (callCount <= 2) {
                                // Initial version checks
                                callCount++
                                respond(
                                    content = """{"value": "2.3.6.126"}""",
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                                )
                            } else {
                                // Version checks after update
                                respond(
                                    content = """{"value": "2.3.6.127"}""",
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                                )
                            }
                        }
                        path.contains("hubUpdateVersion") -> respond(
                            content = """{"value": "2.3.6.127"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        else -> error("Unhandled API path: $path")
                    }
                }
                request.url.encodedPath.contains("/management/firmwareUpdate") && request.url.parameters["token"] != null -> {
                    respond(
                        content = "",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("Unhandled ${request.url}")
            }
        }
        client = HttpClient(mockEngine)

        // When
        val result = updateHubs(maxAttempts = 2, delayMillis = 100) { _ -> }

        // Then
        assertTrue(result.isSuccess)
        val message = result.getOrNull()!!
        assertTrue(message.contains("All hubs updated successfully"))
    }

    @Test
    fun `updateHubs handles failed update command`() = runBlocking {
        // Given
        val hub = Hub(1, "Main Hub", "token1", "192.168.1.100")
        hubs = listOf(hub)

        // Configure mock engine to return error for update command
        mockEngine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("/apps/api") && request.url.parameters["access_token"] != null -> {
                    val path = request.url.encodedPath
                    when {
                        path.contains("firmwareVersionString") -> respond(
                            content = """{"value": "2.3.6.126"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        path.contains("hubUpdateVersion") -> respond(
                            content = """{"value": "2.3.6.127"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        else -> error("Unhandled API path: $path")
                    }
                }
                request.url.encodedPath.contains("/management/firmwareUpdate") && request.url.parameters["token"] != null -> {
                    respond(
                        content = "Internal Server Error",
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("Unhandled ${request.url}")
            }
        }
        client = HttpClient(mockEngine)

        // When
        val result = updateHubs(maxAttempts = 2, delayMillis = 100) { _ -> }

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()!!.message!!
        assertTrue(error.contains("Failed to update hub Main Hub"))
    }

    @Test
    fun `updateHubs handles timeout waiting for hub to come back online`() = runBlocking {
        // Given
        val hub = Hub(1, "Main Hub", "token1", "192.168.1.100")
        hubs = listOf(hub)

        // Configure mock engine to simulate hub never coming back online
        var callCount = 0
        mockEngine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("/apps/api") && request.url.parameters["access_token"] != null -> {
                    val path = request.url.encodedPath
                    when {
                        path.contains("firmwareVersionString") -> {
                            if (callCount == 0) {
                                // Initial version check
                                callCount++
                                respond(
                                    content = """{"value": "2.3.6.126"}""",
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                                )
                            } else {
                                // All subsequent version checks fail
                                respond(
                                    content = "",
                                    status = HttpStatusCode.ServiceUnavailable,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                                )
                            }
                        }
                        path.contains("hubUpdateVersion") -> respond(
                            content = """{"value": "2.3.6.127"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        else -> error("Unhandled API path: $path")
                    }
                }
                request.url.encodedPath.contains("/management/firmwareUpdate") && request.url.parameters["token"] != null -> {
                    respond(
                        content = "",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("Unhandled ${request.url}")
            }
        }
        client = HttpClient(mockEngine)

        // When
        val result = updateHubs(maxAttempts = 2, delayMillis = 100) { _ -> }

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()!!.message!!
        assertTrue(error.contains("Timeout waiting for hubs to complete update"))
        assertTrue(error.contains("Main Hub (still at version 2.3.6.126)"))
    }

    @Test
    fun `updateHubs skips update when version is current`() = runBlocking {
        // Given
        val hub = Hub(1, "Main Hub", "token1", "192.168.1.100")
        hubs = listOf(hub)

        // Configure mock engine to return same version for current and update
        mockEngine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("/apps/api") && request.url.parameters["access_token"] != null -> {
                    val path = request.url.encodedPath
                    when {
                        path.contains("firmwareVersionString") -> respond(
                            content = """{"value": "2.3.6.127"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        path.contains("hubUpdateVersion") -> respond(
                            content = """{"value": "2.3.6.127"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        else -> error("Unhandled API path: $path")
                    }
                }
                else -> error("Unhandled ${request.url}")
            }
        }
        client = HttpClient(mockEngine)

        // When
        val result = updateHubs(maxAttempts = 2, delayMillis = 100) { _ -> }

        // Then
        assertTrue(result.isSuccess)
        val message = result.getOrNull()!!
        assertTrue(message.contains("No updates needed"))
    }
}
