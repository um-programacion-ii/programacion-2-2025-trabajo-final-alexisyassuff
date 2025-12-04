package com.cine.shared

import android.content.Context
import android.content.SharedPreferences

object SessionManager {
    private const val PREF_NAME = "cine_prefs"
    private const val KEY_TOKEN = "session.token"
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
        prefs?.edit()?.remove(KEY_TOKEN)?.apply()
    }
}
