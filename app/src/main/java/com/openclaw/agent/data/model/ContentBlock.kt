package com.openclaw.agent.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
sealed class ContentBlock {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : ContentBlock()

    @Serializable
    @SerialName("tool_call")
    data class ToolCall(
        val id: String,
        val name: String,
        val arguments: JsonObject
    ) : ContentBlock()

    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        val toolCallId: String,
        val name: String,
        val content: String,
        val isError: Boolean = false
    ) : ContentBlock()

    @Serializable
    @SerialName("image")
    data class Image(
        val url: String,
        val mediaType: String,
        val alt: String? = null
    ) : ContentBlock()

    @Serializable
    @SerialName("thinking")
    data class Thinking(val text: String) : ContentBlock()
}
