package com.openclaw.agent.core.deviceagent.capability

import com.google.common.truth.Truth.assertThat
import com.openclaw.agent.core.deviceagent.model.DeviceAction
import com.openclaw.agent.core.deviceagent.model.UiLocator
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CapabilitySetTest {

    @Test
    fun availableCapabilities_listsOnlyReadyProviders() = runTest {
        val capabilities = CapabilitySet(
            accessibility = FakeAccessibilityCapability(available = true),
            shell = FakeShellCapability(available = false),
            screenshot = FakeScreenshotCapability(available = true),
            appControl = FakeAppControlCapability(available = true)
        )

        val available = capabilities.availableCapabilities()

        assertThat(available).containsExactly(
            CapabilityType.ACCESSIBILITY,
            CapabilityType.SCREENSHOT,
            CapabilityType.APP_CONTROL
        )
    }

    @Test
    fun canExecute_prefersSemanticCapabilitiesBeforeShellFallback() = runTest {
        val tapAction = DeviceAction.Tap(locator = UiLocator(testTag = "settings_save"))
        val capabilities = CapabilitySet(
            accessibility = FakeAccessibilityCapability(available = true),
            shell = FakeShellCapability(available = true)
        )

        val route = capabilities.preferredRouteFor(tapAction)

        assertThat(route).isEqualTo(CapabilityRoute.ACCESSIBILITY)
    }

    @Test
    fun canExecute_usesShellFallbackWhenAccessibilityUnavailable() = runTest {
        val tapAction = DeviceAction.Tap(locator = UiLocator(bounds = com.openclaw.agent.core.deviceagent.model.UiBounds(0, 0, 10, 10)))
        val capabilities = CapabilitySet(
            accessibility = FakeAccessibilityCapability(available = false),
            shell = FakeShellCapability(available = true)
        )

        val route = capabilities.preferredRouteFor(tapAction)

        assertThat(route).isEqualTo(CapabilityRoute.SHELL)
    }

    @Test
    fun canExecute_prefersShellForSystemActions() = runTest {
        val clearAction = DeviceAction.ClearAppData(packageName = "com.openclaw.agent")
        val capabilities = CapabilitySet(
            accessibility = FakeAccessibilityCapability(available = true),
            shell = FakeShellCapability(available = true)
        )

        val route = capabilities.preferredRouteFor(clearAction)

        assertThat(route).isEqualTo(CapabilityRoute.SHELL)
    }

    @Test
    fun canExecute_returnsNoneWhenNoProviderMatches() = runTest {
        val captureAction = DeviceAction.AssertVisible(locator = UiLocator(text = "Settings"))
        val capabilities = CapabilitySet()

        val route = capabilities.preferredRouteFor(captureAction)

        assertThat(route).isEqualTo(CapabilityRoute.NONE)
    }
}

private class FakeAccessibilityCapability(
    private val available: Boolean
) : AccessibilityCapability {
    override suspend fun isAvailable(): Boolean = available
}

private class FakeShellCapability(
    private val available: Boolean
) : ShellCapability {
    override suspend fun isAvailable(): Boolean = available
}

private class FakeScreenshotCapability(
    private val available: Boolean
) : ScreenshotCapability {
    override suspend fun isAvailable(): Boolean = available
}

private class FakeAppControlCapability(
    private val available: Boolean
) : AppControlCapability {
    override suspend fun isAvailable(): Boolean = available
}
