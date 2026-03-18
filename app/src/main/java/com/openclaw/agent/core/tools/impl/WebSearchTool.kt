package com.openclaw.agent.core.tools.impl

import android.util.Log
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TAG = "WebSearchTool"

class WebSearchTool(private val okHttpClient: OkHttpClient) : Tool {
    override val name = "web_search"
    override val description = "Search the web using DuckDuckGo. Returns search results with titles, URLs, and snippets. Use this when you need current information."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "Search query")
            }
        }
        put("required", kotlinx.serialization.json.buildJsonArray {
            add(kotlinx.serialization.json.JsonPrimitive("query"))
        })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val query = args["query"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'query' parameter"
        )

        return withContext(Dispatchers.IO) { try {
            // Use DuckDuckGo HTML search (no API key needed)
            val url = "https://html.duckduckgo.com/html/".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()

            // Parse results from HTML
            val results = parseSearchResults(body)
            if (results.isEmpty()) {
                ToolResult(success = true, content = "No results found for: $query")
            } else {
                val formatted = results.take(5).joinToString("\n\n") { (title, url, snippet) ->
                    "### $title\n$url\n$snippet"
                }
                ToolResult(success = true, content = "Search results for \"$query\":\n\n$formatted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            ToolResult(success = false, content = "", errorMessage = "Search failed: ${e.message}")
        } }
    }

    private fun parseSearchResults(html: String): List<Triple<String, String, String>> {
        val results = mutableListOf<Triple<String, String, String>>()
        // Simple regex-based parsing of DuckDuckGo HTML results
        val linkPattern = Regex("""<a rel="nofollow" class="result__a" href="([^"]*)"[^>]*>(.*?)</a>""")
        val snippetPattern = Regex("""<a class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)

        val links = linkPattern.findAll(html).toList()
        val snippets = snippetPattern.findAll(html).toList()

        for (i in links.indices) {
            val link = links[i]
            val url = link.groupValues[1].let {
                // DuckDuckGo wraps URLs, try to extract actual URL
                Regex("""uddg=([^&]+)""").find(it)?.groupValues?.get(1)?.let { encoded ->
                    java.net.URLDecoder.decode(encoded, "UTF-8")
                } ?: it
            }
            val title = link.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
            val snippet = if (i < snippets.size) {
                snippets[i].groupValues[1].replace(Regex("<[^>]*>"), "").trim()
            } else ""

            if (title.isNotBlank()) {
                results.add(Triple(title, url, snippet))
            }
        }
        return results
    }
}
