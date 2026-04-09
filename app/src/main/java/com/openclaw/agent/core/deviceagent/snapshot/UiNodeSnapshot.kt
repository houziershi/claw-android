package com.openclaw.agent.core.deviceagent.snapshot

import com.openclaw.agent.core.deviceagent.model.UiBounds
import kotlinx.serialization.Serializable

@Serializable
data class UiNodeSnapshot(
    val path: String,
    val text: String? = null,
    val contentDescription: String? = null,
    val viewIdResourceName: String? = null,
    val className: String? = null,
    val packageName: String? = null,
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val bounds: UiBounds? = null,
    val children: List<UiNodeSnapshot> = emptyList()
)
