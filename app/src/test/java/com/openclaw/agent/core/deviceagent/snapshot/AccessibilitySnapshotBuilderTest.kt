package com.openclaw.agent.core.deviceagent.snapshot

import com.google.common.truth.Truth.assertThat
import com.openclaw.agent.core.deviceagent.accessibility.AccessibilityNode
import com.openclaw.agent.core.deviceagent.model.UiBounds
import org.junit.Test

class AccessibilitySnapshotBuilderTest {

    @Test
    fun build_createsStableTreeWithNodePathsAndCapabilities() {
        val root = FakeSnapshotNode(
            className = "android.widget.FrameLayout",
            packageName = "com.openclaw.agent",
            bounds = UiBounds(0, 0, 1080, 2400),
            children = listOf(
                FakeSnapshotNode(
                    text = "Settings",
                    contentDescription = "Settings",
                    viewIdResourceName = "com.openclaw.agent:id/settings_button",
                    className = "android.widget.ImageButton",
                    packageName = "com.openclaw.agent",
                    clickable = true,
                    bounds = UiBounds(900, 80, 1040, 220)
                ),
                FakeSnapshotNode(
                    viewIdResourceName = "com.openclaw.agent:id/chat_input",
                    className = "android.widget.EditText",
                    packageName = "com.openclaw.agent",
                    editable = true,
                    bounds = UiBounds(24, 2100, 900, 2220)
                )
            )
        )

        val snapshot = AccessibilitySnapshotBuilder.build(
            root = root,
            packageName = "com.openclaw.agent",
            activityName = "com.openclaw.agent.MainActivity"
        )

        assertThat(snapshot.packageName).isEqualTo("com.openclaw.agent")
        assertThat(snapshot.activityName).isEqualTo("com.openclaw.agent.MainActivity")
        assertThat(snapshot.nodeCount).isEqualTo(3)
        assertThat(snapshot.root.path).isEqualTo("0")
        assertThat(snapshot.root.children[0].path).isEqualTo("0.0")
        assertThat(snapshot.root.children[0].clickable).isTrue()
        assertThat(snapshot.root.children[1].editable).isTrue()
    }

    @Test
    fun build_flattensSearchableSummaryForAiConsumption() {
        val root = FakeSnapshotNode(
            className = "android.widget.FrameLayout",
            packageName = "com.openclaw.agent",
            bounds = UiBounds(0, 0, 1080, 2400),
            children = listOf(
                FakeSnapshotNode(
                    text = "Send",
                    contentDescription = "Send",
                    viewIdResourceName = "com.openclaw.agent:id/send_button",
                    className = "android.widget.ImageButton",
                    packageName = "com.openclaw.agent",
                    clickable = true,
                    bounds = UiBounds(930, 2100, 1050, 2220)
                )
            )
        )

        val snapshot = AccessibilitySnapshotBuilder.build(root, "com.openclaw.agent", null)

        assertThat(snapshot.searchIndex).contains("Send")
        assertThat(snapshot.searchIndex).contains("send_button")
        assertThat(snapshot.searchIndex).contains("android.widget.ImageButton")
    }

    @Test
    fun build_handlesNullRootWithEmptySnapshot() {
        val snapshot = AccessibilitySnapshotBuilder.build(
            root = null,
            packageName = "com.openclaw.agent",
            activityName = null
        )

        assertThat(snapshot.nodeCount).isEqualTo(0)
        assertThat(snapshot.root.children).isEmpty()
        assertThat(snapshot.searchIndex).isEmpty()
    }
}

private class FakeSnapshotNode(
    override val text: String? = null,
    override val contentDescription: String? = null,
    override val viewIdResourceName: String? = null,
    override val className: String? = null,
    override val packageName: String? = null,
    override val clickable: Boolean = false,
    override val editable: Boolean = false,
    override val scrollable: Boolean = false,
    override val bounds: UiBounds? = null,
    children: List<FakeSnapshotNode> = emptyList()
) : AccessibilityNode {
    override var parent: AccessibilityNode? = null
    override val childrenInternal = children.toMutableList<AccessibilityNode>()
    override val children: List<AccessibilityNode>
        get() = childrenInternal

    init {
        childrenInternal.forEach { it.parent = this }
    }

    override fun performAction(action: Int): Boolean = false
    override fun setText(text: String): Boolean = false
}
