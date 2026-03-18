package com.openclaw.agent.core.skill

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SkillEngine"
private const val SKILLS_ASSET_DIR = "skills"

/**
 * Manages skill loading, matching, and context injection.
 *
 * Skills are loaded from:
 * 1. Built-in assets (assets/skills/)
 * 2. User-installed skills (files/memory/skills/)
 *
 * Matching uses keyword triggers from SKILL.md.
 */
@Singleton
class SkillEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val skills = mutableListOf<Skill>()

    /**
     * Load all skills from assets and user directory.
     * Called once at app startup.
     */
    fun loadSkills() {
        skills.clear()

        // 1. Load built-in skills from assets
        loadFromAssets()

        // 2. Load user-installed skills from internal storage
        loadFromUserDir()

        Log.d(TAG, "Loaded ${skills.size} skills: ${skills.map { it.name }}")
    }

    private fun loadFromAssets() {
        try {
            val assetManager = context.assets
            val skillDirs = assetManager.list(SKILLS_ASSET_DIR) ?: return

            for (dir in skillDirs) {
                try {
                    val skillPath = "$SKILLS_ASSET_DIR/$dir/SKILL.md"
                    val content = assetManager.open(skillPath).bufferedReader().readText()
                    SkillParser.parse(content, dir)?.let { skill ->
                        skills.add(skill)
                        Log.d(TAG, "Loaded built-in skill: ${skill.name} (${skill.triggers.size} triggers)")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load skill from assets/$dir", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list skill assets", e)
        }
    }

    private fun loadFromUserDir() {
        try {
            val skillsDir = java.io.File(context.filesDir, "memory/skills")
            if (!skillsDir.exists()) return

            skillsDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                val skillFile = java.io.File(dir, "SKILL.md")
                if (skillFile.exists()) {
                    SkillParser.parse(skillFile.readText(), dir.name)?.let { skill ->
                        // User skills override built-in skills with same name
                        skills.removeAll { it.name == skill.name }
                        skills.add(skill)
                        Log.d(TAG, "Loaded user skill: ${skill.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load user skills", e)
        }
    }

    /**
     * Match the best skill for a user message.
     * Returns null if no skill matches.
     */
    fun matchSkill(userMessage: String): Skill? {
        if (skills.isEmpty()) return null

        val messageLower = userMessage.lowercase()
        var bestMatch: Skill? = null
        var bestScore = 0

        for (skill in skills) {
            if (!skill.enabled) continue

            var score = 0
            for (trigger in skill.triggers) {
                if (trigger.contains(".*")) {
                    // Regex trigger
                    try {
                        if (Regex(trigger, RegexOption.IGNORE_CASE).containsMatchIn(messageLower)) {
                            score += 2
                        }
                    } catch (_: Exception) {
                        // Invalid regex, skip
                    }
                } else if (messageLower.contains(trigger)) {
                    score += 1
                }
            }

            if (score > bestScore) {
                bestScore = score
                bestMatch = skill
            }
        }

        if (bestMatch != null) {
            Log.d(TAG, "Matched skill: ${bestMatch.name} (score=$bestScore) for: ${userMessage.take(50)}")
        }

        return bestMatch
    }

    /**
     * Build the skill context to inject into the system prompt.
     */
    fun buildSkillContext(skill: Skill): String = buildString {
        appendLine()
        appendLine("## Active Skill: ${skill.name}")
        appendLine(skill.systemPrompt)
    }

    /**
     * Get all loaded skills.
     */
    fun getAllSkills(): List<Skill> = skills.toList()

    /**
     * Enable or disable a skill by name.
     */
    fun setSkillEnabled(name: String, enabled: Boolean) {
        val index = skills.indexOfFirst { it.name == name }
        if (index >= 0) {
            skills[index] = skills[index].copy(enabled = enabled)
            Log.d(TAG, "Skill '$name' enabled=$enabled")
        }
    }

    /**
     * Reload all skills (e.g., after user installs a new one).
     */
    fun reload() {
        loadSkills()
    }
}
