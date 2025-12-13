package com.cine.shared

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

// Ajustá estas URLs para pruebas en Emulator -> 10.0.2.2
private const val BASE_URL = "http://10.0.2.2:8080"
private const val PROXY_BASE = "http://10.0.2.2:8081"

@Serializable
data class EventSummary(
    val id: Long,
    val title: String,
    val dateTime: String,
    val availableSeats: Int = 0
)

@Serializable
data class Seat(
    val seatId: String,
    val status: String,
    val holder: String? = null,
    val updatedAt: String? = null
)

suspend fun ApiLogin(username: String, password: String): AuthResponse =
    ApiClient.login(username, password)

object ApiClient {
    suspend fun login(username: String, password: String): AuthResponse {
        val req = AuthRequest(username, password)
        return httpClient.post("$BASE_URL/api/authenticate") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
    }

    suspend fun getEvents(): List<EventSummary> {
        // Endpoint example - ajustá según tu backend real
        return httpClient.get("$BASE_URL/api/endpoints/v1/eventos-resumidos").body()
    }

    suspend fun getSeats(eventId: Long): List<Seat> {
        // Proxy returns a list of seat objects as seen earlier
        return httpClient.get("$PROXY_BASE/internal/kafka/events?eventoId=$eventId").body()
    }
}
