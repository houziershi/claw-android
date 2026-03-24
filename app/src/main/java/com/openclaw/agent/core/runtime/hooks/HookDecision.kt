package com.openclaw.agent.core.runtime.hooks

sealed class HookDecision {
    /** Pass through unchanged */
    object Allow : HookDecision()

    /** Soft deny: inject an error result so LLM knows the call was blocked */
    data class Deny(val reason: String) : HookDecision()

    /** Hard stop: abort the entire agent turn immediately */
    data class Block(val reason: String) : HookDecision()

    /** Modify the tool input before execution */
    data class ModifyInput(val newInput: String) : HookDecision()

    /** Modify the tool result after execution */
    data class ModifyResult(val newResult: String) : HookDecision()

    /** Inject additional context into the next LLM call */
    data class InjectContext(val context: String) : HookDecision()

    /** Filter available tools to a subset (from SkillRouterHook) */
    data class FilterTools(val toolNames: List<String>, val skillName: String) : HookDecision()
}
