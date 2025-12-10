package jbaru.ch.telegram.hubitat

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse

interface NetworkClient {
    suspend fun get(url: String, params: Map<String, String> = emptyMap()): HttpResponse
    suspend fun getBody(url: String, params: Map<String, String> = emptyMap()): String
    suspend fun put(url: String, params: Map<String, String> = emptyMap()): HttpResponse
}

class KtorNetworkClient(private val client: HttpClient) : NetworkClient {
    override suspend fun get(url: String, params: Map<String, String>): HttpResponse {
        return client.get(url) {
            params.forEach { (key, value) ->
                parameter(key, value)
            }
        }
    }
    
    override suspend fun getBody(url: String, params: Map<String, String>): String {
        return get(url, params).body()
    }
    
    override suspend fun put(url: String, params: Map<String, String>): HttpResponse {
        return client.put(url) {
            params.forEach { (key, value) ->
                parameter(key, value)
            }
        }
    }
}
