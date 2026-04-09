package com.openclaw.agent.core.tools.impl

import com.openclaw.agent.core.deviceagent.model.AutomationPlan
import com.openclaw.agent.core.deviceagent.model.ExecutionReport
import com.openclaw.agent.core.deviceagent.model.ExecutionStatus
import com.openclaw.agent.core.deviceagent.report.ExecutionRecorder
import com.openclaw.agent.core.deviceagent.report.ExecutionSummary
import com.openclaw.agent.core.deviceagent.report.ExecutionSummaryFormatter
import com.openclaw.agent.core.deviceagent.snapshot.DeviceSnapshot
import com.openclaw.agent.core.deviceagent.capability.CapabilitySet
import com.openclaw.agent.core.deviceagent.execution.ActionExecutor
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class DeviceExecutePlanTool(
    private val planExecutor: DevicePlanExecutor,
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
) : Tool {
    override val name: String = "device_execute_plan"
    override val description: String = "Execute a structured Android device automation plan and return the execution report plus summary."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("plan") {
                put("type", "object")
                put("description", "AutomationPlan JSON payload to execute")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("plan")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val planElement = args["plan"] ?: return ToolResult(
            success = false,
            content = "",
            errorMessage = "Missing 'plan' parameter"
        )
        return runCatching {
            val plan = json.decodeFromJsonElement(AutomationPlan.serializer(), planElement)
            val payload = planExecutor.execute(plan)
            ToolResult(success = true, content = json.encodeToString(DeviceExecutePlanPayload.serializer(), payload))
        }.getOrElse {
            ToolResult(success = false, content = "", errorMessage = it.message ?: "Failed to execute plan")
        }
    }
}

interface DevicePlanExecutor {
    suspend fun execute(plan: AutomationPlan): DeviceExecutePlanPayload
}

class DefaultDevicePlanExecutor(
    private val actionExecutor: ActionExecutor,
    private val capabilitySet: CapabilitySet,
    private val recorder: ExecutionRecorder,
    private val formatter: ExecutionSummaryFormatter,
    private val snapshotSource: DeviceSnapshotSource? = null
) : DevicePlanExecutor {
    override suspend fun execute(plan: AutomationPlan): DeviceExecutePlanPayload {
        val runId = plan.id
        val stepResults = mutableListOf<com.openclaw.agent.core.deviceagent.model.ExecutionStepResult>()

        for (step in plan.steps) {
            val rawResult = actionExecutor.execute(step, capabilitySet)
            val enriched = recorder.attachStepArtifacts(
                runId = runId,
                stepResult = rawResult,
                snapshot = snapshotSource?.currentSnapshot()
            )
            stepResults += enriched
            if (enriched.status == ExecutionStatus.FAILED && !step.optional) {
                break
            }
        }

        val report = recorder.finalizeReport(planId = plan.id, steps = stepResults)
        val summary = formatter.format(report)
        return DeviceExecutePlanPayload(report = report, summary = summary)
    }
}

@Serializable
data class DeviceExecutePlanPayload(
    val report: ExecutionReport,
    val summary: ExecutionSummary
)
