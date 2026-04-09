package com.openclaw.agent.core.deviceagent.snapshot

import com.openclaw.agent.core.deviceagent.accessibility.AccessibilityNode

object AccessibilitySnapshotBuilder {
    fun build(
        root: AccessibilityNode?,
        packageName: String,
        activityName: String?
    ): DeviceSnapshot {
        val rootSnapshot = if (root != null) buildNode(root, "0") else UiNodeSnapshot(path = "0")
        val flattened = flatten(rootSnapshot)
        val searchIndex = flattened.joinToString("\n") { node ->
            listOfNotNull(
                node.path,
                node.text,
                node.contentDescription,
                node.viewIdResourceName?.substringAfterLast('/'),
                node.className
            ).joinToString(" | ")
        }
        return DeviceSnapshot(
            packageName = packageName,
            activityName = activityName,
            nodeCount = flattened.size,
            root = rootSnapshot,
            searchIndex = searchIndex
        )
    }

    private fun buildNode(node: AccessibilityNode, path: String): UiNodeSnapshot {
        val children = node.children.mapIndexed { index, child ->
            buildNode(child, "$path.$index")
        }
        return UiNodeSnapshot(
            path = path,
            text = node.text,
            contentDescription = node.contentDescription,
            viewIdResourceName = node.viewIdResourceName,
            className = node.className,
            packageName = node.packageName,
            clickable = node.clickable,
            editable = node.editable,
            scrollable = node.scrollable,
            bounds = node.bounds,
            children = children
        )
    }

    private fun flatten(root: UiNodeSnapshot): List<UiNodeSnapshot> {
        val result = mutableListOf<UiNodeSnapshot>()
        fun walk(node: UiNodeSnapshot) {
            result += node
            node.children.forEach(::walk)
        }
        if (root.children.isEmpty() && root.text == null && root.contentDescription == null && root.viewIdResourceName == null && root.className == null) {
            return emptyList()
        }
        walk(root)
        return result
    }
}
