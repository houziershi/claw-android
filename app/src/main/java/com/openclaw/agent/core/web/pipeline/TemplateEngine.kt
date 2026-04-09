package com.openclaw.agent.core.web.pipeline

import android.util.Log

private const val TAG = "TemplateEngine"

/**
 * Evaluates ${{ ... }} template expressions used in YAML pipeline definitions.
 *
 * Supported patterns:
 *   - ${{ args.limit }}       — argument access
 *   - ${{ item.title }}       — current item field access
 *   - ${{ index }}            — current 0-based index
 *   - ${{ index + 1 }}        — arithmetic: +, -, *
 *   - ${{ item.score > 100 }} — comparison (for filter steps)
 *   - ${{ item.tags | join(', ') }} — pipe filter
 *   - Mixed strings: "https://example.com/${{ item.id }}"
 */
class TemplateEngine {

    /**
     * Evaluate a full template string that may contain multiple ${{ }} expressions.
     * Returns a string with all expressions replaced.
     */
    fun evaluateString(template: String, context: Map<String, Any?>): String {
        if (!template.contains("\${{")) return template

        val sb = StringBuilder()
        var pos = 0
        while (pos < template.length) {
            val start = template.indexOf("\${{", pos)
            if (start < 0) {
                sb.append(template.substring(pos))
                break
            }
            sb.append(template.substring(pos, start))
            val end = template.indexOf("}}", start + 3)
            if (end < 0) {
                sb.append(template.substring(start))
                break
            }
            val expr = template.substring(start + 3, end).trim()
            val value = evaluateExpr(expr, context)
            sb.append(value?.toString() ?: "")
            pos = end + 2
        }
        return sb.toString()
    }

    /**
     * Evaluate a template as a boolean (for filter steps).
     * If the template has no ${{ }}, it's treated as a literal condition.
     */
    fun evaluateBoolean(template: String, context: Map<String, Any?>): Boolean {
        val expr = if (template.contains("\${{")) {
            val inner = template.substringAfter("\${{").substringBefore("}}").trim()
            return evaluateComparison(inner, context)
        } else {
            template.trim()
        }
        return evaluateComparison(expr, context)
    }

    /**
     * Evaluate a single expression (without ${{ }}) and return its value.
     */
    fun evaluate(expr: String, context: Map<String, Any?>): Any? {
        return evaluateExpr(expr.trim(), context)
    }

    // ---------------------------------------------------------------------------
    // Internal evaluation
    // ---------------------------------------------------------------------------

    private fun evaluateExpr(expr: String, context: Map<String, Any?>): Any? {
        val e = expr.trim()

        // Pipe filter: item.tags | join(', ')
        if (e.contains(" | ")) {
            val parts = e.split(" | ", limit = 2)
            val base = evaluateExpr(parts[0].trim(), context)
            return applyPipe(base, parts[1].trim())
        }

        // Comparison operators → return Boolean
        for (op in listOf(">=", "<=", "!=", "==", ">", "<")) {
            val idx = e.indexOf(op)
            if (idx > 0) {
                val left = evaluateExpr(e.substring(0, idx).trim(), context)
                val right = evaluateExpr(e.substring(idx + op.length).trim(), context)
                return compare(left, op, right)
            }
        }

        // Ternary: condition ? a : b  — simplified, not recursive
        if (e.contains("?") && e.contains(":")) {
            val qIdx = e.indexOf('?')
            val cIdx = e.lastIndexOf(':')
            if (qIdx in 1 until cIdx) {
                val cond = evaluateExpr(e.substring(0, qIdx).trim(), context)
                val truthy = isTruthy(cond)
                val trueExpr = e.substring(qIdx + 1, cIdx).trim()
                val falseExpr = e.substring(cIdx + 1).trim()
                return evaluateExpr(if (truthy) trueExpr else falseExpr, context)
            }
        }

        // Arithmetic: left OP right (single operator, left to right)
        for (op in listOf("+", "-", "*")) {
            // Avoid mistaking negative numbers; find op not at start
            val idx = findArithmeticOp(e, op)
            if (idx > 0) {
                val left = evaluateExpr(e.substring(0, idx).trim(), context)
                val right = evaluateExpr(e.substring(idx + 1).trim(), context)
                return arithmetic(left, op, right)
            }
        }

        // Logical AND/OR (simple)
        if (e.contains(" && ")) {
            val parts = e.split(" && ")
            return parts.all { isTruthy(evaluateExpr(it.trim(), context)) }
        }
        if (e.contains(" || ")) {
            val parts = e.split(" || ")
            return parts.any { isTruthy(evaluateExpr(it.trim(), context)) }
        }

        // Logical NOT
        if (e.startsWith("!")) {
            val inner = evaluateExpr(e.substring(1).trim(), context)
            return !isTruthy(inner)
        }

        // Boolean literals
        if (e == "true") return true
        if (e == "false") return false

        // Null literal
        if (e == "null" || e == "undefined") return null

        // Numeric literal
        e.toIntOrNull()?.let { return it }
        e.toDoubleOrNull()?.let { return it }

        // String literal (quoted)
        if ((e.startsWith("'") && e.endsWith("'")) ||
            (e.startsWith("\"") && e.endsWith("\""))
        ) {
            return e.substring(1, e.length - 1)
        }

        // Variable access: args.xxx, item.xxx, index, item
        return resolveVariable(e, context)
    }

