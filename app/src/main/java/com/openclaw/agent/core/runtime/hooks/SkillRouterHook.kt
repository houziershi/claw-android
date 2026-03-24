package com.openclaw.agent.core.runtime.hooks

import android.util.Log
import com.openclaw.agent.core.skill.SkillEngine
import com.openclaw.agent.core.tools.ToolRegistry

private const val TAG = "SkillRouterHook"

// Base tools always included regardless of skill (core functionality)
private val BASE_TOOLS = setOf(
    "get_current_time",
    "get_device_info",
    "memory_read",
    "memory_write",
    "memory_search"
)

/**
 * Intercepts UserPromptSubmit to trim the available tool list based on
 * the matched Skill's requiredTools declaration.
 *
 * This reduces the tool list sent to the LLM — fewer tools = less confusion,
 * lower token cost, and more focused behavior.
 *
 * Example: "设个闹钟" matches the Daily Planner skill → only alarm + get_current_time
 * are sent, not all 12 tools.
 *
 * Attached as a UserPromptSubmit hook (priority=0) in AgentRuntime.
 */
class SkillRouterHook(
    private val skillEngine: SkillEngine,
    private val toolRegistry: ToolRegistry
) : HookHandler<HookEvent.UserPromptSubmit> {

    override suspend fun handle(event: HookEvent.UserPromptSubmit): HookDecision {
        val skill = skillEngine.matchSkill(event.prompt)
            ?: return HookDecision.Allow.also {
                Log.d(TAG, "No skill matched for prompt, using all tools")
            }

        val required = skill.requiredTools
        if (required.isEmpty()) {
            Log.d(TAG, "Skill '${skill.name}' matched but has no requiredTools, using all tools")
            return HookDecision.Allow
        }

        // Union of skill-required tools + always-included base tools
        val filteredNames = (required + BASE_TOOLS).toSet()

        // Only include tools that actually exist in the registry
        val allRegisteredNames = toolRegistry.toToolDefinitions().map { it.name }.toSet()
        val validNames = filteredNames.intersect(allRegisteredNames).toList().sorted()

        if (validNames.isEmpty()) {
            Log.w(TAG, "Skill '${skill.name}' requiredTools had no valid registry matches, using all tools")
            return HookDecision.Allow
        }

        Log.d(TAG, "SkillRouter: matched skill '${skill.name}', filtering to ${validNames.size} tools: $validNames")
        return HookDecision.FilterTools(toolNames = validNames, skillName = skill.name)
    }
}
