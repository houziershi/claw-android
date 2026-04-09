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

private const val TAG = "StackOverflowAdapter"
private const val API_BASE = "https://api.stackexchange.com/2.3"

class StackOverflowAdapter(private val client: OkHttpClient) : WebAdapter {
    override val site = "stackoverflow"
    override val displayName = "Stack Overflow"
    override val authStrategy = AuthStrategy.PUBLIC
    override val commands = listOf(
        AdapterCommand(
            name = "hot",
            description = "Get hot questions",
            args = listOf(
                CommandArg("limit", ArgType.INT, default = 10, description = "Number of questions to return")
            )
        ),
        AdapterCommand(
            name = "search",
            description = "Search questions by title",
            args = listOf(
                CommandArg("query", ArgType.STRING, required = true, description = "Search query"),
                CommandArg("limit", ArgType.INT, default = 10, description = "Number of results")
            )
        )
    )

    override suspend fun execute(command: String, args: Map<String, Any>): AdapterResult {
        return when (command) {
            "hot" -> fetchHotQuestions(args)
            "search" -> searchQuestions(args)
            else -> AdapterResult(success = false, error = "Unknown command: $command")
        }
    }

    private suspend fun fetchHotQuestions(args: Map<String, Any>): AdapterResult {
        val limit = (args["limit"] as? Int) ?: 10
        return withContext(Dispatchers.IO) {
            try {
                val url = "$API_BASE/questions".toHttpUrl().newBuilder()
                    .addQueryParameter("order", "desc")
                    .addQueryParameter("sort", "hot")
                    .addQueryParameter("site", "stackoverflow")
                    .addQueryParameter("pagesize", limit.toString())
                    .addQueryParameter("filter", "!9Z(-wzftf")
                    .build()
                val body = get(url.toString())
                parseQuestionsResponse(body)
            } catch (e: Exception) {
                Log.e(TAG, "Hot questions fetch failed", e)
                AdapterResult(success = false, error = "Failed to fetch questions: ${e.message}")
            }
        }
    }

    private suspend fun searchQuestions(args: Map<String, Any>): AdapterResult {
        val query = args["query"] as? String
            ?: return AdapterResult(success = false, error = "Missing 'query' argument")
        val limit = (args["limit"] as? Int) ?: 10
        return withContext(Dispatchers.IO) {
            try {
                val url = "$API_BASE/search".toHttpUrl().newBuilder()
                    .addQueryParameter("intitle", query)
                    .addQueryParameter("site", "stackoverflow")
                    .addQueryParameter("pagesize", limit.toString())
                    .addQueryParameter("order", "desc")
                    .addQueryParameter("sort", "relevance")
                    .addQueryParameter("filter", "!9Z(-wzftf")
                    .build()
                val body = get(url.toString())
                parseQuestionsResponse(body)
            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                AdapterResult(success = false, error = "Search failed: ${e.message}")
            }
        }
    }

    private fun parseQuestionsResponse(body: String): AdapterResult {
        val root = Json.parseToJsonElement(body).jsonObject
        val items = root["items"]?.jsonArray
            ?: return AdapterResult(success = true, data = emptyList())

        val results = items.map { item ->
            val obj = item.jsonObject
            buildMap<String, Any> {
                put("title", obj["title"]?.jsonPrimitive?.content ?: "")
                put("link", obj["link"]?.jsonPrimitive?.content ?: "")
                put("score", obj["score"]?.jsonPrimitive?.intOrNull ?: 0)
                put("answer_count", obj["answer_count"]?.jsonPrimitive?.intOrNull ?: 0)
                put("tags", obj["tags"]?.jsonArray?.joinToString(", ") {
                    it.jsonPrimitive.content
                } ?: "")
            }
        }
        return AdapterResult(success = true, data = results)
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "claw-android/1.0")
            .header("Accept-Encoding", "gzip")
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        response.close()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        return body
    }
}
