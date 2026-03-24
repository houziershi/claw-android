package com.openclaw.agent.core.runtime.hooks

import android.util.Log
import kotlin.reflect.KClass

private const val TAG = "HookEngine"

fun interface HookHandler<E : HookEvent> {
    suspend fun handle(event: E): HookDecision
}

data class HookHandlerWrapper(
    val matcher: String?,
    val priority: Int,
    val handler: HookHandler<HookEvent>
)

class HookEngine {
    @PublishedApi
    internal val hooks = mutableMapOf<KClass<out HookEvent>, MutableList<HookHandlerWrapper>>()

    inline fun <reified E : HookEvent> register(
        matcher: String? = null,
        priority: Int = 0,
        handler: HookHandler<E>
    ) {
        val list = hooks.getOrPut(E::class) { mutableListOf() }
        @Suppress("UNCHECKED_CAST")
        list.add(HookHandlerWrapper(matcher, priority, handler as HookHandler<HookEvent>))
        list.sortBy { it.priority }
        logRegistered(E::class.simpleName, matcher, priority)
    }

    @PublishedApi
    internal fun logRegistered(name: String?, matcher: String?, priority: Int) {
        Log.d(TAG, "Registered $name hook (matcher=$matcher, priority=$priority)")
    }

    suspend fun fire(event: HookEvent): HookDecision {
        val handlers = hooks[event::class] ?: return HookDecision.Allow

        for (wrapper in handlers) {
            if (wrapper.matcher != null && !matchEvent(event, wrapper.matcher)) continue

            val decision = wrapper.handler.handle(event)
            if (decision !is HookDecision.Allow) {
                Log.d(TAG, "${event::class.simpleName} → ${decision::class.simpleName} (matcher=${wrapper.matcher})")
                return decision
            }
        }
        return HookDecision.Allow
    }

    private fun matchEvent(event: HookEvent, pattern: String): Boolean {
        val target = when (event) {
            is HookEvent.PreToolUse -> event.toolName
            is HookEvent.PostToolUse -> event.toolName
            is HookEvent.PostToolUseFailure -> event.toolName
            else -> return true
        }
        return try {
            Regex(pattern).matches(target)
        } catch (e: Exception) {
            Log.w(TAG, "Invalid matcher regex: $pattern", e)
            false
        }
    }
}
