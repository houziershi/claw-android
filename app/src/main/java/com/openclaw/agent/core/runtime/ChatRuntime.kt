package com.openclaw.agent.core.runtime

import kotlinx.coroutines.flow.Flow

interface ChatRuntime {
    fun chat(
        sessionId: String,
        userMessage: String,
        model: String,
        apiKey: String,
        baseUrl: String = "https://api.anthropic.com/v1/messages"
    ): Flow<AgentEvent>
}
