package com.openclaw.agent.core.tools.impl

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "AgentTaskWorker"
private const val CHANNEL_ID = "claw_alarm"

/**
 * Executes an agent task: calls LLM with the stored prompt,
 * then updates the notification with the result.
 * If LLM fails, the fallback notification from AlarmReceiver remains.
 */
class AgentTaskWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId = inputData.getString("task_id") ?: ""
        val prompt = inputData.getString("prompt") ?: return@withContext Result.failure()
        val message = inputData.getString("message") ?: "Claw"
        val notifId = inputData.getInt("notif_id", taskId.hashCode())

        Log.d(TAG, "Executing agent task: $taskId, prompt=$prompt")

        try {
            val result = callLlm(prompt)
            Log.d(TAG, "LLM result: ${result.take(100)}")

            // Update the fallback notification with actual result
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("🤖 $message")
                .setContentText(result.take(100))
                .setStyle(NotificationCompat.BigTextStyle().bigText(result))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(notifId, notification) // Same ID replaces the fallback

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Agent task failed", e)

            // Update notification to show failure (fallback message stays useful)
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ $message")
                .setContentText("智能任务执行失败，请手动查看")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(notifId, notification)

            Result.failure()
        }
    }

    private fun callLlm(prompt: String): String {
        val securePrefs = try {
            androidx.security.crypto.EncryptedSharedPreferences.create(
                "openclaw_secure_prefs",
                androidx.security.crypto.MasterKeys.getOrCreate(
                    androidx.security.crypto.MasterKeys.AES256_GCM_SPEC
                ),
                appContext,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) { null }

        val apiKey = securePrefs?.getString("api_key", null)
            ?: "sk-kimi-KuvCIk4Jp4Jqp2GFEyz0PAIObkDWTMzyiI4pOTgpAKGkU0aKBCto6ifh5AtQ3nxm"
        val baseUrl = securePrefs?.getString("base_url", null)
            ?: "https://api.kimi.com/coding/v1/messages"
        val model = securePrefs?.getString("model", null) ?: "k2p5"

        val requestBody = buildJsonObject {
            put("model", model)
            put("max_tokens", 300)
            put("stream", false)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", prompt)
                }
            }
        }.toString()

        val request = Request.Builder()
            .url(baseUrl)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = okHttpClient.newCall(request).execute()
        val body = response.body?.string() ?: ""
        response.close()

        if (response.code !in 200..299) return "AI 暂不可用 (HTTP ${response.code})"

        val json = Json.parseToJsonElement(body).jsonObject
        return json["content"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")?.jsonPrimitive?.content?.trim()
            ?: "暂无回答"
    }
}
