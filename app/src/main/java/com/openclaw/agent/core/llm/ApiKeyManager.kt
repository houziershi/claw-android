package com.openclaw.agent.core.llm

import android.util.Log
import com.openclaw.agent.data.preferences.SettingsStore
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ApiKeyManager"

@Serializable
data class ApiKeyEntry(
    val apiKey: String,
    val baseUrl: String,
    val label: String = ""
)

/**
 * Manages multiple API keys with automatic rotation on rate limit (HTTP 429/529).
 * 
 * Keys are tried in order: primary (from SettingsStore) + extras (from encrypted prefs).
 * On rate limit, rotates to next key. Supports exhaustion detection and retry counting.
 */
@Singleton
class ApiKeyManager @Inject constructor(
    private val settingsStore: SettingsStore
) {
    private var currentIndex = 0
    private var retryCount = 0

    /**
     * Get all available API keys (primary + extras).
     */
    fun getKeys(): List<ApiKeyEntry> {
        val primary = ApiKeyEntry(
            apiKey = settingsStore.getApiKey(),
            baseUrl = settingsStore.getBaseUrl(),
            label = "Primary"
        )
        val extras = settingsStore.getExtraApiKeys()
        return listOf(primary) + extras
    }

    /**
     * Get the current active API key.
     */
    fun getCurrentKey(): ApiKeyEntry {
        val keys = getKeys()
        if (keys.isEmpty()) {
            Log.w(TAG, "No API keys available, returning empty entry")
            return ApiKeyEntry("", "", "None")
        }
        return keys[currentIndex % keys.size]
    }

    /**
     * Rotate to the next API key (called on rate limit).
     * Returns the new current key.
     */
    fun onRateLimit(): ApiKeyEntry {
        currentIndex++
        retryCount++
        val newKey = getCurrentKey()
        Log.d(TAG, "Rate limit hit, rotating to key #$currentIndex: ${newKey.label}")
        return newKey
    }

    /**
     * Check if all keys have been tried at least once.
     */
    fun isExhausted(): Boolean {
        return currentIndex >= getKeys().size
    }

    /**
     * Reset to the first key (call at start of new request).
     */
    fun reset() {
        currentIndex = 0
        retryCount = 0
    }

    /**
     * Increment retry count (for exponential backoff tracking).
     */
    fun incrementRetry() {
        retryCount++
    }

    /**
     * Reset retry count.
     */
    fun resetRetry() {
        retryCount = 0
    }

    /**
     * Get current retry count.
     */
    fun getRetryCount(): Int = retryCount
}
