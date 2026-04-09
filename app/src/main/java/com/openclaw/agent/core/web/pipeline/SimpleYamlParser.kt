package com.openclaw.agent.core.web.pipeline

import android.util.Log

private const val TAG = "SimpleYamlParser"

/**
 * Lightweight YAML parser for OpenCLI-style pipeline definitions.
 * Uses a recursive line-based approach. NOT a full YAML parser.
 */
class SimpleYamlParser {

    fun parse(yaml: String): YamlPipelineDef? {
        return try {
            val root = parseToMap(yaml)
            
            val site = root["site"] as? String ?: return null
            val name = root["name"] as? String ?: return null
            val description = root["description"] as? String ?: ""
            val strategy = root["strategy"] as? String ?: "public"
            val browser = root["browser"]?.toString()?.trim() == "true"

            @Suppress("UNCHECKED_CAST")
            val argsRaw = root["args"] as? Map<String, Any> ?: emptyMap()
            val args = argsRaw.mapValues { (_, v) ->
                if (v is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    val m = v as Map<String, Any>
                    ArgDef(
                        type = m["type"]?.toString() ?: "string",
                        default = m["default"],
                        required = m["required"]?.toString()?.trim() == "true",
                        description = m["description"]?.toString() ?: ""
                    )
                } else ArgDef()
            }

            @Suppress("UNCHECKED_CAST")
            val pipelineRaw = root["pipeline"] as? List<Any> ?: emptyList()
            val pipeline = pipelineRaw.mapNotNull { parsePipelineStep(it) }

            val columns = parseColumns(root["columns"])

            YamlPipelineDef(site, name, description, strategy, browser, args, pipeline, columns)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse YAML", e)
            null
        }
    }

    /**
     * Parse YAML text into a nested Map/List/String structure.
     */
    fun parseToMap(yaml: String): Map<String, Any> {
        val lines = yaml.lines()
        val (result, _) = parseMap(lines, 0, 0)
        return result
    }

    private fun parseMap(lines: List<String>, start: Int, minIndent: Int): Pair<Map<String, Any>, Int> {
        val map = mutableMapOf<String, Any>()
        var i = start
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith("#")) { i++; continue }

            val indent = line.indexOfFirst { it != ' ' }
            if (indent < minIndent) break

            val stripped = line.trim()

            // List items should not appear here; break to parent
            if (stripped.startsWith("- ")) break

            val colonIdx = stripped.indexOf(':')
            if (colonIdx < 0) { i++; continue }

            val key = stripped.substring(0, colonIdx).trim()
            val valueStr = stripped.substring(colonIdx + 1).trim()

