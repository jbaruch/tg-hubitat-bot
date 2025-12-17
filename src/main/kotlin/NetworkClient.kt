package jbaru.ch.telegram.hubitat

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.contentType
import org.slf4j.LoggerFactory

interface NetworkClient {
    suspend fun get(url: String, params: Map<String, String> = emptyMap()): HttpResponse
    suspend fun getBody(url: String, params: Map<String, String> = emptyMap()): String
    suspend fun put(url: String, params: Map<String, String> = emptyMap()): HttpResponse
}

class KtorNetworkClient(private val client: HttpClient) : NetworkClient {
    private val logger = LoggerFactory.getLogger(KtorNetworkClient::class.java)
    
    override suspend fun get(url: String, params: Map<String, String>): HttpResponse {
        logger.info("HTTP GET: $url with params: $params")
        val response = client.get(url) {
            params.forEach { (key, value) ->
                parameter(key, value)
            }
        }
        logger.info("HTTP Response: status=${response.status}, content-type=${response.contentType()}")
        return response
    }
    
    override suspend fun getBody(url: String, params: Map<String, String>): String {
        logger.info("HTTP GET (body): $url with params: $params")
        val response = get(url, params)
        val body = response.body<String>()
        val preview = if (body.length > 500) body.take(500) + "..." else body
        logger.info("HTTP Response body preview (${body.length} chars): $preview")
        return body
    }
    
    override suspend fun put(url: String, params: Map<String, String>): HttpResponse {
        logger.info("HTTP PUT: $url with params: $params")
        val response = client.put(url) {
            params.forEach { (key, value) ->
                parameter(key, value)
            }
        }
        logger.info("HTTP Response: status=${response.status}, content-type=${response.contentType()}")
        return response
    }
}
