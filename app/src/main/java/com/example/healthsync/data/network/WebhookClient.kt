package com.example.healthsync.data.network

import android.util.Log
import com.example.healthsync.data.model.HealthSyncPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

sealed class WebhookResult {
    data class Success(val statusCode: Int, val responseBody: String) : WebhookResult()
    data class Failure(val statusCode: Int? = null, val errorMessage: String) : WebhookResult()
}

class WebhookClient {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun sendPayload(
        url: String,
        payload: HealthSyncPayload,
        customHeaderName: String? = null,
        customHeaderValue: String? = null
    ): WebhookResult = withContext(Dispatchers.IO) {
        if (url.isBlank()) {
            return@withContext WebhookResult.Failure(errorMessage = "Webhook URL が設定されていません")
        }

        try {
            val jsonString = json.encodeToString(payload)
            val requestBody = jsonString.toRequestBody("application/json; charset=utf-8".toMediaType())

            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("User-Agent", "HealthSync-Android-Exporter/1.0")

            if (!customHeaderName.isNullOrBlank() && !customHeaderValue.isNullOrBlank()) {
                requestBuilder.addHeader(customHeaderName, customHeaderValue)
            }

            val request = requestBuilder.build()
            Log.d("WebhookClient", "Sending payload to $url (JSON size: ${jsonString.length} chars)")

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val code = response.code

                if (response.isSuccessful) {
                    Log.d("WebhookClient", "Webhook success (code=$code): $body")
                    WebhookResult.Success(statusCode = code, responseBody = body)
                } else {
                    Log.w("WebhookClient", "Webhook returned non-2xx code: $code body: $body")
                    WebhookResult.Failure(
                        statusCode = code,
                        errorMessage = "サーバーエラー (HTTP $code): ${body.take(200)}"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("WebhookClient", "Error sending webhook", e)
            WebhookResult.Failure(
                statusCode = null,
                errorMessage = e.localizedMessage ?: "通信エラーが発生しました"
            )
        }
    }
}
