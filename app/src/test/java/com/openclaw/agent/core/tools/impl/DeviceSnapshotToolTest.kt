package com.openclaw.agent.core.tools.impl

import com.google.common.truth.Truth.assertThat
import com.openclaw.agent.core.deviceagent.snapshot.DeviceSnapshot
import com.openclaw.agent.core.deviceagent.snapshot.UiNodeSnapshot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

class DeviceSnapshotToolTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `name is device_snapshot`() {
        val tool = DeviceSnapshotTool(
            snapshotSource = FakeSnapshotSource(),
            capabilityStatusSource = FakeCapabilityStatusSource()
        )

        assertThat(tool.name).isEqualTo("device_snapshot")
    }

    @Test
    fun `execute returns capability status and snapshot`() = runTest {
        val tool = DeviceSnapshotTool(
            snapshotSource = FakeSnapshotSource(
                DeviceSnapshot(
                    packageName = "com.openclaw.agent",
                    activityName = "MainActivity",
                    nodeCount = 2,
                    root = UiNodeSnapshot(
                        path = "0",
                        className = "android.widget.FrameLayout",
                        children = listOf(
                            UiNodeSnapshot(
                                path = "0.0",
                                text = "Settings",
                                viewIdResourceName = "com.openclaw.agent:id/settings_button"
                            )
                        )
                    ),
                    searchIndex = "0 | Settings | settings_button"
                )
            ),
            capabilityStatusSource = FakeCapabilityStatusSource()
        )

        val result = tool.execute(buildJsonObject {})
        val payload = json.decodeFromString(DeviceSnapshotPayload.serializer(), result.content)

        assertThat(result.success).isTrue()
        assertThat(payload.capabilities.availableCapabilities).containsExactly("ACCESSIBILITY", "SHELL")
        assertThat(payload.snapshot?.nodeCount).isEqualTo(2)
        assertThat(payload.summary).contains("nodeCount=2")
    }

    @Test
    fun `execute can omit search index and tree children`() = runTest {
        val tool = DeviceSnapshotTool(
            snapshotSource = FakeSnapshotSource(
                DeviceSnapshot(
                    packageName = "com.openclaw.agent",
                    activityName = null,
                    nodeCount = 1,
                    root = UiNodeSnapshot(
                        path = "0",
                        children = listOf(UiNodeSnapshot(path = "0.0", text = "Send"))
                    ),
                    searchIndex = "0 | Send"
                )
            ),
            capabilityStatusSource = FakeCapabilityStatusSource()
        )

        val result = tool.execute(buildJsonObject {
            put("include_search_index", false)
            put("include_tree", false)
        })
        val payload = json.decodeFromString(DeviceSnapshotPayload.serializer(), result.content)

        assertThat(payload.snapshot?.searchIndex).isEmpty()
        assertThat(payload.snapshot?.root?.children).isEmpty()
    }

    @Test
    fun `status source reports connected flag separately`() = runTest {
        val statusSource = CapabilitySetStatusSource(
            capabilitySet = com.openclaw.agent.core.deviceagent.capability.CapabilitySet(
                accessibility = FakeAccessibilityCapability(true),
                shell = FakeShellCapability(false),
                appControl = FakeAppControlCapability(true)
            ),
            accessibilityConnectedProvider = { false }
        )

        val status = statusSource.currentStatus()

        assertThat(status.accessibilityEnabled).isTrue()
        assertThat(status.accessibilityConnected).isFalse()
        assertThat(status.appControlAvailable).isTrue()
        assertThat(status.shellAvailable).isFalse()
    }
}

private class FakeSnapshotSource(
    private val snapshot: DeviceSnapshot? = null
) : DeviceSnapshotSource {
    override suspend fun currentSnapshot(): DeviceSnapshot? = snapshot
}

private class FakeCapabilityStatusSource : DeviceCapabilityStatusSource {
    override suspend fun currentStatus(): DeviceCapabilityStatus = DeviceCapabilityStatus(
        availableCapabilities = listOf("ACCESSIBILITY", "SHELL"),
        accessibilityEnabled = true,
        accessibilityConnected = true,
        shellAvailable = true,
        appControlAvailable = false,
        screenshotAvailable = false
    )
}

private class FakeAccessibilityCapability(
    private val available: Boolean
) : com.openclaw.agent.core.deviceagent.capability.AccessibilityCapability {
    override suspend fun isAvailable(): Boolean = available
}

private class FakeShellCapability(
    private val available: Boolean
) : com.openclaw.agent.core.deviceagent.capability.ShellCapability {
    override suspend fun isAvailable(): Boolean = available
}

private class FakeAppControlCapability(
    private val available: Boolean
) : com.openclaw.agent.core.deviceagent.capability.AppControlCapability {
    override suspend fun isAvailable(): Boolean = available
}
