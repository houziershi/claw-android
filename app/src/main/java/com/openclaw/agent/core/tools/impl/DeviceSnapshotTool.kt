package com.openclaw.agent.core.tools.impl

import android.content.ComponentName
import android.content.Context
import com.openclaw.agent.core.deviceagent.accessibility.AccessibilityNodeInfoAdapter
import com.openclaw.agent.core.deviceagent.accessibility.TestAgentAccessibilityService
import com.openclaw.agent.core.deviceagent.capability.AccessibilityCapability
import com.openclaw.agent.core.deviceagent.capability.AppControlCapability
import com.openclaw.agent.core.deviceagent.capability.CapabilitySet
import com.openclaw.agent.core.deviceagent.capability.ShellCapability
import com.openclaw.agent.core.deviceagent.snapshot.AccessibilitySnapshotBuilder
import com.openclaw.agent.core.deviceagent.snapshot.DeviceSnapshot
import com.openclaw.agent.core.deviceagent.shell.ShellCommandRunner
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class DeviceSnapshotTool(
    private val snapshotSource: DeviceSnapshotSource,
    private val capabilityStatusSource: DeviceCapabilityStatusSource,
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
    }
) : Tool {
    override val name: String = "device_snapshot"
    override val description: String = "Capture the current device UI snapshot and capability status for the Android test agent."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("include_search_index") {
                put("type", "boolean")
                put("description", "Whether to include the flattened searchIndex text in the response. Default true.")
            }
            putJsonObject("include_tree") {
                put("type", "boolean")
                put("description", "Whether to include the full UI tree in the response. Default true.")
            }
        }
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val includeSearchIndex = args["include_search_index"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
        val includeTree = args["include_tree"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true

        val status = capabilityStatusSource.currentStatus()
        val snapshot = snapshotSource.currentSnapshot()
        val payload = DeviceSnapshotPayload(
            capabilities = status,
            snapshot = snapshot?.let {
                if (includeTree && includeSearchIndex) it
                else it.copy(
                    searchIndex = if (includeSearchIndex) it.searchIndex else "",
                    root = if (includeTree) it.root else it.root.copy(children = emptyList())
                )
            },
            summary = snapshot?.let {
                "package=${it.packageName}, activity=${it.activityName ?: "unknown"}, nodeCount=${it.nodeCount}"
            } ?: "No active UI snapshot available"
        )
        return ToolResult(
            success = true,
            content = json.encodeToString(DeviceSnapshotPayload.serializer(), payload)
        )
    }
}

interface DeviceSnapshotSource {
    suspend fun currentSnapshot(): DeviceSnapshot?
}

interface DeviceCapabilityStatusSource {
    suspend fun currentStatus(): DeviceCapabilityStatus
}

@Serializable
data class DeviceCapabilityStatus(
    val availableCapabilities: List<String>,
    val accessibilityEnabled: Boolean,
    val accessibilityConnected: Boolean,
    val shellAvailable: Boolean,
    val appControlAvailable: Boolean,
    val screenshotAvailable: Boolean
)

@Serializable
data class DeviceSnapshotPayload(
    val capabilities: DeviceCapabilityStatus,
    val snapshot: DeviceSnapshot? = null,
    val summary: String
)

class CapabilitySetStatusSource(
    private val capabilitySet: CapabilitySet,
    private val accessibilityConnectedProvider: () -> Boolean = { TestAgentAccessibilityService.current() != null }
) : DeviceCapabilityStatusSource {
    override suspend fun currentStatus(): DeviceCapabilityStatus {
        val available = capabilitySet.availableCapabilities()
        return DeviceCapabilityStatus(
            availableCapabilities = available.map { it.name },
            accessibilityEnabled = com.openclaw.agent.core.deviceagent.capability.CapabilityType.ACCESSIBILITY in available,
            accessibilityConnected = accessibilityConnectedProvider(),
            shellAvailable = com.openclaw.agent.core.deviceagent.capability.CapabilityType.SHELL in available,
            appControlAvailable = com.openclaw.agent.core.deviceagent.capability.CapabilityType.APP_CONTROL in available,
            screenshotAvailable = com.openclaw.agent.core.deviceagent.capability.CapabilityType.SCREENSHOT in available
        )
    }
}

class AccessibilityServiceSnapshotSource(
    private val context: Context
) : DeviceSnapshotSource {
    override suspend fun currentSnapshot(): DeviceSnapshot? {
        val service = TestAgentAccessibilityService.current() ?: return null
        val root = service.currentRootNode() ?: return null
        val packageName = root.packageName?.toString() ?: context.packageName
        return AccessibilitySnapshotBuilder.build(
            root = AccessibilityNodeInfoAdapter(root),
            packageName = packageName,
            activityName = null
        )
    }
}

class AndroidDeviceCapabilitySet(
    context: Context,
    shellCommandRunner: ShellCommandRunner = ShellCommandRunner()
) {
    val capabilities = CapabilitySet(
        accessibility = object : AccessibilityCapability {
            override suspend fun isAvailable(): Boolean {
                val component = ComponentName(context, TestAgentAccessibilityService::class.java).flattenToString()
                return TestAgentAccessibilityService.isEnabled(context, component)
            }
        },
        shell = object : ShellCapability {
            override suspend fun isAvailable(): Boolean = shellCommandRunner.isAvailable()
        },
        appControl = object : AppControlCapability {
            override suspend fun isAvailable(): Boolean = true
        }
    )
}
