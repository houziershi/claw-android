package com.openclaw.agent.core.web.adapters

import android.util.Log
import com.openclaw.agent.core.web.AdapterCommand
import com.openclaw.agent.core.web.AdapterResult
import com.openclaw.agent.core.web.ArgType
import com.openclaw.agent.core.web.AuthStrategy
import com.openclaw.agent.core.web.CommandArg
import com.openclaw.agent.core.web.WebAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

private const val TAG = "WikipediaAdapter"
private const val REST_BASE = "https://en.wikipedia.org/api/rest_v1"
private const val API_BASE = "https://en.wikipedia.org/w/api.php"

class WikipediaAdapter(private val client: OkHttpClient) : WebAdapter {
    override val site = "wikipedia"
    override val displayName = "Wikipedia"
    override val authStrategy = AuthStrategy.PUBLIC
    override val commands = listOf(
        AdapterCommand(
            name = "search",
            description = "Search Wikipedia articles",
            args = listOf(
                CommandArg("query", ArgType.STRING, required = true, description = "Search query"),
                CommandArg("limit", ArgType.INT, default = 5, description = "Number of results")
            )
        ),
        AdapterCommand(
            name = "summary",
            description = "Get article summary by title",
            args = listOf(
                CommandArg("title", ArgType.STRING, required = true, description = "Article title")
            )
        ),
        AdapterCommand(
            name = "random",
            description = "Get a random Wikipedia article summary",
            args = emptyList()
        )
    )

    override suspend fun execute(command: String, args: Map<String, Any>): AdapterResult {
        return when (command) {
            "search" -> searchArticles(args)
            "summary" -> getArticleSummary(args)
            "random" -> getRandomArticle()
            else -> AdapterResult(success = false, error = "Unknown command: $command")
        }
    }

    private suspend fun searchArticles(args: Map<String, Any>): AdapterResult {
        val query = args["query"] as? String
            ?: return AdapterResult(success = false, error = "Missing 'query' argument")
        val limit = (args["limit"] as? Int) ?: 5
        return withContext(Dispatchers.IO) {
            try {
                val url = API_BASE.toHttpUrl().newBuilder()
                    .addQueryParameter("action", "query")
                    .addQueryParameter("list", "search")
                    .addQueryParameter("srsearch", query)
                    .addQueryParameter("format", "json")
                    .addQueryParameter("srlimit", limit.toString())
                    .build()
                val body = get(url.toString())
                val root = Json.parseToJsonElement(body).jsonObject
                val searchResults = root["query"]?.jsonObject?.get("search")?.jsonArray
                    ?: return@withContext AdapterResult(success = true, data = emptyList())

                val results = searchResults.map { item ->
                    val obj = item.jsonObject
                    buildMap<String, Any> {
                        val title = obj["title"]?.jsonPrimitive?.content ?: ""
                        put("title", title)
                        put("snippet", obj["snippet"]?.jsonPrimitive?.content
                            ?.replace(Regex("<[^>]*>"), "") ?: "")
                        put("pageid", obj["pageid"]?.jsonPrimitive?.intOrNull ?: 0)
                        put("url", "https://en.wikipedia.org/wiki/${title.replace(" ", "_")}")
                    }
                }
                AdapterResult(success = true, data = results)
            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                AdapterResult(success = false, error = "Search failed: ${e.message}")
            }
        }
    }

    private suspend fun getArticleSummary(args: Map<String, Any>): AdapterResult {
        val title = args["title"] as? String
            ?: return AdapterResult(success = false, error = "Missing 'title' argument")
        return withContext(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(title, "UTF-8").replace("+", "_")
                val body = get("$REST_BASE/page/summary/$encoded")
                parseSummaryResponse(body)
            } catch (e: Exception) {
                Log.e(TAG, "Summary fetch failed", e)
                AdapterResult(success = false, error = "Failed to get summary: ${e.message}")
            }
        }
    }

    private suspend fun getRandomArticle(): AdapterResult {
        return withContext(Dispatchers.IO) {
            try {
                val body = get("$REST_BASE/page/random/summary")
                parseSummaryResponse(body)
            } catch (e: Exception) {
                Log.e(TAG, "Random article fetch failed", e)
                AdapterResult(success = false, error = "Failed to get random article: ${e.message}")
            }
        }
    }

    private fun parseSummaryResponse(body: String): AdapterResult {
        val obj = Json.parseToJsonElement(body).jsonObject
        val result = buildMap<String, Any> {
            put("title", obj["title"]?.jsonPrimitive?.content ?: "")
            put("description", obj["description"]?.jsonPrimitive?.content ?: "")
            put("extract", obj["extract"]?.jsonPrimitive?.content ?: "")
            put("url", obj["content_urls"]?.jsonObject?.get("desktop")?.jsonObject
                ?.get("page")?.jsonPrimitive?.content ?: "")
            obj["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.content?.let {
                put("thumbnail", it)
            }
        }
        return AdapterResult(success = true, data = listOf(result))
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "claw-android/1.0")
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        response.close()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        return body
    }
}
