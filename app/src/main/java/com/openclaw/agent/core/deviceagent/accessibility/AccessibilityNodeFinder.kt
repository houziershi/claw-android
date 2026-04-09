package com.openclaw.agent.core.deviceagent.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.openclaw.agent.core.deviceagent.model.UiLocator

interface AccessibilityNode {
    val text: String?
    val contentDescription: String?
    val viewIdResourceName: String?
    val className: String?
    val packageName: String?
    val clickable: Boolean
    val editable: Boolean
    val scrollable: Boolean
    val bounds: com.openclaw.agent.core.deviceagent.model.UiBounds?
    var parent: AccessibilityNode?
    val childrenInternal: MutableList<AccessibilityNode>
    val children: List<AccessibilityNode>

    fun performAction(action: Int): Boolean
    fun setText(text: String): Boolean
}

enum class ScrollDirection {
    FORWARD,
    BACKWARD
}

object AccessibilityNodeFinder {
    fun findFirst(root: AccessibilityNode?, locator: UiLocator): AccessibilityNode? {
        if (root == null) return null
        if (matches(root, locator)) return root
        root.children.forEach { child ->
            val match = findFirst(child, locator)
            if (match != null) return match
        }
        return null
    }

    fun clickNearestClickable(node: AccessibilityNode?): Boolean {
        var current = node
        while (current != null) {
            if (current.clickable && current.performAction(ACTION_CLICK)) {
                return true
            }
            current = current.parent
        }
        return false
    }

    fun matches(node: AccessibilityNode, locator: UiLocator): Boolean {
        locator.text?.let { if (!equalsNormalized(node.text, it)) return false }
        locator.contentDescription?.let { if (!equalsNormalized(node.contentDescription, it)) return false }
        locator.resourceId?.let { if (!equalsNormalized(node.viewIdResourceName, it)) return false }
        locator.testTag?.let { if (!matchesTestTag(node.viewIdResourceName, it)) return false }
        locator.className?.let { if (!equalsNormalized(node.className, it)) return false }
        locator.packageName?.let { if (!equalsNormalized(node.packageName, it)) return false }
        locator.bounds?.let { if (node.bounds != it) return false }
        return true
    }

    fun setText(node: AccessibilityNode?, text: String): Boolean {
        var current = node
        while (current != null) {
            if (current.editable && current.setText(text)) {
                return true
            }
            current = current.parent
        }
        return false
    }

    fun scroll(node: AccessibilityNode?, direction: ScrollDirection): Boolean {
        var current = node
        while (current != null) {
            if (current.scrollable) {
                val action = when (direction) {
                    ScrollDirection.FORWARD -> ACTION_SCROLL_FORWARD
                    ScrollDirection.BACKWARD -> ACTION_SCROLL_BACKWARD
                }
                if (current.performAction(action)) return true
            }
            current = current.parent
        }
        return false
    }

    private fun matchesTestTag(viewIdResourceName: String?, testTag: String): Boolean {
        val viewId = viewIdResourceName?.substringAfterLast('/')
        return equalsNormalized(viewId, testTag)
    }

    private fun equalsNormalized(actual: String?, expected: String): Boolean {
        return actual?.trim()?.lowercase() == expected.trim().lowercase()
    }

    const val ACTION_CLICK: Int = 16
    const val ACTION_SCROLL_FORWARD: Int = 4096
    const val ACTION_SCROLL_BACKWARD: Int = 8192
}

internal class AccessibilityNodeInfoAdapter(
    val node: AccessibilityNodeInfo,
    override var parent: AccessibilityNode? = null
) : AccessibilityNode {
    override val text: String?
        get() = node.text?.toString()

    override val contentDescription: String?
        get() = node.contentDescription?.toString()

    override val viewIdResourceName: String?
        get() = node.viewIdResourceName

    override val className: String?
        get() = node.className?.toString()

    override val packageName: String?
        get() = node.packageName?.toString()

    override val clickable: Boolean
        get() = node.isClickable

    override val editable: Boolean
        get() = node.isEditable

    override val scrollable: Boolean
        get() = node.isScrollable

    override val bounds: com.openclaw.agent.core.deviceagent.model.UiBounds?
        get() {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            return com.openclaw.agent.core.deviceagent.model.UiBounds(rect.left, rect.top, rect.right, rect.bottom)
        }

    override val childrenInternal: MutableList<AccessibilityNode>
        get() = children.toMutableList()

    override val children: List<AccessibilityNode>
        get() = buildList {
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                add(AccessibilityNodeInfoAdapter(child, this@AccessibilityNodeInfoAdapter))
            }
        }

    override fun performAction(action: Int): Boolean = node.performAction(action)

    override fun setText(text: String): Boolean {
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
}
