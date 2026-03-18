package com.openclaw.agent.core.llm

import kotlinx.coroutines.flow.Flow

/**
 * Unified interface for LLM providers.
 * All providers must support streaming via Flow<LlmEvent>.
 */
interface LlmClient {

    /**
     * Send a chat request and receive a stream of events.
     *
     * @param messages Conversation history (user + assistant turns)
     * @param systemPrompt Optional system prompt injected before the conversation
     * @param tools Tool definitions available to the model (empty = no tool use)
     * @param model Model identifier (e.g. "claude-sonnet-4-5-20251001")
     * @param maxTokens Maximum tokens to generate
     */
    fun chat(
        messages: List<LlmMessage>,
        systemPrompt: String? = null,
        tools: List<ToolDefinition> = emptyList(),
        model: String,
        maxTokens: Int = 4096
    ): Flow<LlmEvent>
}
