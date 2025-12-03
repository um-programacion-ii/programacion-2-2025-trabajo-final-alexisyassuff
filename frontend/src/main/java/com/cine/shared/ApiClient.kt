package com.cine.shared

import com.yassuff.cinemobile.network.AuthInterceptor
import com.yassuff.cinemobile.network.UnauthorizedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

private val JSON = "application/json; charset=utf-8".toMediaType()

// Para device con adb reverse use 127.0.0.1; para emulador 10.0.2.2
private const val BASE_URL = "http://127.0.0.1:8080"   // backend principal (tokens, etc.)
private const val PROXY_BASE = "http://127.0.0.1:8081" // proxy que sirve eventos/asientos

private val client: OkHttpClient = OkHttpClient.Builder()
    .addInterceptor(AuthInterceptor()) // añade Authorization si SessionManager tiene token
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

suspend fun loginRequest(username: String, password: String): AuthResponse =
    ApiClient.login(username, password)

object ApiClient {
    private fun encode(v: String) = URLEncoder.encode(v, StandardCharsets.UTF_8.toString())

    suspend fun login(username: String, password: String): AuthResponse = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/authenticate"

        val payload = JSONObject().apply {
            put("username", username)
            put("password", password)
        }.toString()

        val req = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(JSON))
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(req).execute().use { resp ->
            val code = resp.code
            val body = resp.body?.string() ?: ""
            if (code == 401) {
                throw UnauthorizedException()
            }
            if (code !in 200..299) {
                throw Exception("Login failed: http=$code body=$body")
            }
            val obj = JSONObject(body)
            val token = when {
                obj.has("token") -> obj.getString("token")
                obj.has("idToken") -> obj.getString("idToken")
                obj.has("accessToken") -> obj.getString("accessToken")
                else -> throw Exception("Token not found in response: $body")
            }
            return@withContext AuthResponse(token)
        }
    }

    // getEvents ahora apunta al proxy en 8081 /eventos y maneja códigos HTTP
    suspend fun getEvents(): List<EventSummary> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$PROXY_BASE/eventos")
            .get()
            .addHeader("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            val code = resp.code
            val body = resp.body?.string() ?: ""
            if (code == 401) {
                SessionManager.clear()
                throw UnauthorizedException("Session expired")
            }
            if (code !in 200..299) {
                // Si devuelve 204 (no content) devolvemos lista vacía
                if (code == 204) return@withContext emptyList<EventSummary>()
                throw Exception("getEvents failed: http=$code body=$body")
            }
            val arr = JSONArray(body)
            val list = mutableListOf<EventSummary>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = when {
                    o.has("id") -> o.getLong("id")
                    o.has("eventoId") -> o.getLong("eventoId")
                    else -> i.toLong()
                }
                val title = when {
                    o.has("title") -> o.optString("title")
                    o.has("titulo") -> o.optString("titulo")
                    else -> "Evento $id"
                }
                val dateTime = when {
                    o.has("dateTime") -> o.optString("dateTime")
                    o.has("fecha") -> o.optString("fecha")
                    else -> ""
                }
                val available = when {
                    o.has("availableSeats") -> o.optInt("availableSeats")
                    o.has("disponibles") -> o.optInt("disponibles")
                    else -> 0
                }
                list.add(EventSummary(id, title, dateTime, available))
            }
            return@withContext list
        }
    }

    suspend fun getSeats(eventId: Long): List<Seat> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$PROXY_BASE/internal/kafka/events?eventoId=$eventId")
            .get()
            .addHeader("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            val code = resp.code
            val body = resp.body?.string() ?: "[]"
            if (code == 401) {
                SessionManager.clear()
                throw UnauthorizedException("Session expired")
            }
            val arr = JSONArray(body)
            val list = mutableListOf<Seat>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val seatId = o.optString("seatId", o.optString("asientoId", "unknown"))
                val status = o.optString("status", o.optString("estado", "LIBRE"))
                val holder = if (o.has("holder")) o.optString("holder") else o.optString("usuario", null)
                val updatedAt = o.optString("updatedAt", null)
                list.add(Seat(seatId, status, holder, updatedAt))
            }
            return@withContext list
        }
    }
}