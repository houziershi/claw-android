package com.openclaw.agent.core.web

interface WebAdapter {
    val site: String
    val displayName: String
    val authStrategy: AuthStrategy
    val commands: List<AdapterCommand>
    suspend fun execute(command: String, args: Map<String, Any>): AdapterResult
}

enum class AuthStrategy { PUBLIC, COOKIE, HEADER, API_KEY }

data class AdapterCommand(
    val name: String,
    val description: String,
    val args: List<CommandArg> = emptyList()
)

data class CommandArg(
    val name: String,
    val type: ArgType,
    val required: Boolean = false,
    val default: Any? = null,
    val description: String = ""
)

enum class ArgType { STRING, INT, BOOLEAN }

data class AdapterResult(
    val success: Boolean,
    val data: List<Map<String, Any>> = emptyList(),
    val error: String? = null
)
