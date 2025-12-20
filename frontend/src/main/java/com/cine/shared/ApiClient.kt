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

    private suspend fun getAvailableSeatsCount(eventId: Long): Int {
        return try {
            val url = "${PROXY_BASE}/api/endpoints/v1/evento/$eventId"
            val req = Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.code in 200..299) {
                    val body = resp.body?.string() ?: ""
                    val obj = JSONObject(body)
                    // Si el endpoint devuelve "asientos" con estado, contamos los "LIBRE"
                    if (obj.has("asientos")) {
                        val asientos = obj.getJSONArray("asientos")
                        var available = 0
                        for (i in 0 until asientos.length()) {
                            val asiento = asientos.getJSONObject(i)
                            val status = asiento.optString("status", "LIBRE").uppercase()
                            if (status.contains("LIBRE") || status.contains("AVAILABLE") || status.contains("FREE")) {
                                available++
                            }
                        }
                        return available
                    } else {
                        // fallback: dimensiones
                        val rows = obj.optInt("filaAsientos", obj.optInt("rows", 0))
                        // soportar variantes de nombre de columnas
                        val cols = when {
                            obj.has("columnaAsientos") -> obj.optInt("columnaAsientos", 0)
                            obj.has("columns") -> obj.optInt("columns", 0)
                            else -> obj.optInt("columnas", 0)
                        }
                        return rows * cols
                    }
                } else {
                    0
                }
            }
        } catch (e: Exception) {
            0 // En caso de error, devolver 0
        }
    }

    suspend fun getEvents(): List<EventSummary> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${PROXY_BASE}/api/endpoints/v1/eventos")
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
                val available = when {
                    o.has("availableSeats") -> o.optInt("availableSeats")
                    o.has("disponibles") -> o.optInt("disponibles")
                    else -> 0
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
                val totalSeats = rows * columns
                val image = o.optString("imagen", null) // Extraer la URL de la imagen, si existe
                val realAvailable = if (id > 0) getAvailableSeatsCount(id) else available
                list.add(EventSummary(id, title, dateTime, realAvailable, price, totalSeats, rows, columns, image))
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

    // Helper to get session header value
    private fun sessionHeaderValue(): String {
        return SessionManager.getToken() ?: ""
    }


    // Bloqueo de múltiples asientos
    suspend fun blockMultipleSeats(eventId: Long, seatIds: List<String>): Boolean = withContext(Dispatchers.IO) {
        val url = "$PROXY_BASE/api/endpoints/v1/bloquear-asientos"
        val payload = JSONObject().apply {
            put("eventoId", eventId) // enviar eventoId en el body
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
            throw Exception("blockMultipleSeats failed: http=${resp.code} body=$body")
        }
    }

    // Bloqueo de un asiento individual
    suspend fun blockSeat(eventId: Long, seatId: String): Boolean = withContext(Dispatchers.IO) {
        val url = "$PROXY_BASE/api/endpoints/v1/bloquear-asiento"
        val payload = JSONObject().apply {
            put("eventoId", eventId) // incluir eventoId en el body
            put("seatId", seatId) // incluir seatId en el body
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
            throw Exception("blockSeat failed: http=${resp.code} body=$body")
        }
    }

    // Desbloquear un asiento
    suspend fun unlockSeat(eventId: Long, seatId: String): Boolean = withContext(Dispatchers.IO) {
        val url = "$PROXY_BASE/api/endpoints/v1/desbloquear-asiento"
        val payload = JSONObject().apply {
            put("eventoId", eventId) // Enviar eventoId en el cuerpo
            put("seatId", seatId) // Enviar seatId en el cuerpo
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
            throw Exception("unlockSeat failed: http=${resp.code} body=$body")
        }
    }

    // Desbloquear múltiples asientos
    suspend fun unblockMultipleSeats(eventId: Long, seatIds: List<String>): Boolean = withContext(Dispatchers.IO) {
        val url = "$PROXY_BASE/api/endpoints/v1/desbloquear-asientos"
        val payload = JSONObject().apply {
            put("eventoId", eventId) // Enviar eventoId en el cuerpo
            put("seatIds", JSONArray(seatIds)) // Enviar lista de seatIds en el cuerpo
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
            throw Exception("unblockMultipleSeats failed: http=${resp.code} body=$body")
        }
    }

    // Venta de asientos múltiples
    suspend fun purchaseMultipleSeatsWithBuyer(eventId: Long, seatIds: List<String>, persona: String): Boolean = withContext(Dispatchers.IO) {
        val url = "$PROXY_BASE/api/endpoints/v1/realizar-ventas" // Nueva ruta
        val payload = JSONObject().apply {
            put("eventoId", eventId) // Incluir eventoId en el body
            put("seatIds", JSONArray(seatIds)) // Lista de asientos
            put("persona", persona) // Datos del comprador
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
            throw Exception("purchaseMultipleSeatsWithBuyer failed: http=${resp.code} body=$body")
        }
    }

    // Venta de un asiento individual
    suspend fun purchaseSeatWithBuyer(eventId: Long, seatId: String, persona: String): Boolean = withContext(Dispatchers.IO) {
        val url = "$PROXY_BASE/api/endpoints/v1/realizar-venta" // Nueva ruta
        val payload = JSONObject().apply {
            put("eventoId", eventId) // Incluir eventoId en el body
            put("seatId", seatId) // ID del asiento
            put("persona", persona) // Datos del comprador
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
            throw Exception("purchaseSeatWithBuyer failed: http=${resp.code} body=$body")
        }
    }

    // Obtencion de ventas
    suspend fun getSales(): List<Sale> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$PROXY_BASE/api/endpoints/v1/listar-ventas")
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
    
    suspend fun getSeatState(eventId: Long, seatId: String): Map<String, Any?> = withContext(Dispatchers.IO) {
        val url = "$PROXY_BASE/asientos/$eventId/$seatId/state"
        val req = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json")
            .addHeader("X-Session-Id", sessionHeaderValue())
            .build()

        client.newCall(req).execute().use { resp ->
            val code = resp.code
            val body = resp.body?.string() ?: ""

            if (code == 401) {
                SessionManager.clear()
                throw UnauthorizedException()
            }
            if (code !in 200..299) {
                // 404 -> devolver mapa vacío para simplificar manejo en UI
                if (code == 404) return@withContext emptyMap<String, Any?>()
                throw Exception("getSeatState failed: http=$code body=$body")
            }

            val json = JSONObject(body)
            return@withContext jsonObjectToMap(json)
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