    private fun resolveVariable(path: String, context: Map<String, Any?>): Any? {
        val parts = path.split(".")
        var current: Any? = context[parts[0]] ?: return null
        for (i in 1 until parts.size) {
            val key = parts[i]
            current = when (current) {
                is Map<*, *> -> current[key]
                else -> null
            }
        }
        return current
    }

    private fun applyPipe(value: Any?, filter: String): Any? {
        return when {
            filter.startsWith("join(") -> {
                val sep = filter.removePrefix("join(").removeSuffix(")")
                    .trim().trim('"', '\'')
                when (value) {
                    is List<*> -> value.joinToString(sep)
                    is Array<*> -> value.joinToString(sep)
                    else -> value?.toString() ?: ""
                }
            }
            filter == "sanitize" -> value?.toString()
                ?.replace(Regex("[/\\\\:*?\"<>|]"), "_") ?: ""
            else -> value?.toString() ?: ""
        }
    }

    private fun compare(left: Any?, op: String, right: Any?): Boolean {
        val l = toDouble(left)
        val r = toDouble(right)
        return if (l != null && r != null) {
            when (op) {
                ">" -> l > r; ">=" -> l >= r
                "<" -> l < r; "<=" -> l <= r
                "==" -> l == r; "!=" -> l != r
                else -> false
            }
        } else {
            val ls = left?.toString() ?: ""
            val rs = right?.toString() ?: ""
            when (op) {
                "==" -> ls == rs; "!=" -> ls != rs
                else -> false
            }
        }
    }

    private fun evaluateComparison(expr: String, context: Map<String, Any?>): Boolean {
        val result = evaluateExpr(expr, context)
        return isTruthy(result)
    }

    private fun arithmetic(left: Any?, op: String, right: Any?): Any {
        val l = toDouble(left) ?: 0.0
        val r = toDouble(right) ?: 0.0
        val result = when (op) {
            "+" -> {
                // String concat if either is not numeric
                if (left is String || right is String) {
                    return "${left ?: ""}${right ?: ""}"
                }
                l + r
            }
            "-" -> l - r
            "*" -> l * r
            else -> l
        }
        // Return Int if it's a whole number
        return if (result % 1.0 == 0.0) result.toInt() else result
    }

    private fun findArithmeticOp(expr: String, op: String): Int {
        // Find op that is not inside quotes or at position 0
        var inQuote = false
        var quoteChar = ' '
        for (i in expr.indices) {
            val c = expr[i]
            if (!inQuote && (c == '\'' || c == '"')) {
                inQuote = true; quoteChar = c
            } else if (inQuote && c == quoteChar) {
                inQuote = false
            } else if (!inQuote && i > 0 && expr.substring(i).startsWith(op)) {
                // Make sure it's not >= <= != ==
                if (op == "+" || op == "-" || op == "*") {
                    val prev = expr[i - 1]
                    if (prev == '>' || prev == '<' || prev == '!' || prev == '=') continue
                }
                return i
            }
        }
        return -1
    }

    private fun isTruthy(value: Any?): Boolean = when (value) {
        null -> false
        is Boolean -> value
        is Number -> value.toDouble() != 0.0
        is String -> value.isNotEmpty() && value != "null" && value != "undefined" && value != "false"
        is List<*> -> value.isNotEmpty()
        is Map<*, *> -> value.isNotEmpty()
        else -> true
    }

    private fun toDouble(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        is Boolean -> if (value) 1.0 else 0.0
        else -> null
    }
}
