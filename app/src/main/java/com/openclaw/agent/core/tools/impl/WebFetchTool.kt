package com.openclaw.agent.core.tools.impl

import android.util.Log
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "WebFetchTool"

/**
 * Generic URL fetcher — the Android equivalent of `curl`.
 * Skills like weather, translation, etc. can use this as their underlying tool.
 */
class WebFetchTool(private val okHttpClient: OkHttpClient) : Tool {
    override val name = "web_fetch"
    override val description = "Fetch content from a URL. Similar to curl. Returns the response body as text. Use for APIs, web pages, or any HTTP GET request."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") {
                put("type", "string")
                put("description", "The URL to fetch (HTTP or HTTPS)")
            }
            putJsonObject("headers") {
                put("type", "object")
                put("description", "Optional HTTP headers as key-value pairs")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("url")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val url = args["url"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'url' parameter"
        )

        return try {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "curl/8.0")
                .get()

            // Add custom headers if provided
            args["headers"]?.jsonObject?.forEach { (key, value) ->
                requestBuilder.header(key, value.jsonPrimitive.content)
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            val body = response.body?.string() ?: ""
            val code = response.code
            response.close()

            if (code in 200..299) {
                ToolResult(success = true, content = body.take(50000)) // Cap at 50KB
            } else {
                ToolResult(success = false, content = body.take(2000), errorMessage = "HTTP $code")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fetch failed: $url", e)
            ToolResult(success = false, content = "", errorMessage = "Fetch failed: ${e.message}")
        }
    }
}
