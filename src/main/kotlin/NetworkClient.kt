package jbaru.ch.telegram.hubitat

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import org.slf4j.LoggerFactory

interface NetworkClient {
    suspend fun get(url: String, params: Map<String, String> = emptyMap()): HttpResponse
    suspend fun getBody(url: String, params: Map<String, String> = emptyMap()): String
}

class KtorNetworkClient(private val client: HttpClient) : NetworkClient {
    private val logger = LoggerFactory.getLogger(KtorNetworkClient::class.java)

    override suspend fun get(url: String, params: Map<String, String>): HttpResponse {
        logger.info("HTTP GET: $url with params: ${redact(params)}")
        val response = client.get(url) {
            params.forEach { (key, value) ->
                parameter(key, value)
            }
        }
        logger.info("HTTP Response: status=${response.status}, content-type=${response.contentType()}")
        return response
    }

    override suspend fun getBody(url: String, params: Map<String, String>): String {
        logger.info("HTTP GET (body): $url with params: ${redact(params)}")
        val response = get(url, params)
        // An error page must never reach a JSON parser looking like data. Callers
        // that expect mid-operation failures (hub rebooting during /update) catch
        // this and treat it as "not ready yet".
        if (!response.status.isSuccess()) {
            // The URL stays in the logs: handlers format e.message into chat
            // replies, and internal endpoints don't belong there.
            logger.warn("GET $url failed: HTTP ${response.status}")
            throw IllegalStateException(
                "Hub request failed: HTTP ${response.status}. " +
                    "Check that the hub is reachable and the Maker API app id/token are valid, then retry."
            )
        }
        val body = response.body<String>()
        if (isSecretResponse(url)) {
            logger.info("HTTP Response body (${body.length} chars): [REDACTED]")
        } else {
            val preview = if (body.length > 500) body.take(500) + "..." else body
            logger.info("HTTP Response body preview (${body.length} chars): $preview")
        }
        return body
    }

    companion object {
        // Query params whose values are credentials and must never hit the logs.
        private val SENSITIVE_KEYS = setOf("access_token", "token")

        internal fun redact(params: Map<String, String>): Map<String, String> =
            params.mapValues { (key, value) -> if (key in SENSITIVE_KEYS) "[REDACTED]" else value }

        // Endpoints whose response body is itself a secret (the hub management token).
        internal fun isSecretResponse(url: String): Boolean = url.contains("getManagementToken")
    }
}