            when {
                valueStr.startsWith("|") -> {
                    // Block scalar
                    val (block, nextI) = parseBlockScalar(lines, i + 1, indent + 2)
                    map[key] = block
                    i = nextI
                }
                valueStr.startsWith("[") -> {
                    // Inline list
                    map[key] = valueStr.trim('[', ']')
                        .split(",").map { it.trim().trim('"', '\'') }
                    i++
                }
                valueStr.isEmpty() -> {
                    // Value is a child block (map or list)
                    val nextI = nextNonBlank(lines, i + 1)
                    if (nextI >= lines.size) { map[key] = ""; i++; continue }
                    val nextLine = lines[nextI]
                    val childIndent = nextLine.indexOfFirst { it != ' ' }
                    if (childIndent <= indent) { map[key] = ""; i++; continue }

                    if (nextLine.trimStart().startsWith("- ")) {
                        val (list, afterI) = parseList(lines, nextI, childIndent)
                        map[key] = list
                        i = afterI
                    } else {
                        val (child, afterI) = parseMap(lines, nextI, childIndent)
                        map[key] = child
                        i = afterI
                    }
                }
                else -> {
                    // Simple value
                    map[key] = valueStr.trim('"', '\'')
                    i++
                }
            }
        }
        return Pair(map, i)
    }

    private fun parseList(lines: List<String>, start: Int, minIndent: Int): Pair<List<Any>, Int> {
        val list = mutableListOf<Any>()
        var i = start
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith("#")) { i++; continue }

            val indent = line.indexOfFirst { it != ' ' }
            if (indent < minIndent) break

            val stripped = line.trimStart()
            if (!stripped.startsWith("- ")) break

            val content = stripped.removePrefix("- ").trim()
            
            if (content.isEmpty()) {
                // Child is a map on next lines
                val nextI = nextNonBlank(lines, i + 1)
                if (nextI < lines.size) {
                    val childIndent = lines[nextI].indexOfFirst { it != ' ' }
                    val (child, afterI) = parseMap(lines, nextI, childIndent)
                    list.add(child)
                    i = afterI
                } else {
                    i++
                }
            } else if (content.contains(":")) {
                // Inline map on this line, possibly with children on next lines
                // E.g. "- fetch:" or "- map:" or "- select: items"
                val colonIdx = content.indexOf(':')
                val itemKey = content.substring(0, colonIdx).trim()
                val itemVal = content.substring(colonIdx + 1).trim()

                if (itemVal.isEmpty()) {
                    // Children follow
                    val nextI = nextNonBlank(lines, i + 1)
                    if (nextI < lines.size) {
                        val childIndent = lines[nextI].indexOfFirst { it != ' ' }
                        if (childIndent > indent) {
                            val (child, afterI) = parseMap(lines, nextI, childIndent)
                            list.add(mapOf(itemKey to child))
                            i = afterI
                        } else {
                            list.add(mapOf(itemKey to ""))
                            i++
                        }
                    } else {
                        list.add(mapOf(itemKey to ""))
                        i++
                    }
                } else {
                    list.add(mapOf(itemKey to itemVal.trim('"', '\'')))
                    i++
                }
            } else {
                // Simple scalar
                list.add(content.trim('"', '\''))
                i++
            }
        }
        return Pair(list, i)
    }

    private fun parseBlockScalar(lines: List<String>, start: Int, minIndent: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var i = start
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) { sb.append("\n"); i++; continue }
            val indent = line.indexOfFirst { it != ' ' }
            if (indent < minIndent) break
            sb.append(line.substring(minIndent.coerceAtMost(line.length))).append("\n")
            i++
        }
        return Pair(sb.toString().trimEnd(), i)
    }

    private fun nextNonBlank(lines: List<String>, from: Int): Int {
        var i = from
        while (i < lines.size && (lines[i].isBlank() || lines[i].trimStart().startsWith("#"))) i++
        return i
    }

    // ---------------------------------------------------------------------------
    // Pipeline step parsing
    // ---------------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun parsePipelineStep(item: Any): PipelineStep? {
        if (item !is Map<*, *>) return null
        val m = item as Map<String, Any>
        return when {
            m.containsKey("fetch") -> {
                when (val fetch = m["fetch"]) {
                    is Map<*, *> -> {
                        val fm = fetch as Map<String, Any>
                        PipelineStep.Fetch(
                            url = fm["url"]?.toString() ?: return null,
                            headers = (fm["headers"] as? Map<String, Any>)?.mapValues { it.value.toString() } ?: emptyMap(),
                            params = (fm["params"] as? Map<String, Any>)?.mapValues { it.value.toString() } ?: emptyMap()
                        )
                    }
                    is String -> PipelineStep.Fetch(fetch)
                    else -> null
                }
            }
            m.containsKey("map") -> {
                val mapDef = m["map"]
                if (mapDef is Map<*, *>) {
                    PipelineStep.MapStep((mapDef as Map<String, Any>).mapValues { it.value.toString() })
                } else null
            }
            m.containsKey("limit") -> PipelineStep.Limit(m["limit"].toString())
            m.containsKey("filter") -> PipelineStep.Filter(m["filter"].toString())
            m.containsKey("select") -> PipelineStep.Select(m["select"].toString().trim())
            else -> null
        }
    }

    private fun parseColumns(raw: Any?): List<String> = when (raw) {
        is List<*> -> raw.filterIsInstance<String>()
        is String -> raw.trim('[', ']').split(",").map { it.trim() }
        else -> emptyList()
    }
}
