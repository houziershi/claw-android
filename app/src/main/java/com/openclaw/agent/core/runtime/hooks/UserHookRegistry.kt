package com.openclaw.agent.core.runtime.hooks

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "UserHookRegistry"
private const val PREFS_NAME = "user_hooks"
private const val KEY_HOOKS_JSON = "hooks_json"

@Serializable
data class UserHookConfig(
    val id: String,
    val event: String,      // "PreToolUse" or "PostToolUse"
    val matcher: String,    // regex matching tool name
    val action: String,     // "confirm", "deny", "log"
    val message: String,
    val enabled: Boolean = true
)

/**
 * Registry for user-defined hook configurations.
 * Stores hooks as JSON in SharedPreferences.
 */
@Singleton
class UserHookRegistry @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Get all registered user hooks.
     */
    fun getHooks(): List<UserHookConfig> {
        val jsonStr = prefs.getString(KEY_HOOKS_JSON, null)
        if (jsonStr.isNullOrBlank()) return emptyList()

        return try {
            json.decodeFromString<List<UserHookConfig>>(jsonStr)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode hooks JSON", e)
            emptyList()
        }
    }

    /**
     * Save hooks to preferences.
     */
    fun saveHooks(hooks: List<UserHookConfig>) {
        val jsonStr = try {
            json.encodeToString(hooks)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode hooks", e)
            return
        }
        prefs.edit().putString(KEY_HOOKS_JSON, jsonStr).apply()
        Log.d(TAG, "Saved ${hooks.size} hooks")
    }

    /**
     * Add a new hook configuration.
     */
    fun addHook(config: UserHookConfig) {
        val current = getHooks().toMutableList()
        current.add(config)
        saveHooks(current)
    }

    /**
     * Remove a hook by ID.
     */
    fun removeHook(id: String) {
        val current = getHooks().toMutableList()
        current.removeAll { it.id == id }
        saveHooks(current)
    }
}
