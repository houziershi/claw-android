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
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

private const val TAG = "YahooFinanceAdapter"
private const val CHART_BASE = "https://query1.finance.yahoo.com/v8/finance/chart"

class YahooFinanceAdapter(private val client: OkHttpClient) : WebAdapter {
    override val site = "yahoofinance"
    override val displayName = "Yahoo Finance"
    override val authStrategy = AuthStrategy.PUBLIC
    override val commands = listOf(
        AdapterCommand(
            name = "quote",
            description = "Get current stock quote",
            args = listOf(
                CommandArg("symbol", ArgType.STRING, required = true, description = "Ticker symbol, e.g. AAPL, MSFT, BTC-USD")
            )
        )
    )

    override suspend fun execute(command: String, args: Map<String, Any>): AdapterResult {
        return when (command) {
            "quote" -> fetchQuote(args)
            else -> AdapterResult(success = false, error = "Unknown command: $command")
        }
    }

    private suspend fun fetchQuote(args: Map<String, Any>): AdapterResult {
        val symbol = args["symbol"] as? String
            ?: return AdapterResult(success = false, error = "Missing 'symbol' argument")
        return withContext(Dispatchers.IO) {
            try {
                val url = "$CHART_BASE/${symbol.uppercase()}".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("range", "1d")
                    .addQueryParameter("interval", "1d")
                    .build()
                val body = get(url.toString())
                parseQuoteResponse(body)
            } catch (e: Exception) {
                Log.e(TAG, "Quote fetch failed for $symbol", e)
                AdapterResult(success = false, error = "Failed to fetch quote for $symbol: ${e.message}")
            }
        }
    }

    private fun parseQuoteResponse(body: String): AdapterResult {
        val root = Json.parseToJsonElement(body).jsonObject
        val result = root["chart"]?.jsonObject?.get("result")?.jsonArray?.firstOrNull()?.jsonObject
            ?: return AdapterResult(success = false, error = "No quote data found")

        val meta = result["meta"]?.jsonObject
            ?: return AdapterResult(success = false, error = "No metadata in quote response")

        val price = meta["regularMarketPrice"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val previousClose = meta["previousClose"]?.jsonPrimitive?.doubleOrNull
            ?: meta["chartPreviousClose"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val change = price - previousClose
        val changePercent = if (previousClose != 0.0) (change / previousClose) * 100 else 0.0

        val quoteData = buildMap<String, Any> {
            put("symbol", meta["symbol"]?.jsonPrimitive?.content ?: "")
            put("price", "%.4f".format(price))
            put("change", "%.4f".format(change))
            put("changePercent", "%.2f%%".format(changePercent))
            put("currency", meta["currency"]?.jsonPrimitive?.content ?: "")
            meta["regularMarketDayHigh"]?.jsonPrimitive?.doubleOrNull?.let {
                put("dayHigh", "%.4f".format(it))
            }
            meta["regularMarketDayLow"]?.jsonPrimitive?.doubleOrNull?.let {
                put("dayLow", "%.4f".format(it))
            }
            meta["regularMarketVolume"]?.jsonPrimitive?.doubleOrNull?.let {
                put("volume", it.toLong().toString())
            }
        }
        return AdapterResult(success = true, data = listOf(quoteData))
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36")
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        response.close()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        return body
    }
}
