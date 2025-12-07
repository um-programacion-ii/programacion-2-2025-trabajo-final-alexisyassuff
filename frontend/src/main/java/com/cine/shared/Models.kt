package com.cine.shared

data class AuthResponse(val idToken: String)

data class EventSummary(
    val id: Long,
    val title: String,
    val dateTime: String,
    val availableSeats: Int = 0,
    val price: Double = 0.0
)

data class Seat(
    val seatId: String,
    val status: String,
    val holder: String? = null,
    val updatedAt: String? = null
)
