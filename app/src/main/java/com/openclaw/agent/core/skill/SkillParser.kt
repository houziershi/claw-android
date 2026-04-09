package com.openclaw.agent.core.skill

import android.util.Log

private const val TAG = "SkillParser"

/**
 * Parses SKILL.md files into Skill objects.
 *
 * Expected format:
 * ```
 * # Skill Name
 *
 * Brief description on the first non-empty line after the title.
 *
 * ## Triggers
 * - keyword1
 * - keyword2
 *
 * ## System Prompt
 * Multi-line prompt text...
 *
 * ## Required Tools
 * - tool_name1
 * - tool_name2
 *
 * ## Description
 * Detailed description text.
 * ```
 */
object SkillParser {

    fun parse(content: String, fileName: String = "unknown"): Skill? {
        try {
            val frontmatter = parseFrontmatter(content)
            val markdownContent = stripFrontmatter(content)
            val lines = markdownContent.lines()
            if (lines.isEmpty()) return null

            var name = frontmatter["name"]?.firstOrNull()?.trim().orEmpty()
            var description = frontmatter["description"]?.firstOrNull()?.trim().orEmpty()
            val triggers = frontmatter["triggers"]
                ?.map { it.trim().lowercase() }
                ?.toMutableList()
                ?: mutableListOf()
            var systemPrompt = StringBuilder()
            val requiredTools = frontmatter["required_tools"]
                ?.map { it.trim() }
                ?.toMutableList()
                ?: mutableListOf()

            var currentSection = ""

            for (line in lines) {
                val trimmed = line.trim()

                // H1 = skill name
                if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) {
                    if (name.isEmpty()) {
                        name = trimmed.removePrefix("# ").trim()
                    }
                    currentSection = "header"
                    continue
                }

                // H2 = section header
                if (trimmed.startsWith("## ")) {
                    currentSection = trimmed.removePrefix("## ").trim().lowercase()
                    continue
                }

                when (currentSection) {
                    "header" -> {
                        // First non-empty line after title is brief description
                        if (trimmed.isNotEmpty() && description.isEmpty()) {
                            description = trimmed
                        }
                    }
                    "triggers" -> {
                        if (trimmed.startsWith("- ")) {
                            triggers.add(trimmed.removePrefix("- ").trim().lowercase())
                        }
                    }
                    "system prompt" -> {
                        systemPrompt.appendLine(line)
                    }
                    "required tools" -> {
                        if (trimmed.startsWith("- ")) {
                            requiredTools.add(trimmed.removePrefix("- ").trim())
                        }
                    }
                    "description" -> {
                        if (trimmed.isNotEmpty() && description.isEmpty()) {
                            description = trimmed
                        } else if (trimmed.isNotEmpty()) {
                            description = trimmed // Override with Description section
                        }
                    }
                }
            }

            if (name.isEmpty()) {
                Log.w(TAG, "Skill has no name: $fileName")
                return null
            }

            return Skill(
                name = name,
                description = description,
                triggers = triggers,
                systemPrompt = systemPrompt.toString().trim(),
                requiredTools = requiredTools
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse skill: $fileName", e)
            return null
        }
    }

    private fun stripFrontmatter(content: String): String {
        val lines = content.lines()
        if (lines.isEmpty() || lines.first().trim() != "---") return content

        for (index in 1 until lines.size) {
            if (lines[index].trim() == "---") {
                return lines.drop(index + 1).joinToString("\n")
            }
        }

        return content
    }

    private fun parseFrontmatter(content: String): Map<String, List<String>> {
        val lines = content.lines()
        if (lines.isEmpty() || lines.first().trim() != "---") return emptyMap()

        val result = linkedMapOf<String, MutableList<String>>()
        var currentKey: String? = null

        for (index in 1 until lines.size) {
            val rawLine = lines[index]
            val trimmed = rawLine.trim()
            if (trimmed == "---") break
            if (trimmed.isEmpty()) continue

            if (!rawLine.startsWith(" ") && trimmed.contains(":")) {
                val key = trimmed.substringBefore(":").trim().lowercase()
                val value = trimmed.substringAfter(":", "").trim()
                currentKey = key
                if (value.isNotEmpty()) {
                    result.getOrPut(key) { mutableListOf() }.add(value)
                } else {
                    result.getOrPut(key) { mutableListOf() }
                }
                continue
            }

            if (trimmed.startsWith("- ") && currentKey != null) {
                result.getOrPut(currentKey) { mutableListOf() }.add(trimmed.removePrefix("- ").trim())
            }
        }

        return result
    }
}
