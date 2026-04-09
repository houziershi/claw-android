package com.openclaw.agent.core.tools.impl

import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import com.openclaw.agent.core.web.AdapterRegistry
import kotlinx.serialization.json.*

class QueryWebTool(private val adapterRegistry: AdapterRegistry) : Tool {
    override val name = "query_web"
    override val description = "Query structured data from popular websites using built-in adapters. Use list_web_adapters first to see available sites and commands."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("site") {
                put("type", "string")
                put("description", "Site identifier, e.g. 'hackernews', 'wikipedia', 'arxiv', 'bbc', 'stackoverflow', 'yahoofinance'")
            }
            putJsonObject("command") {
                put("type", "string")
                put("description", "Command to execute on the site, e.g. 'top', 'search', 'summary', 'quote'")
            }
            putJsonObject("args") {
                put("type", "object")
                put("description", "Optional command arguments as key-value pairs, e.g. {\"query\": \"machine learning\", \"limit\": 5}")
            }
        }
        put("required", buildJsonArray {
            add(JsonPrimitive("site"))
            add(JsonPrimitive("command"))
        })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val site = args["site"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'site' parameter"
        )
        val command = args["command"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'command' parameter"
        )

        val adapter = adapterRegistry.getAdapter(site) ?: return ToolResult(
            success = false,
            content = "",
            errorMessage = "Unknown site: '$site'. Use list_web_adapters to see available sites."
        )

        // Parse the args object into Map<String, Any>
        val cmdArgs = mutableMapOf<String, Any>()
        args["args"]?.jsonObject?.forEach { (key, value) ->
            if (value is JsonPrimitive) {
                val intVal = value.intOrNull
                val boolVal = value.booleanOrNull
                when {
                    intVal != null -> cmdArgs[key] = intVal
                    boolVal != null -> cmdArgs[key] = boolVal
                    else -> cmdArgs[key] = value.content
                }
            }
        }

        val result = adapter.execute(command, cmdArgs)
        return if (result.success) {
            if (result.data.isEmpty()) {
                ToolResult(success = true, content = "No results found.")
            } else {
                val content = buildString {
                    result.data.forEachIndexed { index, item ->
                        if (index > 0) appendLine("---")
                        item.forEach { (k, v) -> appendLine("$k: $v") }
                    }
                }
                ToolResult(success = true, content = content.trimEnd())
            }
        } else {
            ToolResult(success = false, content = "", errorMessage = result.error ?: "Adapter returned an error")
        }
    }
}
