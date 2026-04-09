package com.openclaw.agent.core.deviceagent.accessibility

import android.accessibilityservice.AccessibilityService

enum class AccessibilityGlobalAction {
    BACK,
    HOME,
    RECENTS
}

fun interface GlobalActionPerformer {
    fun performGlobalAction(action: Int): Boolean
}

class AccessibilityGlobalActionDispatcher(
    private val performer: GlobalActionPerformer
) {
    fun dispatch(action: AccessibilityGlobalAction): Boolean {
        return performer.performGlobalAction(
            when (action) {
                AccessibilityGlobalAction.BACK -> GLOBAL_ACTION_BACK
                AccessibilityGlobalAction.HOME -> GLOBAL_ACTION_HOME
                AccessibilityGlobalAction.RECENTS -> GLOBAL_ACTION_RECENTS
            }
        )
    }

    companion object {
        const val GLOBAL_ACTION_BACK: Int = AccessibilityService.GLOBAL_ACTION_BACK
        const val GLOBAL_ACTION_HOME: Int = AccessibilityService.GLOBAL_ACTION_HOME
        const val GLOBAL_ACTION_RECENTS: Int = AccessibilityService.GLOBAL_ACTION_RECENTS
    }
}
