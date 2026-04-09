package com.openclaw.agent.core.web.adapters

import android.util.Log
import com.openclaw.agent.core.web.*
import com.openclaw.agent.core.web.cookie.CookieVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "ZhihuAdapter"
private const val API_BASE = "https://www.zhihu.com/api/v3"

class ZhihuAdapter(
    private val client: OkHttpClient,
    private val cookieVault: CookieVault
) : WebAdapter {

    override val site = "zhihu"
    override val displayName = "知乎"
    override val authStrategy = AuthStrategy.COOKIE

    override val commands = listOf(
        AdapterCommand(
            "hot", "知乎热榜",
            listOf(CommandArg("limit", ArgType.INT, default = 20, description = "Number of topics"))
        ),
        AdapterCommand(
            "search", "搜索知乎内容",
            listOf(
                CommandArg("query", ArgType.STRING, required = true, description = "Search keyword"),
                CommandArg("limit", ArgType.INT, default = 10, description = "Number of results")
            )
        )
    )

    override suspend fun execute(command: String, args: Map<String, Any>): AdapterResult {
        return when (command) {
            "hot" -> fetchHot(args)
            "search" -> search(args)
            else -> AdapterResult(success = false, error = "Unknown command: $command")
        }
    }

    private suspend fun fetchHot(args: Map<String, Any>): AdapterResult {
        val limit = (args["limit"] as? Int) ?: 20
        return withContext(Dispatchers.IO) {
            try {
                val body = get("https://www.zhihu.com/api/v3/feed/topstory/hot-lists/total?limit=$limit")
                val root = Json.parseToJsonElement(body).jsonObject
                val list = root["data"]?.jsonArray
                    ?: return@withContext AdapterResult(success = true, data = emptyList())

                val data = list.take(limit).mapIndexed { i, item ->
                    val obj = item.jsonObject
                    val target = obj["target"]?.jsonObject
                    buildMap<String, Any> {
                        put("rank", i + 1)
                        put("title", target?.get("title")?.jsonPrimitive?.content ?: "")
                        put("excerpt", target?.get("excerpt")?.jsonPrimitive?.content ?: "")
                        put("heat", obj["detail_text"]?.jsonPrimitive?.content ?: "")
                        val id = target?.get("id")?.jsonPrimitive?.content ?: ""
                        put("url", "https://www.zhihu.com/question/$id")
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
        val limit = (args["limit"] as? Int) ?: 10

        if (!cookieVault.isLoggedIn("zhihu")) {
            return AdapterResult(success = false, error = "请先登录知乎。在设置 → 站点账号中登录。")
        }

        return withContext(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val body = get("https://www.zhihu.com/api/v4/search_v3?t=general&q=$encoded&correction=1&offset=0&limit=$limit")
                val root = Json.parseToJsonElement(body).jsonObject
                val items = root["data"]?.jsonArray
                    ?: return@withContext AdapterResult(success = true, data = emptyList())

                val data = items.take(limit).mapIndexedNotNull { i, item ->
                    val obj = item.jsonObject["object"]?.jsonObject ?: return@mapIndexedNotNull null
                    val type = obj["type"]?.jsonPrimitive?.content ?: ""
                    buildMap<String, Any> {
                        put("rank", i + 1)
                        put("type", type)
                        put("title", (obj["title"]?.jsonPrimitive?.content ?: "")
                            .replace(Regex("<[^>]*>"), ""))
                        put("excerpt", (obj["excerpt"]?.jsonPrimitive?.content ?: "")
                            .replace(Regex("<[^>]*>"), "").take(100))
                        put("url", obj["url"]?.jsonPrimitive?.content ?: "")
                    }
                }
                AdapterResult(success = true, data = data)
            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                AdapterResult(success = false, error = "Search failed: ${e.message}")
            }
        }
    }

    private fun get(url: String): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .header("Referer", "https://www.zhihu.com")
        cookieVault.getCookies("zhihu")?.let { builder.header("Cookie", it) }

        val response = client.newCall(builder.build()).execute()
        val body = response.body?.string() ?: ""
        response.close()
        if (!response.isSuccessful) {
            if (response.code == 401 || response.code == 403) {
                throw Exception("知乎登录已过期，请在设置 → 站点账号中重新登录")
            }
            throw Exception("HTTP ${response.code}")
        }
        return body
    }
}
