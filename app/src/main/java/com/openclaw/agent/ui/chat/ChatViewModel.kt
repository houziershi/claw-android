package com.openclaw.agent.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.agent.core.runtime.AgentEvent
import com.openclaw.agent.core.runtime.AgentRuntime
import com.openclaw.agent.core.session.SessionManager
import com.openclaw.agent.data.db.entities.MessageEntity
import com.openclaw.agent.data.preferences.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

/** Represents a tool call in progress or completed, for UI display */
data class ToolCallUiState(
    val id: String,
    val name: String,
    val input: JsonObject? = null,
    val result: String? = null,
    val success: Boolean? = null,
    val isRunning: Boolean = true
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agentRuntime: AgentRuntime,
    private val sessionManager: SessionManager,
    private val settingsStore: SettingsStore
) : ViewModel() {

    private var currentSessionId: String = ""

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Active tool calls being displayed in the UI */
    private val _activeToolCalls = MutableStateFlow<List<ToolCallUiState>>(emptyList())
    val activeToolCalls: StateFlow<List<ToolCallUiState>> = _activeToolCalls.asStateFlow()

    fun loadSession(sessionId: String) {
        currentSessionId = sessionId
        viewModelScope.launch {
            sessionManager.getMessages(sessionId).collect { msgs ->
                _messages.value = msgs
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isStreaming.value) return

        _isStreaming.value = true
        _streamingText.value = ""
        _error.value = null
        _activeToolCalls.value = emptyList()

        viewModelScope.launch {
            val model = settingsStore.selectedModelFlow.first()
            val apiKey = settingsStore.getApiKey()
            val baseUrl = settingsStore.getBaseUrl()

            agentRuntime.chat(
                sessionId = currentSessionId,
                userMessage = text,
                model = model,
                apiKey = apiKey,
                baseUrl = baseUrl
            ).collect { event ->
                when (event) {
                    is AgentEvent.TextChunk -> {
                        _streamingText.value += event.text
                    }
                    is AgentEvent.ToolCallStarted -> {
                        _activeToolCalls.value = _activeToolCalls.value + ToolCallUiState(
                            id = event.id,
                            name = event.name,
                            isRunning = true
                        )
                    }
                    is AgentEvent.ToolCallFinished -> {
                        _activeToolCalls.value = _activeToolCalls.value.map { tc ->
                            if (tc.id == event.id) {
                                tc.copy(
                                    input = event.input,
                                    result = event.result,
                                    success = event.success,
                                    isRunning = false
                                )
                            } else tc
                        }
                    }
                    is AgentEvent.Thinking -> {
                        // Reset streaming text for next LLM turn
                        _streamingText.value = ""
                    }
                    is AgentEvent.TurnComplete -> {
                        _streamingText.value = ""
                        _isStreaming.value = false
                        _activeToolCalls.value = emptyList()
                        // Auto-generate title from first message
                        if (_messages.value.size <= 2) {
                            val title = text.take(40).let {
                                if (text.length > 40) "$it..." else it
                            }
                            sessionManager.updateTitle(currentSessionId, title)
                        }
                    }
                    is AgentEvent.Error -> {
                        _error.value = event.message
                        _isStreaming.value = false
                        _streamingText.value = ""
                        _activeToolCalls.value = emptyList()
                    }
                }
            }
        }
    }
}
