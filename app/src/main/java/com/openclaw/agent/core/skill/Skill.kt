package com.openclaw.agent.core.skill

/**
 * Represents a parsed SKILL.md definition.
 * Skills provide specialized system prompts and tool guidance for specific tasks.
 */
data class Skill(
    val name: String,
    val description: String,
    val triggers: List<String>,
    val systemPrompt: String,
    val requiredTools: List<String>,
    val enabled: Boolean = true
)
