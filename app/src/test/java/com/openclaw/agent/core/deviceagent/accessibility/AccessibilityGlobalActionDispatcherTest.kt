package com.openclaw.agent.core.deviceagent.accessibility

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccessibilityGlobalActionDispatcherTest {

    @Test
    fun dispatch_mapsBackToPlatformConstant() {
        val recorder = RecordingGlobalActionPerformer()
        val dispatcher = AccessibilityGlobalActionDispatcher(recorder)

        val result = dispatcher.dispatch(AccessibilityGlobalAction.BACK)

        assertThat(result).isTrue()
        assertThat(recorder.performed).containsExactly(AccessibilityGlobalActionDispatcher.GLOBAL_ACTION_BACK)
    }

    @Test
    fun dispatch_mapsHomeToPlatformConstant() {
        val recorder = RecordingGlobalActionPerformer()
        val dispatcher = AccessibilityGlobalActionDispatcher(recorder)

        dispatcher.dispatch(AccessibilityGlobalAction.HOME)

        assertThat(recorder.performed).containsExactly(AccessibilityGlobalActionDispatcher.GLOBAL_ACTION_HOME)
    }

    @Test
    fun dispatch_returnsFalseWhenPlatformRejectsAction() {
        val recorder = RecordingGlobalActionPerformer(shouldSucceed = false)
        val dispatcher = AccessibilityGlobalActionDispatcher(recorder)

        val result = dispatcher.dispatch(AccessibilityGlobalAction.RECENTS)

        assertThat(result).isFalse()
        assertThat(recorder.performed).containsExactly(AccessibilityGlobalActionDispatcher.GLOBAL_ACTION_RECENTS)
    }
}

private class RecordingGlobalActionPerformer(
    private val shouldSucceed: Boolean = true
) : GlobalActionPerformer {
    val performed = mutableListOf<Int>()

    override fun performGlobalAction(action: Int): Boolean {
        performed += action
        return shouldSucceed
    }
}
