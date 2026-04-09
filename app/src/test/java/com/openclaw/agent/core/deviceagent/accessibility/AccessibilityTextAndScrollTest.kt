package com.openclaw.agent.core.deviceagent.accessibility

import com.google.common.truth.Truth.assertThat
import com.openclaw.agent.core.deviceagent.model.UiBounds
import com.openclaw.agent.core.deviceagent.model.UiLocator
import org.junit.Test

class AccessibilityTextAndScrollTest {

    @Test
    fun setText_usesNearestEditableNode() {
        val editable = FakeActionNode(
            className = "android.widget.EditText",
            editable = true
        )
        val label = FakeActionNode(text = "API Key", parentOverride = editable)
        editable.childrenInternal += label

        val result = AccessibilityNodeFinder.setText(label, "sk-live")

        assertThat(result).isTrue()
        assertThat(editable.lastSetText).isEqualTo("sk-live")
    }

    @Test
    fun setText_returnsFalseWhenNoEditableNodeExists() {
        val node = FakeActionNode(text = "API Key", editable = false)

        val result = AccessibilityNodeFinder.setText(node, "sk-live")

        assertThat(result).isFalse()
    }

    @Test
    fun scrollForward_usesNearestScrollableAncestor() {
        val scrollable = FakeActionNode(scrollable = true)
        val child = FakeActionNode(text = "Session item", parentOverride = scrollable)
        scrollable.childrenInternal += child

        val result = AccessibilityNodeFinder.scroll(child, ScrollDirection.FORWARD)

        assertThat(result).isTrue()
        assertThat(scrollable.performedActions).containsExactly(AccessibilityNodeFinder.ACTION_SCROLL_FORWARD)
    }

    @Test
    fun matches_supportsBoundsSelector() {
        val node = FakeActionNode(bounds = UiBounds(0, 10, 100, 200))

        val matched = AccessibilityNodeFinder.matches(node, UiLocator(bounds = UiBounds(0, 10, 100, 200)))

        assertThat(matched).isTrue()
    }

    @Test
    fun matches_supportsTestTagMappedToViewIdSuffix() {
        val node = FakeActionNode(viewIdResourceName = "com.openclaw.agent:id/settings_save")

        val matched = AccessibilityNodeFinder.matches(node, UiLocator(testTag = "settings_save"))

        assertThat(matched).isTrue()
    }
}

private class FakeActionNode(
    override val text: String? = null,
    override val contentDescription: String? = null,
    override val viewIdResourceName: String? = null,
    override val className: String? = null,
    override val packageName: String? = null,
    override val clickable: Boolean = false,
    override val editable: Boolean = false,
    override val scrollable: Boolean = false,
    override val bounds: UiBounds? = null,
    parentOverride: AccessibilityNode? = null
) : AccessibilityNode {
    override var parent: AccessibilityNode? = parentOverride
    override val childrenInternal = mutableListOf<AccessibilityNode>()
    override val children: List<AccessibilityNode>
        get() = childrenInternal

    val performedActions = mutableListOf<Int>()
    var lastSetText: String? = null

    override fun performAction(action: Int): Boolean {
        performedActions += action
        return when (action) {
            AccessibilityNodeFinder.ACTION_SCROLL_FORWARD,
            AccessibilityNodeFinder.ACTION_SCROLL_BACKWARD -> scrollable
            AccessibilityNodeFinder.ACTION_CLICK -> clickable
            else -> false
        }
    }

    override fun setText(text: String): Boolean {
        if (!editable) return false
        lastSetText = text
        return true
    }
}
