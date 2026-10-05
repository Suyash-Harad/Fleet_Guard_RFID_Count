package com.bartech.fleet_guard_rfid_count

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.apply
import kotlin.jvm.java
import kotlin.text.isNullOrEmpty

class AuthInterceptor(private val context: Context, private val prefs: Prefs) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val originalResponse = chain.proceed(request)

        when (originalResponse.code) {
            440, 401 -> {
                Log.d("AuthInterceptor", "Session expired or unauthorized. Status code: ${originalResponse.code}")

                try {
                    callLogoutApi(prefs.getToken())
                } catch (e: Exception) {
                    Log.e("AuthInterceptor", "Error calling logout API: ${e.message}")
                }

                prefs.clearToken()

                val mainHandler = Handler(context.mainLooper)
                mainHandler.post {
                    val intent = Intent(context, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    context.startActivity(intent)
                }

                // Throw IOException with a custom message that ApiManager will recognize.
                throw IOException("AuthException: Authentication handled. Code:${originalResponse.code}")
            }
        }
        return originalResponse
    }

    private fun callLogoutApi(token: String?) {
        if (token.isNullOrEmpty()) {
            return
        }

        val ipAddress = prefs.getIpAddress() ?: ""
        val port = prefs.getPortNumber() ?: ""
        val protocol = prefs.getProtocol() ?: ""
        val fullUrl = "$protocol://$ipAddress:$port/api/admin/logout"

        val client = OkHttpClient()
        val requestBody = "".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(fullUrl)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        try {
            client.newCall(request).execute()
            Log.d("AuthInterceptor", "Logout API call successful.")
        } catch (e: IOException) {
            Log.e("AuthInterceptor", "Logout API call failed: ${e.message}")
        }
    }
}