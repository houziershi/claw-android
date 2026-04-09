package com.openclaw.agent.core.deviceagent.accessibility

import com.google.common.truth.Truth.assertThat
import com.openclaw.agent.core.deviceagent.model.UiLocator
import org.junit.Test

class AccessibilityNodeFinderTest {

    @Test
    fun findFirst_matchesByText() {
        val tree = fakeTree()

        val match = AccessibilityNodeFinder.findFirst(tree, UiLocator(text = "Settings"))

        assertThat(match?.text).isEqualTo("Settings")
    }

    @Test
    fun findFirst_matchesByContentDescription() {
        val tree = fakeTree()

        val match = AccessibilityNodeFinder.findFirst(tree, UiLocator(contentDescription = "Send"))

        assertThat(match?.contentDescription).isEqualTo("Send")
    }

    @Test
    fun findFirst_matchesByViewIdAndPackage() {
        val tree = fakeTree()

        val match = AccessibilityNodeFinder.findFirst(
            tree,
            UiLocator(
                resourceId = "com.openclaw.agent:id/chat_input",
                packageName = "com.openclaw.agent"
            )
        )

        assertThat(match?.viewIdResourceName).isEqualTo("com.openclaw.agent:id/chat_input")
    }

    @Test
    fun findFirst_requiresAllSpecifiedSelectorsToMatch() {
        val tree = fakeTree()

        val match = AccessibilityNodeFinder.findFirst(
            tree,
            UiLocator(text = "Settings", packageName = "other.package")
        )

        assertThat(match).isNull()
    }

    @Test
    fun clickNearestClickable_usesAncestorWhenLeafNotClickable() {
        val clickableParent = FakeNode(
            text = "Settings",
            clickable = true,
            children = listOf(FakeNode(text = "Advanced", clickable = false))
        )

        val clicked = AccessibilityNodeFinder.clickNearestClickable(
            AccessibilityNodeFinder.findFirst(clickableParent, UiLocator(text = "Advanced"))
        )

        assertThat(clicked).isTrue()
        assertThat(clickableParent.performedActions).containsExactly(FakeNode.ACTION_CLICK)
    }

    @Test
    fun clickNearestClickable_returnsFalseWhenNoClickableNodeExists() {
        val tree = FakeNode(text = "leaf", clickable = false)

        val clicked = AccessibilityNodeFinder.clickNearestClickable(tree)

        assertThat(clicked).isFalse()
    }

    private fun fakeTree(): FakeNode {
        val settings = FakeNode(
            text = "Settings",
            contentDescription = "Settings",
            viewIdResourceName = "com.openclaw.agent:id/settings_button",
            packageName = "com.openclaw.agent",
            clickable = true
        )
        val input = FakeNode(
            text = "",
            viewIdResourceName = "com.openclaw.agent:id/chat_input",
            packageName = "com.openclaw.agent",
            className = "android.widget.EditText",
            clickable = true
        )
        val send = FakeNode(
            contentDescription = "Send",
            viewIdResourceName = "com.openclaw.agent:id/send_button",
            packageName = "com.openclaw.agent",
            clickable = true
        )
        return FakeNode(children = listOf(settings, input, send))
    }
}

private class FakeNode(
    override val text: String? = null,
    override val contentDescription: String? = null,
    override val viewIdResourceName: String? = null,
    override val className: String? = null,
    override val packageName: String? = null,
    override val clickable: Boolean = false,
    override val editable: Boolean = false,
    override val scrollable: Boolean = false,
    override val bounds: com.openclaw.agent.core.deviceagent.model.UiBounds? = null,
    children: List<FakeNode> = emptyList()
) : AccessibilityNode {
    override var parent: AccessibilityNode? = null
    override val childrenInternal = children.toMutableList<AccessibilityNode>()
    override val children: List<AccessibilityNode>
        get() = childrenInternal
    val performedActions = mutableListOf<Int>()

    init {
        childrenInternal.forEach { it.parent = this }
    }

    override fun performAction(action: Int): Boolean {
        performedActions += action
        return clickable && action == ACTION_CLICK
    }

    override fun setText(text: String): Boolean = false

    companion object {
        const val ACTION_CLICK = 16
    }
}
