package com.openclaw.agent.core.deviceagent.snapshot

import kotlinx.serialization.Serializable

@Serializable
data class DeviceSnapshot(
    val packageName: String,
    val activityName: String? = null,
    val nodeCount: Int,
    val root: UiNodeSnapshot,
    val searchIndex: String
)
