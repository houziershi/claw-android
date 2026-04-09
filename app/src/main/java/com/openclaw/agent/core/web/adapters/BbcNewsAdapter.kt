package com.openclaw.agent.core.web.adapters

import android.util.Log
import android.util.Xml
import com.openclaw.agent.core.web.AdapterCommand
import com.openclaw.agent.core.web.AdapterResult
import com.openclaw.agent.core.web.AuthStrategy
import com.openclaw.agent.core.web.WebAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

private const val TAG = "BbcNewsAdapter"
private const val RSS_URL = "https://feeds.bbci.co.uk/news/rss.xml"

class BbcNewsAdapter(private val client: OkHttpClient) : WebAdapter {
    override val site = "bbc"
    override val displayName = "BBC News"
    override val authStrategy = AuthStrategy.PUBLIC
    override val commands = listOf(
        AdapterCommand(
            name = "news",
            description = "Get latest BBC News headlines"
        )
    )

    override suspend fun execute(command: String, args: Map<String, Any>): AdapterResult {
        return when (command) {
            "news" -> fetchNews()
            else -> AdapterResult(success = false, error = "Unknown command: $command")
        }
    }

    private suspend fun fetchNews(): AdapterResult {
        return withContext(Dispatchers.IO) {
            try {
                val body = get(RSS_URL)
                val results = parseRssXml(body)
                AdapterResult(success = true, data = results)
            } catch (e: Exception) {
                Log.e(TAG, "News fetch failed", e)
                AdapterResult(success = false, error = "Failed to fetch news: ${e.message}")
            }
        }
    }

    private fun parseRssXml(body: String): List<Map<String, Any>> {
        val results = mutableListOf<Map<String, Any>>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(body))

        var eventType = parser.eventType
        var inItem = false
        var currentTitle = ""
        var currentDescription = ""
        var currentLink = ""
        var currentPubDate = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> {
                        inItem = true
                        currentTitle = ""
                        currentDescription = ""
                        currentLink = ""
                        currentPubDate = ""
                    }
                    "title" -> if (inItem) currentTitle = parser.nextText().trim()
                    "description" -> if (inItem) currentDescription = parser.nextText().trim()
                    "link" -> if (inItem) currentLink = parser.nextText().trim()
                    "pubDate" -> if (inItem) currentPubDate = parser.nextText().trim()
                }
                XmlPullParser.END_TAG -> if (parser.name == "item" && inItem) {
                    results.add(
                        mapOf(
                            "title" to currentTitle,
                            "description" to currentDescription,
                            "link" to currentLink,
                            "pubDate" to currentPubDate
                        )
                    )
                    inItem = false
                }
            }
            eventType = parser.next()
        }
        return results
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
