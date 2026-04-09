package com.openclaw.agent.core.web.adapters

import android.util.Log
import android.util.Xml
import com.openclaw.agent.core.web.AdapterCommand
import com.openclaw.agent.core.web.AdapterResult
import com.openclaw.agent.core.web.ArgType
import com.openclaw.agent.core.web.AuthStrategy
import com.openclaw.agent.core.web.CommandArg
import com.openclaw.agent.core.web.WebAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

private const val TAG = "ArxivAdapter"
private const val API_BASE = "https://export.arxiv.org/api/query"

class ArxivAdapter(private val client: OkHttpClient) : WebAdapter {
    override val site = "arxiv"
    override val displayName = "arXiv"
    override val authStrategy = AuthStrategy.PUBLIC
    override val commands = listOf(
        AdapterCommand(
            name = "search",
            description = "Search arXiv preprints",
            args = listOf(
                CommandArg("query", ArgType.STRING, required = true, description = "Search query"),
                CommandArg("limit", ArgType.INT, default = 10, description = "Number of results")
            )
        )
    )

    override suspend fun execute(command: String, args: Map<String, Any>): AdapterResult {
        return when (command) {
            "search" -> searchPapers(args)
            else -> AdapterResult(success = false, error = "Unknown command: $command")
        }
    }

    private suspend fun searchPapers(args: Map<String, Any>): AdapterResult {
        val query = args["query"] as? String
            ?: return AdapterResult(success = false, error = "Missing 'query' argument")
        val limit = (args["limit"] as? Int) ?: 10
        return withContext(Dispatchers.IO) {
            try {
                val url = API_BASE.toHttpUrl().newBuilder()
                    .addQueryParameter("search_query", "all:$query")
                    .addQueryParameter("max_results", limit.toString())
                    .addQueryParameter("sortBy", "relevance")
                    .build()
                val body = get(url.toString())
                val results = parseAtomXml(body)
                AdapterResult(success = true, data = results)
            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                AdapterResult(success = false, error = "Search failed: ${e.message}")
            }
        }
    }

    private fun parseAtomXml(body: String): List<Map<String, Any>> {
        val results = mutableListOf<Map<String, Any>>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(body))

        var eventType = parser.eventType
        var inEntry = false
        var inAuthor = false
        var currentTitle = ""
        var currentId = ""
        var currentAbstract = ""
        var currentPublished = ""
        val currentAuthors = mutableListOf<String>()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "entry" -> {
                        inEntry = true
                        inAuthor = false
                        currentTitle = ""
                        currentId = ""
                        currentAbstract = ""
                        currentPublished = ""
                        currentAuthors.clear()
                    }
                    "author" -> if (inEntry) inAuthor = true
                    "title" -> if (inEntry) currentTitle = parser.nextText().trim()
                    "id" -> if (inEntry) currentId = parser.nextText().trim()
                    "summary" -> if (inEntry) currentAbstract = parser.nextText().trim()
                    "published" -> if (inEntry) currentPublished = parser.nextText().trim()
                    "name" -> if (inEntry && inAuthor) currentAuthors.add(parser.nextText().trim())
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "author" -> inAuthor = false
                    "entry" -> if (inEntry) {
                        results.add(
                            mapOf(
                                "title" to currentTitle,
                                "authors" to currentAuthors.joinToString(", "),
                                "abstract" to currentAbstract,
                                "link" to currentId,
                                "published" to currentPublished
                            )
                        )
                        inEntry = false
                    }
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
