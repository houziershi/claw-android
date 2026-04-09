package com.openclaw.agent.core.web.adapters

import android.util.Log
import com.openclaw.agent.core.web.AdapterCommand
import com.openclaw.agent.core.web.AdapterResult
import com.openclaw.agent.core.web.ArgType
import com.openclaw.agent.core.web.AuthStrategy
import com.openclaw.agent.core.web.CommandArg
import com.openclaw.agent.core.web.WebAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

private const val TAG = "HackerNewsAdapter"
private const val BASE_URL = "https://hacker-news.firebaseio.com/v0"
private const val ALGOLIA_URL = "https://hn.algolia.com/api/v1/search"

class HackerNewsAdapter(private val client: OkHttpClient) : WebAdapter {
    override val site = "hackernews"
    override val displayName = "Hacker News"
    override val authStrategy = AuthStrategy.PUBLIC
    override val commands = listOf(
        AdapterCommand(
            name = "top",
            description = "Get top stories",
            args = listOf(CommandArg("limit", ArgType.INT, default = 10, description = "Number of stories to return"))
        ),
        AdapterCommand(
            name = "new",
            description = "Get new stories",
            args = listOf(CommandArg("limit", ArgType.INT, default = 10, description = "Number of stories to return"))
        ),
        AdapterCommand(
            name = "best",
            description = "Get best stories",
            args = listOf(CommandArg("limit", ArgType.INT, default = 10, description = "Number of stories to return"))
        ),
        AdapterCommand(
            name = "search",
            description = "Search stories via Algolia",
            args = listOf(
                CommandArg("query", ArgType.STRING, required = true, description = "Search query"),
                CommandArg("limit", ArgType.INT, default = 10, description = "Number of results")
            )
        )
    )

    override suspend fun execute(command: String, args: Map<String, Any>): AdapterResult {
        return when (command) {
            "top" -> fetchStoryList("topstories", args)
            "new" -> fetchStoryList("newstories", args)
            "best" -> fetchStoryList("beststories", args)
            "search" -> searchStories(args)
            else -> AdapterResult(success = false, error = "Unknown command: $command")
        }
    }

    private suspend fun fetchStoryList(endpoint: String, args: Map<String, Any>): AdapterResult {
        val limit = (args["limit"] as? Int) ?: 10
        return withContext(Dispatchers.IO) {
            try {
                val idsJson = get("$BASE_URL/$endpoint.json")
                val ids = Json.parseToJsonElement(idsJson).jsonArray
                    .take(limit)
                    .map { it.jsonPrimitive.int }

                val stories = coroutineScope {
                    ids.map { id ->
                        async {
                            try {
                                val storyJson = get("$BASE_URL/item/$id.json")
                                parseStory(storyJson, id)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to fetch story $id", e)
                                null
                            }
                        }
                    }.mapNotNull { it.await() }
                }
                AdapterResult(success = true, data = stories)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch $endpoint", e)
                AdapterResult(success = false, error = "Failed to fetch stories: ${e.message}")
            }
        }
    }

    private suspend fun searchStories(args: Map<String, Any>): AdapterResult {
        val query = args["query"] as? String
            ?: return AdapterResult(success = false, error = "Missing 'query' argument")
        val limit = (args["limit"] as? Int) ?: 10
        return withContext(Dispatchers.IO) {
            try {
                val url = ALGOLIA_URL.toHttpUrl().newBuilder()
                    .addQueryParameter("query", query)
                    .addQueryParameter("tags", "story")
                    .addQueryParameter("hitsPerPage", limit.toString())
                    .build()
                val body = get(url.toString())
                val root = Json.parseToJsonElement(body).jsonObject
                val hits = root["hits"]?.jsonArray ?: return@withContext AdapterResult(
                    success = true, data = emptyList()
                )
                val results = hits.map { hit ->
                    val obj = hit.jsonObject
                    buildMap<String, Any> {
                        put("title", obj["title"]?.jsonPrimitive?.content ?: "")
                        put("url", obj["url"]?.jsonPrimitive?.content ?: "")
                        put("score", obj["points"]?.jsonPrimitive?.intOrNull ?: 0)
                        put("author", obj["author"]?.jsonPrimitive?.content ?: "")
                        put("comments", obj["num_comments"]?.jsonPrimitive?.intOrNull ?: 0)
                        val id = obj["objectID"]?.jsonPrimitive?.content ?: ""
                        put("hn_url", "https://news.ycombinator.com/item?id=$id")
                    }
                }
                AdapterResult(success = true, data = results)
            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                AdapterResult(success = false, error = "Search failed: ${e.message}")
            }
        }
    }

    private fun parseStory(json: String, id: Int): Map<String, Any>? {
        return try {
            val obj = Json.parseToJsonElement(json).jsonObject
            buildMap {
                put("title", obj["title"]?.jsonPrimitive?.content ?: return null)
                put("url", obj["url"]?.jsonPrimitive?.content ?: "https://news.ycombinator.com/item?id=$id")
                put("score", obj["score"]?.jsonPrimitive?.intOrNull ?: 0)
                put("author", obj["by"]?.jsonPrimitive?.content ?: "")
                put("comments", obj["descendants"]?.jsonPrimitive?.intOrNull ?: 0)
                put("hn_url", "https://news.ycombinator.com/item?id=$id")
            }
        } catch (e: Exception) {
            null
        }
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
