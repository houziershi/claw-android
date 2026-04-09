package com.openclaw.agent.core.web.adapters

import android.util.Log
import com.openclaw.agent.core.web.*
import com.openclaw.agent.core.web.cookie.CookieVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "WeiboAdapter"

class WeiboAdapter(
    private val client: OkHttpClient,
    private val cookieVault: CookieVault
) : WebAdapter {

    override val site = "weibo"
    override val displayName = "微博"
    override val authStrategy = AuthStrategy.COOKIE

    override val commands = listOf(
        AdapterCommand(
            "hot", "微博热搜",
            listOf(CommandArg("limit", ArgType.INT, default = 20, description = "Number of topics"))
        ),
        AdapterCommand(
            "search", "搜索微博",
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
                val body = get("https://weibo.com/ajax/side/hotSearch")
                val root = Json.parseToJsonElement(body).jsonObject
                val list = root["data"]?.jsonObject?.get("realtime")?.jsonArray
                    ?: return@withContext AdapterResult(success = true, data = emptyList())

                val data = list.take(limit).mapIndexed { i, item ->
                    val obj = item.jsonObject
                    buildMap<String, Any> {
                        put("rank", i + 1)
                        put("keyword", obj["word"]?.jsonPrimitive?.content ?: "")
                        put("heat", obj["num"]?.jsonPrimitive?.intOrNull ?: 0)
                        put("category", obj["category"]?.jsonPrimitive?.content ?: "")
                        val word = java.net.URLEncoder.encode(
                            obj["word"]?.jsonPrimitive?.content ?: "", "UTF-8"
                        )
                        put("url", "https://s.weibo.com/weibo?q=$word")
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

        if (!cookieVault.isLoggedIn("weibo")) {
            return AdapterResult(success = false, error = "请先登录微博。在设置 → 站点账号中登录。")
        }

        return withContext(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val body = get("https://m.weibo.cn/api/container/getIndex?containerid=100103type%3D1%26q%3D$encoded&page_type=searchall")
                val root = Json.parseToJsonElement(body).jsonObject
                val cards = root["data"]?.jsonObject?.get("cards")?.jsonArray
                    ?: return@withContext AdapterResult(success = true, data = emptyList())

                val data = mutableListOf<Map<String, Any>>()
                for (card in cards) {
                    val mblog = card.jsonObject["mblog"]?.jsonObject ?: continue
                    data.add(buildMap {
                        put("rank", data.size + 1)
                        put("author", mblog["user"]?.jsonObject?.get("screen_name")?.jsonPrimitive?.content ?: "")
                        put("text", (mblog["text"]?.jsonPrimitive?.content ?: "")
                            .replace(Regex("<[^>]*>"), "").take(120))
                        put("reposts", mblog["reposts_count"]?.jsonPrimitive?.intOrNull ?: 0)
                        put("comments", mblog["comments_count"]?.jsonPrimitive?.intOrNull ?: 0)
                        put("likes", mblog["attitudes_count"]?.jsonPrimitive?.intOrNull ?: 0)
                    })
                    if (data.size >= limit) break
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
            .header("Referer", "https://weibo.com")
        cookieVault.getCookies("weibo")?.let { builder.header("Cookie", it) }

        val response = client.newCall(builder.build()).execute()
        val body = response.body?.string() ?: ""
        response.close()
        if (!response.isSuccessful) {
            if (response.code == 401 || response.code == 403) {
                throw Exception("微博登录已过期，请在设置 → 站点账号中重新登录")
            }
            throw Exception("HTTP ${response.code}")
        }
        return body
    }
}
