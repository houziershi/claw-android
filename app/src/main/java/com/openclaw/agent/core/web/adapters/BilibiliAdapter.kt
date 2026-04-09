package com.openclaw.agent.core.web.adapters

import android.util.Log
import com.openclaw.agent.core.web.*
import com.openclaw.agent.core.web.cookie.CookieVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "BilibiliAdapter"
private const val API_BASE = "https://api.bilibili.com"

class BilibiliAdapter(
    private val client: OkHttpClient,
    private val cookieVault: CookieVault
) : WebAdapter {

    override val site = "bilibili"
    override val displayName = "哔哩哔哩"
    override val authStrategy = AuthStrategy.COOKIE

    override val commands = listOf(
        AdapterCommand(
            "hot", "B站热门视频",
            listOf(CommandArg("limit", ArgType.INT, default = 20, description = "Number of videos"))
        ),
        AdapterCommand(
            "search", "搜索B站视频",
            listOf(
                CommandArg("query", ArgType.STRING, required = true, description = "Search keyword"),
                CommandArg("limit", ArgType.INT, default = 20, description = "Number of results")
            )
        ),
        AdapterCommand(
            "ranking", "B站排行榜",
            listOf(CommandArg("limit", ArgType.INT, default = 20, description = "Number of videos"))
        )
    )

    override suspend fun execute(command: String, args: Map<String, Any>): AdapterResult {
        return when (command) {
            "hot" -> fetchHot(args)
            "search" -> search(args)
            "ranking" -> fetchRanking(args)
            else -> AdapterResult(success = false, error = "Unknown command: $command")
        }
    }

    private suspend fun fetchHot(args: Map<String, Any>): AdapterResult {
        val limit = (args["limit"] as? Int) ?: 20
        return withContext(Dispatchers.IO) {
            try {
                val body = get("$API_BASE/x/web-interface/popular?ps=$limit&pn=1")
                val root = Json.parseToJsonElement(body).jsonObject
                val list = root["data"]?.jsonObject?.get("list")?.jsonArray
                    ?: return@withContext AdapterResult(success = true, data = emptyList())

                val data = list.take(limit).mapIndexed { i, item ->
                    val obj = item.jsonObject
                    buildMap<String, Any> {
                        put("rank", i + 1)
                        put("title", obj["title"]?.jsonPrimitive?.content ?: "")
                        put("author", obj["owner"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "")
                        put("play", obj["stat"]?.jsonObject?.get("view")?.jsonPrimitive?.intOrNull ?: 0)
                        put("danmaku", obj["stat"]?.jsonObject?.get("danmaku")?.jsonPrimitive?.intOrNull ?: 0)
                        put("url", "https://www.bilibili.com/video/${obj["bvid"]?.jsonPrimitive?.content ?: ""}")
                    }
                }
                AdapterResult(success = true, data = data)
            } catch (e: Exception) {
                Log.e(TAG, "Hot fetch failed", e)
                AdapterResult(success = false, error = "Failed: ${e.message}")
            }
        }
    }

    private suspend fun search(args: Map<String, Any>): AdapterResult {
        val query = args["query"] as? String
            ?: return AdapterResult(success = false, error = "Missing 'query' argument")
        val limit = (args["limit"] as? Int) ?: 20

        if (!cookieVault.isLoggedIn("bilibili")) {
            return AdapterResult(success = false, error = "请先登录哔哩哔哩。在设置 → 站点账号中登录。")
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = "$API_BASE/x/web-interface/search/type?search_type=video&keyword=${java.net.URLEncoder.encode(query, "UTF-8")}&page=1"
                val body = get(url)
                val root = Json.parseToJsonElement(body).jsonObject
                val results = root["data"]?.jsonObject?.get("result")?.jsonArray
                    ?: return@withContext AdapterResult(success = true, data = emptyList())

                val data = results.take(limit).mapIndexed { i, item ->
                    val obj = item.jsonObject
                    buildMap<String, Any> {
                        put("rank", i + 1)
                        put("title", (obj["title"]?.jsonPrimitive?.content ?: "")
                            .replace(Regex("<[^>]*>"), ""))
                        put("author", obj["author"]?.jsonPrimitive?.content ?: "")
                        put("play", obj["play"]?.jsonPrimitive?.intOrNull ?: 0)
                        put("url", "https://www.bilibili.com/video/${obj["bvid"]?.jsonPrimitive?.content ?: ""}")
                    }
                }
                AdapterResult(success = true, data = data)
            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                AdapterResult(success = false, error = "Search failed: ${e.message}")
            }
        }
    }

    private suspend fun fetchRanking(args: Map<String, Any>): AdapterResult {
        val limit = (args["limit"] as? Int) ?: 20
        return withContext(Dispatchers.IO) {
            try {
                val body = get("$API_BASE/x/web-interface/ranking/v2?rid=0&type=all")
                val root = Json.parseToJsonElement(body).jsonObject
                val list = root["data"]?.jsonObject?.get("list")?.jsonArray
                    ?: return@withContext AdapterResult(success = true, data = emptyList())

                val data = list.take(limit).mapIndexed { i, item ->
                    val obj = item.jsonObject
                    buildMap<String, Any> {
                        put("rank", i + 1)
                        put("title", obj["title"]?.jsonPrimitive?.content ?: "")
                        put("author", obj["owner"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "")
                        put("score", obj["stat"]?.jsonObject?.get("view")?.jsonPrimitive?.intOrNull ?: 0)
                        put("url", "https://www.bilibili.com/video/${obj["bvid"]?.jsonPrimitive?.content ?: ""}")
                    }
                }
                AdapterResult(success = true, data = data)
            } catch (e: Exception) {
                Log.e(TAG, "Ranking failed", e)
                AdapterResult(success = false, error = "Ranking failed: ${e.message}")
            }
        }
    }

    private fun get(url: String): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .header("Referer", "https://www.bilibili.com")
        cookieVault.getCookies("bilibili")?.let { builder.header("Cookie", it) }

        val response = client.newCall(builder.build()).execute()
        val body = response.body?.string() ?: ""
        response.close()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        // Detect expired login: Bilibili returns code=-101 when session expired
        if (body.contains("\"code\":-101")) {
            throw Exception("哔哩哔哩登录已过期，请在设置 → 站点账号中重新登录")
        }
        return body
    }
}
