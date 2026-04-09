package com.openclaw.agent.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    // EncryptedSharedPreferences for sensitive data (API keys)
    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "openclaw_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // DataStore keys for non-sensitive settings
    private object Keys {
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val SHOW_TOOL_CALLS = booleanPreferencesKey("show_tool_calls")
        val TWO_PHASE_ROUTING = booleanPreferencesKey("two_phase_routing")
        val CONTEXT_COMPACTION = booleanPreferencesKey("context_compaction")
        val USER_HOOKS_ENABLED = booleanPreferencesKey("user_hooks_enabled")
    }

    // Encrypted API key operations (synchronous, backed by EncryptedSharedPreferences)
    open fun getApiKey(): String {
        val saved = encryptedPrefs.getString(PREF_API_KEY, "") ?: ""
        return saved.ifBlank { DEFAULT_API_KEY }
    }

    fun saveApiKey(apiKey: String) {
        encryptedPrefs.edit().putString(PREF_API_KEY, apiKey).apply()
    }

    fun hasApiKey(): Boolean = getApiKey().isNotBlank()

    open fun getBaseUrl(): String {
        val saved = encryptedPrefs.getString(PREF_API_BASE_URL, "") ?: ""
        return saved.ifBlank { DEFAULT_BASE_URL }
    }

    fun saveBaseUrl(url: String) {
        encryptedPrefs.edit().putString(PREF_API_BASE_URL, url).apply()
    }

    // Model selection
    open val selectedModelFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.SELECTED_MODEL] ?: DEFAULT_MODEL
    }

    suspend fun saveSelectedModel(model: String) {
        dataStore.edit { prefs ->
            prefs[Keys.SELECTED_MODEL] = model
        }
    }

    // Theme mode: "system" | "light" | "dark"
    val themeModeFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE] ?: "system"
    }

    suspend fun saveThemeMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode
        }
    }

    // Custom system prompt override
    val systemPromptFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[Keys.SYSTEM_PROMPT]
    }

    suspend fun saveSystemPrompt(prompt: String?) {
        dataStore.edit { prefs ->
            if (prompt != null) {
                prefs[Keys.SYSTEM_PROMPT] = prompt
            } else {
                prefs.remove(Keys.SYSTEM_PROMPT)
            }
        }
    }

    // Show tool calls in chat
    open val showToolCallsFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SHOW_TOOL_CALLS] ?: false
    }

    suspend fun saveShowToolCalls(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SHOW_TOOL_CALLS] = show
        }
    }

    // Two-phase routing
    val twoPhaseRoutingEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.TWO_PHASE_ROUTING] ?: false
    }

    suspend fun saveTwoPhaseRoutingEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.TWO_PHASE_ROUTING] = enabled }
    }

    // Context compaction
    val contextCompactionEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.CONTEXT_COMPACTION] ?: true
    }

    suspend fun saveContextCompactionEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.CONTEXT_COMPACTION] = enabled }
    }

    // User hooks enabled
    val userHooksEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.USER_HOOKS_ENABLED] ?: false
    }

    suspend fun saveUserHooksEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.USER_HOOKS_ENABLED] = enabled }
    }

    // Extra API keys (stored in EncryptedSharedPreferences as JSON)
    fun getExtraApiKeys(): List<com.openclaw.agent.core.llm.ApiKeyEntry> {
        val rawJson = encryptedPrefs.getString(PREF_EXTRA_API_KEYS, null) ?: return emptyList()
        return try {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString<List<com.openclaw.agent.core.llm.ApiKeyEntry>>(rawJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveExtraApiKeys(keys: List<com.openclaw.agent.core.llm.ApiKeyEntry>) {
        val rawJson = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(com.openclaw.agent.core.llm.ApiKeyEntry.serializer()),
            keys
        )
        encryptedPrefs.edit().putString(PREF_EXTRA_API_KEYS, rawJson).apply()
    }

    companion object {
        private const val PREF_API_KEY = "claude_api_key"
        private const val PREF_API_BASE_URL = "api_base_url"
        private const val PREF_EXTRA_API_KEYS = "extra_api_keys"

        const val DEFAULT_MODEL = "k2p5"
        const val DEFAULT_API_KEY = "sk-kimi-KuvCIk4Jp4Jqp2GFEyz0PAIObkDWTMzyiI4pOTgpAKGkU0aKBCto6ifh5AtQ3nxm"
        const val DEFAULT_BASE_URL = "https://api.kimi.com/coding/v1/messages"

        val AVAILABLE_MODELS = listOf(
            "k2p5" to "Kimi K2P5 (Default)",
            "claude-opus-4-6" to "Claude Opus 4.6 (Most Capable)",
            "claude-sonnet-4-6" to "Claude Sonnet 4.6 (Balanced)",
            "claude-haiku-4-5-20251001" to "Claude Haiku 4.5 (Fast)",
            "claude-sonnet-4-5-20251001" to "Claude Sonnet 4.5",
        )
    }
}
