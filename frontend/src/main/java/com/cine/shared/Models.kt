package com.cine.shared

data class AuthResponse(val idToken: String)

data class EventSummary(
    val id: Long,
    val title: String,
    val dateTime: String,
    val availableSeats: Int = 0,
    val price: Double = 0.0,
    val totalSeats: Int = 0,
    val rows: Int = 0,
    val columns: Int = 0,
    val image: String? = null,
    val asientosDisponibles: Int = 0, // Nuevo campo: número de disponibles
    val totalAsientos: Int = 0       // Nuevo campo: número total de asientos
)

data class Seat(
    val seatId: String,
    val status: String,
    val holder: String? = null,
    val updatedAt: String? = null
)


data class Sale(
    val evento: String,
    val asiento: String,
    val fechaVenta: String,
    val comprador: String
)

data class SeatSale(
    val fila: Int,
    val columna: Int,
    val nombre: String,
    val apellido: String
)

data class SaleDetail(
    val eventoId: Long,
    val ventaId: Long,
    val fechaVenta: String,
    val resultado: Boolean,
    val descripcion: String,
    val precioVenta: Double,
    val cantidadAsientos: Int,
    val asientos: List<SeatSale>
)
