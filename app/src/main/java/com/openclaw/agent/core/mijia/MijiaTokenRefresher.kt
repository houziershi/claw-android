package com.openclaw.agent.core.mijia

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MijiaTokenRefresher"

/**
 * After WebView login, we have passToken + serviceToken but NOT ssecurity.
 * This class calls the serviceLogin API with passToken cookie to obtain ssecurity,
 * then follows the location URL to refresh the serviceToken.
 *
 * This mirrors the Python SDK's refresh/login flow.
 */
@Singleton
class MijiaTokenRefresher @Inject constructor(
    private val httpClient: OkHttpClient,
    private val authStore: MijiaAuthStore
) {

    /**
     * Fetch ssecurity using existing passToken.
     * Returns updated MijiaAuth with ssecurity populated, or null on failure.
     */
    suspend fun refreshSsecurity(): MijiaAuth? = withContext(Dispatchers.IO) {
        val auth = authStore.load() ?: return@withContext null

        if (auth.passToken.isBlank()) {
            Log.w(TAG, "No passToken available, cannot refresh ssecurity")
            return@withContext null
        }

        try {
            // Step 1: Call serviceLogin with _json=true and passToken cookie
            val serviceLoginUrl = "https://account.xiaomi.com/pass/serviceLogin?_json=true&sid=mijia&_locale=zh_CN"

            val cookies = buildString {
                append("userId=${auth.userId}; ")
                append("passToken=${auth.passToken}; ")
                append("deviceId=${auth.deviceId}; ")
                if (auth.cUserId.isNotBlank()) append("cUserId=${auth.cUserId}; ")
                append("uLocale=${auth.locale}")
            }

            val request = Request.Builder()
                .url(serviceLoginUrl)
                .addHeader("User-Agent", auth.ua)
                .addHeader("Cookie", cookies)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            var body = response.body?.string() ?: ""

            // Response has "&&&START&&&" prefix
            if (body.startsWith("&&&START&&&")) {
                body = body.removePrefix("&&&START&&&")
            }

            Log.d(TAG, "serviceLogin response: ${body.take(300)}")

            val json = JSONObject(body)
            val code = json.optInt("code", -1)

            if (code == 0) {
                // Token still valid! Extract ssecurity from response
                val ssecurity = json.optString("ssecurity", "")
                val nonce = json.optString("nonce", "")
                val psecurity = json.optString("psecurity", "")
                val location = json.optString("location", "")
                val newUserId = json.optString("userId", auth.userId)
                val newCUserId = json.optString("cUserId", auth.cUserId)
                val newPassToken = json.optString("passToken", auth.passToken)

                if (ssecurity.isBlank()) {
                    Log.w(TAG, "serviceLogin returned code=0 but no ssecurity")
                    return@withContext null
                }

                Log.d(TAG, "Got ssecurity from serviceLogin! Following location URL...")

                // Step 2: Follow the location URL to get fresh serviceToken
                var newServiceToken = auth.serviceToken
                if (location.isNotBlank()) {
                    val locRequest = Request.Builder()
                        .url(location)
                        .addHeader("User-Agent", auth.ua)
                        .addHeader("Cookie", cookies)
                        .get()
                        .build()

                    // Don't follow redirects — we want the Set-Cookie from the response
                    val locClient = httpClient.newBuilder().followRedirects(false).build()
                    val locResponse = locClient.newCall(locRequest).execute()

                    // Extract serviceToken from Set-Cookie header
                    locResponse.headers.values("Set-Cookie").forEach { cookie ->
                        if (cookie.startsWith("serviceToken=")) {
                            newServiceToken = cookie.substringAfter("serviceToken=").substringBefore(";")
                            Log.d(TAG, "Got fresh serviceToken from location redirect")
                        }
                    }
                    locResponse.close()
                }

                val updatedAuth = auth.copy(
                    ssecurity = ssecurity,
                    serviceToken = newServiceToken,
                    passToken = newPassToken.ifBlank { auth.passToken },
                    userId = newUserId.ifBlank { auth.userId },
                    cUserId = newCUserId.ifBlank { auth.cUserId },
                    expireTime = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                )

                authStore.save(updatedAuth)
                Log.d(TAG, "Auth refreshed successfully with ssecurity!")
                return@withContext updatedAuth
            } else {
                Log.w(TAG, "serviceLogin returned code=$code, passToken may be expired")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh ssecurity", e)
            return@withContext null
        }
    }
}
