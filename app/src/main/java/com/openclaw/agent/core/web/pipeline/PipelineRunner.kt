package com.openclaw.agent.core.web.pipeline

import android.util.Log
import com.openclaw.agent.core.web.AdapterResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "PipelineRunner"

class PipelineRunner(
    private val client: OkHttpClient,
    private val templateEngine: TemplateEngine
) {
    suspend fun execute(
        pipeline: YamlPipelineDef,
        args: Map<String, Any>
    ): AdapterResult = withContext(Dispatchers.IO) {
        try {
            // Start with a list containing a single empty item (for initial fetch)
            var data: List<Map<String, Any?>> = listOf(emptyMap())

            for (step in pipeline.pipeline) {
                data = when (step) {
                    is PipelineStep.Fetch -> executeFetch(step, data, args)
                    is PipelineStep.MapStep -> executeMap(step, data, args)
                    is PipelineStep.Limit -> executeLimit(step, data, args)
                    is PipelineStep.Filter -> executeFilter(step, data, args)
                    is PipelineStep.Select -> executeSelect(step, data)
                }
                Log.d(TAG, "After ${step::class.simpleName}: ${data.size} items")
            }

            @Suppress("UNCHECKED_CAST")
            AdapterResult(success = true, data = data as List<Map<String, Any>>)
        } catch (e: Exception) {
            Log.e(TAG, "Pipeline execution failed", e)
            AdapterResult(success = false, error = "Pipeline error: ${e.message}")
        }
    }

    // ---------------------------------------------------------------------------
    // Fetch step
    // ---------------------------------------------------------------------------

    private suspend fun executeFetch(
        step: PipelineStep.Fetch,
        currentData: List<Map<String, Any?>>,
        args: Map<String, Any>
    ): List<Map<String, Any?>> {
        // If URL contains ${{ item. }}, it means we iterate current data items
        val isPerItem = step.url.contains("\${{ item") || step.url.contains("\${{item")

        return if (isPerItem) {
            // Fetch once per current item (concurrent)
            coroutineScope {
                currentData.mapIndexed { index, item ->
                    async {
                        val context = buildContext(args, item, index)
                        val url = templateEngine.evaluateString(step.url, context)
                        try {
                            val json = httpGet(url, step.headers)
                            parseJsonToItem(json)
                        } catch (e: Exception) {
                            Log.w(TAG, "Per-item fetch failed for $url", e)
                            null
                        }
                    }
                }.mapNotNull { it.await() }
            }
        } else {
            // Single fetch
            val context = buildContext(args, emptyMap(), 0)

            // Build URL with params
            val rawUrl = templateEngine.evaluateString(step.url, context)
            val finalUrl = if (step.params.isEmpty()) {
                rawUrl
            } else {
                val builder = rawUrl.toHttpUrlOrNull()?.newBuilder() ?: return emptyList()
                step.params.forEach { (k, v) ->
                    builder.addQueryParameter(k, templateEngine.evaluateString(v, context))
                }
                builder.build().toString()
            }

            val json = httpGet(finalUrl, step.headers)
            val parsed = parseJsonElement(Json.parseToJsonElement(json))

            // Wrap in list for uniform processing
            when (parsed) {
                is List<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    parsed as List<Map<String, Any?>>
                }
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    listOf(parsed as Map<String, Any?>)
                }
                else -> listOf(mapOf("value" to parsed))
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Map step
    // ---------------------------------------------------------------------------

    private fun executeMap(
        step: PipelineStep.MapStep,
        currentData: List<Map<String, Any?>>,
        args: Map<String, Any>
    ): List<Map<String, Any?>> {
        return currentData.mapIndexed { index, item ->
            val context = buildContext(args, item, index)
            step.fields.mapValues { (_, template) ->
                val value = templateEngine.evaluateString(template, context)
                // Try to preserve numeric types
                value.toIntOrNull() ?: value.toDoubleOrNull() ?: value
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Limit step
    // ---------------------------------------------------------------------------

    private fun executeLimit(
        step: PipelineStep.Limit,
        currentData: List<Map<String, Any?>>,
        args: Map<String, Any>
    ): List<Map<String, Any?>> {
        val context = buildContext(args, emptyMap(), 0)
        val limitVal = templateEngine.evaluate(
            step.expr.replace("\${{", "").replace("}}", "").trim(),
            context
        )
        val limit = when (limitVal) {
            is Number -> limitVal.toInt()
            is String -> limitVal.toIntOrNull() ?: currentData.size
            else -> currentData.size
        }
        return currentData.take(limit)
    }

    // ---------------------------------------------------------------------------
    // Filter step
    // ---------------------------------------------------------------------------

    private fun executeFilter(
        step: PipelineStep.Filter,
        currentData: List<Map<String, Any?>>,
        args: Map<String, Any>
    ): List<Map<String, Any?>> {
        return currentData.filterIndexed { index, item ->
            val context = buildContext(args, item, index)
            templateEngine.evaluateBoolean(step.expr, context)
        }
    }

    // ---------------------------------------------------------------------------
    // Select step
    // ---------------------------------------------------------------------------

    private fun executeSelect(
        step: PipelineStep.Select,
        currentData: List<Map<String, Any?>>
    ): List<Map<String, Any?>> {
        // Get the field from the first (or only) item
        val first = currentData.firstOrNull() ?: return emptyList()
        val selected = first[step.field]
        return when (selected) {
            is List<*> -> {
                @Suppress("UNCHECKED_CAST")
                selected.filterIsInstance<Map<String, Any?>>()
            }
            else -> emptyList()
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun buildContext(
        args: Map<String, Any>,
        item: Map<String, Any?>,
        index: Int
    ): Map<String, Any?> = mapOf(
        "args" to args,
        "item" to item,
        "index" to index
    )

    private fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String {
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "claw-android/1.0")
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        val response = client.newCall(reqBuilder.build()).execute()
        val body = response.body?.string() ?: ""
        response.close()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code} for $url")
        return body
    }

    private fun parseJsonToItem(json: String): Map<String, Any?> {
        return try {
            val element = Json.parseToJsonElement(json)
            when (val parsed = parseJsonElement(element)) {
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    parsed as Map<String, Any?>
                }
                else -> mapOf("value" to parsed)
            }
        } catch (e: Exception) {
            mapOf("_raw" to json)
        }
    }

    /**
     * Recursively converts JsonElement to native Kotlin types.
     */
    fun parseJsonElement(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.boolean
            element.intOrNull != null -> element.int
            element.longOrNull != null -> element.long
            element.doubleOrNull != null -> element.double
            else -> element.content
        }
        is JsonArray -> element.map { parseJsonElement(it) }
        is JsonObject -> element.entries.associate { (k, v) -> k to parseJsonElement(v) }
    }
}
