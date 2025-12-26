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
private const val PROXY_BASE = "http://127.0.0.1:8081"        
private val client: OkHttpClient = OkHttpClient.Builder()
    .addInterceptor(AuthInterceptor()) // añade Authorization si SessionManager tiene token
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

suspend fun loginRequest(username: String, password: String): AuthResponse =
    ApiClient.login(username, password)

object ApiClient {
    private fun encode(v: String) = URLEncoder.encode(v, StandardCharsets.UTF_8.toString())

    suspend fun getEvents(): List<EventSummary> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE_URL/api/endpoints/v1/eventos")
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
                val price = when {
                    o.has("price") -> o.optDouble("price", 0.0)
                    o.has("precio") -> o.optDouble("precio", 0.0)
                    o.has("precioEntrada") -> o.optDouble("precioEntrada", 0.0)
                    o.has("precioVenta") -> o.optDouble("precioVenta", 0.0)
                    else -> 0.0
                }
                val rows = when {
                    o.has("rows") -> o.optInt("rows", 0)
                    o.has("filas") -> o.optInt("filas", 0)
                    o.has("filaAsientos") -> o.optInt("filaAsientos", 0)
                    else -> 0
                }
                val columns = when {
                    o.has("columns") -> o.optInt("columns", 0)
                    o.has("columnaAsientos") -> o.optInt("columnaAsientos", 0)
                    o.has("columnas") -> o.optInt("columnas", 0)
                    else -> 0
                }
                val totalSeats: Int = rows * columns
                val image = if (o.has("imagen")) o.optString("imagen") else null

                list.add(
                    EventSummary(
                        id = id,                          
                        title = title,                 
                        dateTime = dateTime,             
                        price = price,                    
                        totalSeats = totalSeats,          
                        rows = rows,                      
                        columns = columns,               
                        image = image                    
                    )
                )
            }
            return@withContext list
        }
    }

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


    // Helper to get session header value
    private fun sessionHeaderValue(): String {
        val token = SessionManager.getToken()
        println("[DEBUG front] Usando session header: $token")
        return token ?: ""
    }


    suspend fun blockSeats(eventId: Long, seatIds: List<String>): Boolean = withContext(Dispatchers.IO) {
        val url = "$PROXY_BASE/api/endpoints/v1/bloquear-asiento"
        val payload = JSONObject().apply {
            put("eventoId", eventId)
            put("seatIds", JSONArray(seatIds))
        }.toString()

        val req = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(JSON))
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Session-Id", sessionHeaderValue())
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (resp.code == 401) {
                SessionManager.clear()
                throw UnauthorizedException()
            }
            if (resp.code in 200..299) return@withContext true
            throw Exception("blockSeats failed: http=${resp.code} body=$body")
        }
    }


    suspend fun purchaseSeatsWithBuyer(eventId: Long, seatIds: List<String>, persona: String): Boolean = withContext(Dispatchers.IO) {
        val url = "$PROXY_BASE/api/endpoints/v1/realizar-ventas"
        val payload = JSONObject().apply {
            put("eventoId", eventId)
            put("seatIds", JSONArray(seatIds))
            put("persona", persona)
        }.toString()

        val req = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(JSON))
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Session-Id", sessionHeaderValue())
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (resp.code == 401) {
                SessionManager.clear()
                throw UnauthorizedException()
            }
            if (resp.code in 200..299) return@withContext true
            throw Exception("purchaseSeatsWithBuyer failed: http=${resp.code} body=$body")
        }
    }

    // Obtencion de ventas
    suspend fun getSales(): List<Sale> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE_URL/api/endpoints/v1/listar-ventas")
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
                if (code == 204) return@withContext emptyList<Sale>()
                throw Exception("getSales failed: http=$code body=$body")
            }

            val arr = JSONArray(body)
            val list = mutableListOf<Sale>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val evento = o.optString("evento", "")
                val asiento = o.optString("asiento", "")
                val fechaVenta = o.optString("fechaVenta", "")
                val comprador = o.optString("comprador", null) // Opcional: puede ser nulo
                list.add(Sale(evento, asiento, fechaVenta, comprador))
            }

            // Ordenar la lista por la fecha de venta (descendente)
            val sortedList = list.sortedByDescending { it.fechaVenta }
            return@withContext sortedList
        }
    }
    
    /* Helpers privados dentro de ApiClient (añádelos junto al resto del objeto) */
    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val it = obj.keys()
        while (it.hasNext()) {
            val key = it.next()
            val value = obj.get(key)
            map[key] = when (value) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null
                else -> value
            }
        }
        return map
    }


    suspend fun getSeats(eventId: Long): List<Seat> = withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("$PROXY_BASE/asientos/$eventId")                
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("X-Session-Id", sessionHeaderValue())
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: "[]"
                if (resp.code == 401) {
                    SessionManager.clear()
                    throw UnauthorizedException("Session expired")
                }
                if (resp.code !in 200..299) {
                    if (resp.code == 204) return@withContext emptyList<Seat>()
                    throw Exception("getSeats failed: http=${resp.code} body=$body")
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

    private fun jsonArrayToList(arr: JSONArray): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until arr.length()) {
            val v = arr.get(i)
            list.add(
                when (v) {
                    is JSONObject -> jsonObjectToMap(v)
                    is JSONArray -> jsonArrayToList(v)
                    JSONObject.NULL -> null
                    else -> v
                }
            )
        }
        return list
    }
}