package com.openclaw.agent.core.tools.impl

import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import com.openclaw.agent.core.web.AdapterRegistry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ListAdaptersTool(private val adapterRegistry: AdapterRegistry) : Tool {
    override val name = "list_web_adapters"
    override val description = "List all available web data source adapters and their supported commands. Use this before calling query_web to see what sites and commands are available."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val adapters = adapterRegistry.getAllAdapters()
        if (adapters.isEmpty()) {
            return ToolResult(success = true, content = "No web adapters available.")
        }

        val content = buildString {
            appendLine("## Available Web Adapters")
            appendLine()
            adapters.forEach { adapter ->
                appendLine("### ${adapter.displayName} (site: `${adapter.site}`)")
                appendLine("- Auth: ${adapter.authStrategy}")
                appendLine("- Commands:")
                adapter.commands.forEach { cmd ->
                    append("  - `${cmd.name}`: ${cmd.description}")
                    if (cmd.args.isNotEmpty()) {
                        val argList = cmd.args.joinToString(", ") { arg ->
                            "${arg.name} (${arg.type}${if (arg.required) ", required" else ", optional"}${if (arg.default != null) ", default=${arg.default}" else ""})"
                        }
                        append(" — args: $argList")
                    }
                    appendLine()
                }
                appendLine()
            }
        }
        return ToolResult(success = true, content = content.trimEnd())
    }
}
