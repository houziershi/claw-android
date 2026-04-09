package com.openclaw.agent.core.deviceagent.model

import kotlinx.serialization.Serializable

@Serializable
data class UiBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

@Serializable
data class UiLocator(
    val resourceId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val testTag: String? = null,
    val className: String? = null,
    val packageName: String? = null,
    val bounds: UiBounds? = null
) {
    init {
        require(
            resourceId != null ||
                text != null ||
                contentDescription != null ||
                testTag != null ||
                className != null ||
                packageName != null ||
                bounds != null
        ) {
            "UiLocator requires at least one selector"
        }
    }
}

@Serializable
data class AutomationTarget(
    val packageName: String,
    val activityName: String? = null,
    val allowRootFallback: Boolean = false
)
