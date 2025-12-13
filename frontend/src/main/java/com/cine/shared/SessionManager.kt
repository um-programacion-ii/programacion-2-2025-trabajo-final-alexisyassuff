package com.cine.shared

import android.content.Context
import android.content.SharedPreferences

object SessionManager {
    private const val PREF_NAME = "cine_prefs"
    private const val KEY_TOKEN = "session.token"
    private const val KEY_SEAT_TIMESTAMPS = "seat.timestamps"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    fun saveToken(token: String) {
        prefs?.edit()?.putString(KEY_TOKEN, token)?.apply()
    }

    fun getToken(): String? = prefs?.getString(KEY_TOKEN, null)

    fun clear() {
        prefs?.edit()?.remove(KEY_TOKEN)?.remove(KEY_SEAT_TIMESTAMPS)?.apply()
    }
    
    // Gestión de timestamps de bloqueo para persistir entre navegaciones
    fun saveSeatTimestamp(eventId: Long, seatId: String, timestamp: Long) {
        val key = "lock_${eventId}_${seatId}"
        prefs?.edit()?.putLong(key, timestamp)?.apply()
    }
    
    fun getSeatTimestamp(eventId: Long, seatId: String): Long? {
        val key = "lock_${eventId}_${seatId}"
        val timestamp = prefs?.getLong(key, -1L) ?: -1L
        return if (timestamp == -1L) null else timestamp
    }
    
    fun removeSeatTimestamp(eventId: Long, seatId: String) {
        val key = "lock_${eventId}_${seatId}"
        prefs?.edit()?.remove(key)?.apply()
    }
    
    fun clearExpiredTimestamps(eventId: Long) {
        val editor = prefs?.edit()
        val allPrefs = prefs?.all ?: return
        val currentTime = System.currentTimeMillis()
        
        allPrefs.keys.filter { it.startsWith("lock_${eventId}_") }.forEach { key ->
            val timestamp = allPrefs[key] as? Long
            if (timestamp != null && (currentTime - timestamp) >= 300_000) { // 5 minutos
                editor?.remove(key)
            }
        }
        editor?.apply()
    }
}
