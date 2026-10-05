package com.bartech.fleet_guard_rfid_count

import android.content.Context
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.jvm.Throws
import kotlin.let
import kotlin.text.startsWith
import kotlin.text.substringAfter
import kotlin.text.toInt
import kotlin.text.trim
import kotlin.text.uppercase

class ApiManager(private val context: Context, private val prefs: Prefs) {

    private val okHttpClient: OkHttpClient

    init {
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(context, prefs))
            // Add explicit timeouts to prevent indefinite waiting
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun getBaseUrl(): String {
        val ipAddress = prefs.getIpAddress() ?: ""
        val port = prefs.getPortNumber() ?: ""
        val protocol = prefs.getProtocol() ?: ""
        return "$protocol://$ipAddress:$port"
    }

    @Throws(IOException::class, IllegalStateException::class, IllegalArgumentException::class)
    fun callApi(
        endpoint: String,
        method: String,
        body: String? = null,
        token: String? = null
    ): Response {
        val fullUrl = "${getBaseUrl()}/$endpoint"
        val requestBuilder = Request.Builder().url(fullUrl)

        token?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }

        when (method.uppercase()) {
            "GET" -> requestBuilder.get()
            "POST" -> {
                val requestBody = body?.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                    ?: "".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                requestBuilder.post(requestBody)
            }
            else -> throw kotlin.IllegalArgumentException("Unsupported method: $method")
        }

        val request = requestBuilder.build()

        try {
            return okHttpClient.newCall(request).execute()
        } catch (e: IOException) {
            // Check for the unique error message from the interceptor
            if (e.message?.startsWith("AuthException:") == true) {
                // The interceptor handled the auth error and redirected.
                // We'll create a synthetic error response with the correct status code and message.

                val originalErrorCode = e.message!!.substringAfter("Code:").trim().toInt()
                val originalErrorMessage = when (originalErrorCode) {
                    440 -> "Session Timed Out. Please Log In again."
                    401 -> "Unauthorized Access Found."
                    else -> "Authentication Error"
                }

                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(originalErrorCode)
                    .message(originalErrorMessage)
                    .body(null) // Keep the body null to trigger the else block in your Activity
                    .build()
            }
            throw e
        }
    }
}
