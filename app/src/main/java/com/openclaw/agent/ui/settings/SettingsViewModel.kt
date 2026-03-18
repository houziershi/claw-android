package com.openclaw.agent.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.agent.core.llm.ClaudeClient
import com.openclaw.agent.core.llm.LlmEvent
import com.openclaw.agent.core.llm.LlmMessage
import com.openclaw.agent.data.preferences.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val TAG = "SettingsViewModel"

/** Connection test result */
sealed class ConnectionTestState {
    object Idle : ConnectionTestState()
    object Testing : ConnectionTestState()
    data class Success(val reply: String, val inputTokens: Int, val outputTokens: Int) : ConnectionTestState()
    data class Failure(val error: String) : ConnectionTestState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore
) : ViewModel() {

    val selectedModel: Flow<String> = settingsStore.selectedModelFlow
    val themeMode: Flow<String> = settingsStore.themeModeFlow
    val availableModels = SettingsStore.AVAILABLE_MODELS
    val themeModes = listOf(
        "system" to "System Default",
        "light" to "Light",
        "dark" to "Dark"
    )

    private val _apiKey = MutableStateFlow(settingsStore.getApiKey())
    val apiKey: StateFlow<String> = _apiKey

    private val _baseUrl = MutableStateFlow(settingsStore.getBaseUrl())
    val baseUrl: StateFlow<String> = _baseUrl

    private val _connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    fun saveApiKey(key: String) {
        settingsStore.saveApiKey(key)
        _apiKey.value = key
    }

    fun saveBaseUrl(url: String) {
        settingsStore.saveBaseUrl(url)
        _baseUrl.value = url
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            settingsStore.saveSelectedModel(modelId)
        }
    }

    fun selectTheme(mode: String) {
        viewModelScope.launch {
            settingsStore.saveThemeMode(mode)
        }
    }

    /**
     * Test connectivity to the configured LLM API.
     * Sends a minimal "Hi" message (non-streaming) and checks the response.
     */
    fun testConnection(model: String) {
        if (_connectionTestState.value is ConnectionTestState.Testing) return

        _connectionTestState.value = ConnectionTestState.Testing

        viewModelScope.launch {
            val apiKey = settingsStore.getApiKey()
            val baseUrl = settingsStore.getBaseUrl()

            if (apiKey.isBlank()) {
                _connectionTestState.value = ConnectionTestState.Failure("API Key is empty")
                return@launch
            }

            Log.d(TAG, "Testing connection: model=$model, baseUrl=$baseUrl")

            val okHttp = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            val client = ClaudeClient(apiKey, okHttp, baseUrl)
            val testMessages = listOf(
                LlmMessage(role = "user", content = JsonPrimitive("Hi, reply with exactly: CONNECTION_OK"))
            )

            val replyBuilder = StringBuilder()
            var inputTokens = 0
            var outputTokens = 0
            var errorMsg: String? = null

            try {
                client.chat(
                    messages = testMessages,
                    systemPrompt = "You are a test assistant. Reply briefly.",
                    model = model,
                    maxTokens = 64
                ).collect { event ->
                    when (event) {
                        is LlmEvent.TextChunk -> replyBuilder.append(event.text)
                        is LlmEvent.Done -> {
                            inputTokens = event.inputTokens
                            outputTokens = event.outputTokens
                        }
                        is LlmEvent.Error -> {
                            errorMsg = event.message
                        }
                        else -> { /* ignore tool events */ }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection test exception", e)
                errorMsg = "Exception: ${e.message}"
            }

            if (errorMsg != null) {
                Log.e(TAG, "Connection test failed: $errorMsg")
                _connectionTestState.value = ConnectionTestState.Failure(errorMsg!!)
            } else if (replyBuilder.isEmpty()) {
                _connectionTestState.value = ConnectionTestState.Failure("Empty response — model returned no text")
            } else {
                val reply = replyBuilder.toString()
                Log.d(TAG, "Connection test success: reply=$reply, tokens=$inputTokens/$outputTokens")
                _connectionTestState.value = ConnectionTestState.Success(reply, inputTokens, outputTokens)
            }
        }
    }

    fun resetTestState() {
        _connectionTestState.value = ConnectionTestState.Idle
    }
}
