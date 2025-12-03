package com.yassuff.cinemobile

import android.app.Application
import com.cine.shared.SessionManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicializa SessionManager una sola vez al arrancar la app
        SessionManager.init(this)
    }
}