package com.openclaw.agent.core.deviceagent.accessibility

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccessibilityServiceStatusTest {

    @Test
    fun isServiceEnabled_matchesFlattenedComponentName() {
        val enabledServices = listOf(
            "com.android.talkback/com.google.android.accessibility.talkback.TalkBackService",
            "com.openclaw.agent/com.openclaw.agent.core.deviceagent.accessibility.TestAgentAccessibilityService"
        ).joinToString(":")

        val enabled = AccessibilityServiceStatus.isServiceEnabled(
            enabledServices = enabledServices,
            expectedComponent = "com.openclaw.agent/com.openclaw.agent.core.deviceagent.accessibility.TestAgentAccessibilityService"
        )

        assertThat(enabled).isTrue()
    }

    @Test
    fun isServiceEnabled_returnsFalseWhenSettingMissing() {
        val enabled = AccessibilityServiceStatus.isServiceEnabled(
            enabledServices = null,
            expectedComponent = "com.openclaw.agent/com.openclaw.agent.core.deviceagent.accessibility.TestAgentAccessibilityService"
        )

        assertThat(enabled).isFalse()
    }

    @Test
    fun isServiceEnabled_ignoresCaseAndWhitespaceNoise() {
        val enabled = AccessibilityServiceStatus.isServiceEnabled(
            enabledServices = " com.openclaw.agent/com.openclaw.agent.core.deviceagent.accessibility.testagentaccessibilityservice ",
            expectedComponent = "com.openclaw.agent/com.openclaw.agent.core.deviceagent.accessibility.TestAgentAccessibilityService"
        )

        assertThat(enabled).isTrue()
    }
}
