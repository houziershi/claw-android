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
            val lines = content.lines()
            if (lines.isEmpty()) return null

            var name = ""
            var description = ""
            var triggers = mutableListOf<String>()
            var systemPrompt = StringBuilder()
            var requiredTools = mutableListOf<String>()

            var currentSection = ""

            for (line in lines) {
                val trimmed = line.trim()

                // H1 = skill name
                if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) {
                    name = trimmed.removePrefix("# ").trim()
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
}
