package com.openclaw.agent.core.deviceagent.execution

import com.openclaw.agent.core.deviceagent.capability.CapabilityRoute
import com.openclaw.agent.core.deviceagent.capability.CapabilitySet
import com.openclaw.agent.core.deviceagent.model.AutomationStep
import com.openclaw.agent.core.deviceagent.model.ExecutionFailure
import com.openclaw.agent.core.deviceagent.model.ExecutionStepResult
import com.openclaw.agent.core.deviceagent.model.ExecutionStatus

interface ActionRouteHandler {
    val route: CapabilityRoute
    suspend fun execute(step: AutomationStep): ExecutionStepResult
}

class ActionExecutor(
    handlers: List<ActionRouteHandler>,
    private val fallbackPolicy: FallbackPolicy = FallbackPolicy()
) {
    private val handlersByRoute = handlers.associateBy { it.route }

    suspend fun execute(step: AutomationStep, capabilities: CapabilitySet): ExecutionStepResult {
        val routes = fallbackPolicy.routesFor(step.action, capabilities)
        if (routes.isEmpty()) {
            return ExecutionStepResult(
                stepId = step.id,
                status = ExecutionStatus.FAILED,
                failure = ExecutionFailure(
                    code = "no_available_route",
                    message = "No capability route available for ${step.action::class.simpleName}",
                    recoverable = false
                )
            )
        }

        var lastFailure: ExecutionStepResult? = null

        for (route in routes) {
            val handler = handlersByRoute[route] ?: continue
            val result = handler.execute(step)
            if (result.status == ExecutionStatus.PASSED) {
                return result
            }
            lastFailure = result
            if (result.failure?.recoverable != true) {
                return result
            }
        }

        return lastFailure ?: ExecutionStepResult(
            stepId = step.id,
            status = ExecutionStatus.FAILED,
            failure = ExecutionFailure(
                code = "no_handler_registered",
                message = "No handler registered for candidate routes: ${routes.joinToString()}",
                recoverable = false
            )
        )
    }
}
