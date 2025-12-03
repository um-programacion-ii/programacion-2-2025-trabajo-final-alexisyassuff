package com.yassuff.cinemobile.network

import com.cine.shared.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = SessionManager.getToken()
        if (token.isNullOrBlank()) {
            return chain.proceed(original)
        }
        val requestWithAuth = original.newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()
        return chain.proceed(requestWithAuth)
    }
}