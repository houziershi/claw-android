package com.openclaw.agent.core.runtime

import android.util.Log
import com.openclaw.agent.core.llm.*
import com.openclaw.agent.core.memory.MemoryContextBuilder
import com.openclaw.agent.core.memory.MemoryStore
import com.openclaw.agent.core.runtime.hooks.*
import com.openclaw.agent.core.skill.SkillEngine
import com.openclaw.agent.core.tools.ToolRegistry
import com.openclaw.agent.core.tools.ToolRouter
import com.openclaw.agent.data.db.MessageDao
import com.openclaw.agent.data.db.SessionDao
import com.openclaw.agent.data.db.entities.MessageEntity
import com.openclaw.agent.data.preferences.SettingsStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AgentRuntime"
private const val MAX_TOOL_LOOPS = 10
private const val MAX_RATE_LIMIT_RETRIES = 3

@Singleton
class AgentRuntime @Inject constructor(
    private val settingsStore: SettingsStore,
    private val memoryContextBuilder: MemoryContextBuilder,
    private val memoryStore: MemoryStore,
    private val skillEngine: SkillEngine,
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val toolRegistry: ToolRegistry,
    private val toolRouter: ToolRouter,
    private val twoPhaseRouter: TwoPhaseRouter,
    private val contextCompactor: ContextCompactor,
    private val apiKeyManager: ApiKeyManager,
    private val userHookRegistry: UserHookRegistry
) : ChatRuntime {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val hookEngine by lazy {
        HookEngine().apply {
            register<HookEvent.UserPromptSubmit>(priority = 0, handler = SkillRouterHook(skillEngine, toolRegistry))
            register<HookEvent.PreToolUse>(priority = 0, handler = LoopDetectionHook())
            register<HookEvent.PreToolUse>(matcher = "mijia_control", priority = 1, handler = MijiaSafetyHook())
            register<HookEvent.PostToolUse>(priority = 0, handler = ResultTruncateHook())
            register<HookEvent.PostToolUse>(priority = 1, handler = ContextGuardHook())
            
            // Register user-defined hooks if enabled
            val userHooksEnabled = runBlocking { settingsStore.userHooksEnabledFlow.first() }
            if (userHooksEnabled) {
                register<HookEvent.PreToolUse>(priority = 100, handler = UserDefinedHook(userHookRegistry))
            }
        }
    }

    /**
     * Process a user message and stream back AgentEvents.
     * Implements the function-calling loop: LLM → tool_use → tool_result → LLM → ...
     */
    override fun chat(
        sessionId: String,
        userMessage: String,
        model: String,
        apiKey: String,
        baseUrl: String
    ): Flow<AgentEvent> = flow {

        // Phase 7.3: API key rotation
        apiKeyManager.reset()
        val primaryKeyEntry = apiKeyManager.getCurrentKey()
        var effectiveApiKey = primaryKeyEntry.apiKey.ifBlank { apiKey }
        var effectiveBaseUrl = primaryKeyEntry.baseUrl.ifBlank { baseUrl }
        
        if (effectiveApiKey.isBlank()) {
            emit(AgentEvent.Error("API key not set. Go to Settings to add your API key."))
            return@flow
        }

        // Save user message to DB
        val userMsgEntity = MessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = "user",
            content = userMessage,
            timestamp = System.currentTimeMillis()
        )
        messageDao.insertMessage(userMsgEntity)
        sessionDao.incrementMessageCount(sessionId, System.currentTimeMillis())

        // Match skill for this message
        val matchedSkill = skillEngine.matchSkill(userMessage)
        val skillContext = matchedSkill?.let { skillEngine.buildSkillContext(it) }

        // Build system prompt with memory context + skill context
        val systemPrompt = memoryContextBuilder.buildSystemPrompt(
            lastUserMessage = userMessage,
            skillContext = skillContext
        )

        // Build conversation history from DB
        val dbMessages = messageDao.getMessagesForSessionOnce(sessionId)
        val rawHistory = dbMessages.map { msg ->
            when {
                // Tool result messages: reconstruct as tool_result content blocks
                msg.role == "user" && msg.toolResultJson != null -> {
                    LlmMessage(
                        role = "user",
                        content = json.parseToJsonElement(msg.toolResultJson)
                    )
                }
                // Assistant messages with tool calls: reconstruct as tool_use content blocks
                msg.role == "assistant" && msg.toolCallsJson != null -> {
                    LlmMessage(
                        role = "assistant",
                        content = json.parseToJsonElement(msg.toolCallsJson)
                    )
                }
                // Normal text messages
                else -> {
                    LlmMessage(
                        role = msg.role,
                        content = JsonPrimitive(msg.content)
                    )
                }
            }
        }

        // Phase 7.2: Context compaction
        val compactionEnabled = runBlocking { settingsStore.contextCompactionEnabledFlow.first() }
        val conversationHistory = if (compactionEnabled) {
            runBlocking { contextCompactor.compact(rawHistory, effectiveApiKey, effectiveBaseUrl, model) }.toMutableList()
        } else {
            rawHistory.toMutableList()
        }

        // Fire UserPromptSubmit hook — SkillRouterHook may filter tools
        val promptDecision = runBlocking {
            hookEngine.fire(HookEvent.UserPromptSubmit(prompt = userMessage, sessionId = sessionId))
        }

        // Get tool definitions (possibly filtered by SkillRouterHook)
        val allToolDefs = toolRegistry.toToolDefinitions()
        val toolDefs = if (promptDecision is HookDecision.FilterTools) {
            val filtered = allToolDefs.filter { it.name in promptDecision.toolNames }
            Log.d(TAG, "SkillRouter active [${promptDecision.skillName}]: ${filtered.size} tools — ${filtered.joinToString { it.name }}")
            filtered
        } else {
            allToolDefs
        }

        // Phase 7.1: Two-phase routing (if enabled)
        val twoPhaseEnabled = runBlocking { settingsStore.twoPhaseRoutingEnabledFlow.first() }
        val finalToolDefs = if (twoPhaseEnabled) {
            val routedTools = runBlocking { twoPhaseRouter.route(userMessage, effectiveApiKey, effectiveBaseUrl, model) }
            if (routedTools != null) {
                val filtered = toolDefs.filter { it.name in routedTools }
                Log.d(TAG, "TwoPhaseRouter: filtered to ${filtered.size} tools: ${filtered.joinToString { it.name }}")
                filtered.ifEmpty { toolDefs }
            } else toolDefs
        } else toolDefs
        
        Log.d(TAG, "Available tools: ${finalToolDefs.size} — ${finalToolDefs.joinToString { it.name }}")

        // ── Function Calling Loop ──────────────────────────────────────
        var loopCount = 0
        var totalInputTokens = 0
        var totalOutputTokens = 0
        val fullAssistantText = StringBuilder()
        var consecutiveToolErrors = 0
        var lastFailedToolName = ""
        // callHistory accumulates ToolCallRecords for LoopDetectionHook
        val callHistory = mutableListOf<ToolCallRecord>()

        while (loopCount < MAX_TOOL_LOOPS) {
            loopCount++
            Log.d(TAG, "=== LLM call #$loopCount ===")

            val currentText = StringBuilder()
            var stopReason = "end_turn"
            var inputTokens = 0
            var outputTokens = 0
            val pendingToolCalls = mutableListOf<PendingToolCall>()
            var hasError = false

            // Phase 7.3: Rate-limit retry loop
            var rlRetries = 0
            var rateLimited: Boolean
            do {
                rateLimited = false
                currentText.clear()
                pendingToolCalls.clear()
                stopReason = "end_turn"
                inputTokens = 0
                outputTokens = 0
                var currentToolId = ""
                var currentToolName = ""
                val currentToolInput = StringBuilder()

                val rlEntry = apiKeyManager.getCurrentKey()
                val callApiKey = rlEntry.apiKey.ifBlank { effectiveApiKey }
                val callBaseUrl = rlEntry.baseUrl.ifBlank { effectiveBaseUrl }

                createClient(callApiKey, callBaseUrl).chat(
                    messages = conversationHistory,
                    systemPrompt = systemPrompt,
                    tools = finalToolDefs,
                    model = model
                ).collect { event ->
                    when (event) {
                        is LlmEvent.TextChunk -> {
                            currentText.append(event.text)
                            fullAssistantText.append(event.text)
                            emit(AgentEvent.TextChunk(event.text))
                        }
                        is LlmEvent.ToolCallStart -> {
                            currentToolId = event.id
                            currentToolName = event.name
                            currentToolInput.clear()
                            Log.d(TAG, "Tool call started: ${event.name} (${event.id})")
                            emit(AgentEvent.ToolCallStarted(event.id, event.name))
                        }
                        is LlmEvent.ToolCallInput -> {
                            currentToolInput.append(event.partialJson)
                        }
                        is LlmEvent.ToolCallComplete -> {
                            Log.d(TAG, "Tool call complete: ${event.name} args=${event.input}")
                            pendingToolCalls.add(PendingToolCall(event.id, event.name, event.input))
                            currentToolId = ""
                            currentToolName = ""
                            currentToolInput.clear()
                        }
                        is LlmEvent.Done -> {
                            stopReason = event.stopReason
                            inputTokens = event.inputTokens
                            outputTokens = event.outputTokens
                            totalInputTokens += inputTokens
                            totalOutputTokens += outputTokens
                        }
                        is LlmEvent.Error -> {
                            val isRateLimit = event.code == 429 || event.code == 529
                            if (isRateLimit && rlRetries < MAX_RATE_LIMIT_RETRIES && !apiKeyManager.isExhausted()) {
                                Log.w(TAG, "Rate limit (${event.code}), rotating key (attempt $rlRetries)")
                                rateLimited = true
                            } else {
                                emit(AgentEvent.Error(event.message))
                                hasError = true
                            }
                        }
                    }
                }

                if (rateLimited) {
                    apiKeyManager.onRateLimit()
                    rlRetries++
                    Log.d(TAG, "Retrying with key: ${apiKeyManager.getCurrentKey().label}")
                }
            } while (rateLimited && rlRetries <= MAX_RATE_LIMIT_RETRIES)

            if (hasError) return@flow

            // ── If stop_reason is NOT tool_use, we're done ─────────────
            if (stopReason != "tool_use" || pendingToolCalls.isEmpty()) {
                Log.d(TAG, "Turn complete: stopReason=$stopReason, loops=$loopCount")

                // Save assistant text message to DB
                if (currentText.isNotEmpty()) {
                    val assistantMsg = MessageEntity(
                        id = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        role = "assistant",
                        content = currentText.toString(),
                        timestamp = System.currentTimeMillis(),
                        inputTokens = totalInputTokens,
                        outputTokens = totalOutputTokens,
                        model = model
                    )
                    messageDao.insertMessage(assistantMsg)
                    sessionDao.incrementMessageCount(sessionId, System.currentTimeMillis())
                }

                // Auto-record to daily notes
                try {
                    val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                    val summary = "- [$time] User: ${userMessage.take(80)} → Assistant: ${fullAssistantText.toString().take(120)}"
                    memoryStore.appendToDailyNote(summary)
                    Log.d(TAG, "Daily note recorded")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to record daily note", e)
                }

                emit(AgentEvent.TurnComplete(fullAssistantText.toString(), Usage(totalInputTokens, totalOutputTokens), model))
                return@flow
            }

            // ── stop_reason is tool_use → execute tools ────────────────
            Log.d(TAG, "Processing ${pendingToolCalls.size} tool call(s)")
            emit(AgentEvent.Thinking)

            // Build the assistant message content blocks (text + tool_use)
            val assistantContentBlocks = buildJsonArray {
                if (currentText.isNotEmpty()) {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", currentText.toString())
                    })
                }
                pendingToolCalls.forEach { tc ->
                    add(buildJsonObject {
                        put("type", "tool_use")
                        put("id", tc.id)
                        put("name", tc.name)
                        put("input", tc.args)
                    })
                }
            }

            // Save assistant tool_use message to DB
            val assistantToolMsg = MessageEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = "assistant",
                content = currentText.toString().ifEmpty { "[Tool call: ${pendingToolCalls.joinToString { it.name }}]" },
                toolCallsJson = assistantContentBlocks.toString(),
                timestamp = System.currentTimeMillis(),
                inputTokens = inputTokens,
                outputTokens = outputTokens
            )
            messageDao.insertMessage(assistantToolMsg)

            // Add assistant message to conversation history
            conversationHistory.add(
                LlmMessage(role = "assistant", content = assistantContentBlocks)
            )

            // Execute each tool and collect results
            data class ToolExecResult(
                val tc: PendingToolCall,
                val result: com.openclaw.agent.core.tools.ToolResult
            )
            val execResults = mutableListOf<ToolExecResult>()
            for (tc in pendingToolCalls) {
                // Fire PreToolUse hook for loop detection
                val preDecision = runBlocking {
                    hookEngine.fire(HookEvent.PreToolUse(
                        toolName = tc.name,
                        toolInput = tc.args,
                        turnIndex = loopCount,
                        callHistory = callHistory.toList()
                    ))
                }

                // Handle loop detection decisions
                when (preDecision) {
                    is HookDecision.Block -> {
                        Log.w(TAG, "PreToolUse BLOCK for ${tc.name}: ${preDecision.reason}")
                        emit(AgentEvent.Error(preDecision.reason))
                        return@flow
                    }
                    is HookDecision.Deny -> {
                        Log.w(TAG, "PreToolUse DENY for ${tc.name}: ${preDecision.reason}")
                        // Inject a synthetic error result so the LLM knows to try something else
                        val syntheticResult = com.openclaw.agent.core.tools.ToolResult(
                            success = false,
                            content = "",
                            errorMessage = "[loop guard] ${preDecision.reason}"
                        )
                        execResults.add(ToolExecResult(tc, syntheticResult))
                        emit(AgentEvent.ToolCallFinished(
                            id = tc.id, name = tc.name, input = tc.args,
                            result = "[loop guard] ${preDecision.reason}", success = false
                        ))
                        continue
                    }
                    else -> { /* Allow / other: proceed */ }
                }

                val startTime = System.currentTimeMillis()
                val result = toolRouter.execute(tc.name, tc.args)
                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "Tool ${tc.name} result: success=${result.success}, content=${result.content.take(200)}")

                // Fire PostToolUse hook for result truncation / context guard
                val postDecision = runBlocking {
                    hookEngine.fire(HookEvent.PostToolUse(
                        toolName = tc.name,
                        toolInput = tc.args,
                        toolResult = if (result.success) result.content else (result.errorMessage ?: ""),
                        success = result.success,
                        durationMs = duration
                    ))
                }
                val finalContent = when (postDecision) {
                    is HookDecision.ModifyResult -> postDecision.newResult
                    else -> if (result.success) result.content else (result.errorMessage ?: "")
                }
                val finalResult = if (postDecision is HookDecision.ModifyResult) {
                    com.openclaw.agent.core.tools.ToolResult(result.success, finalContent, result.errorMessage)
                } else {
                    result
                }

                // Record in callHistory for loop detection
                callHistory.add(ToolCallRecord(
                    name = tc.name,
                    argsHash = tc.args.hashCode(),
                    resultHash = finalContent.hashCode(),
                    timestamp = System.currentTimeMillis()
                ))

                // Emit ToolCallFinished for normal (non-denied) executions
                emit(AgentEvent.ToolCallFinished(
                    id = tc.id,
                    name = tc.name,
                    input = tc.args,
                    result = if (finalResult.success) finalResult.content else (finalResult.errorMessage ?: "Unknown error"),
                    success = finalResult.success
                ))

                execResults.add(ToolExecResult(tc, finalResult))
            }

            // Detect repeated tool failures (consecutive errors)
            val allFailed = execResults.all { !it.result.success }
            val failedToolNames = execResults.filter { !it.result.success }.map { it.tc.name }
            
            if (allFailed && failedToolNames.isNotEmpty()) {
                val currentFailedName = failedToolNames.first()
                if (currentFailedName == lastFailedToolName) {
                    consecutiveToolErrors++
                } else {
                    consecutiveToolErrors = 1
                    lastFailedToolName = currentFailedName
                }
                if (consecutiveToolErrors >= 3) {
                    Log.w(TAG, "Tool $currentFailedName failed $consecutiveToolErrors times consecutively, stopping")
                    emit(AgentEvent.Error("工具 $currentFailedName 连续失败 $consecutiveToolErrors 次，已停止。请检查参数后重试。"))
                    return@flow
                }
            } else {
                consecutiveToolErrors = 0
                lastFailedToolName = ""
            }

            // Build tool result JSON blocks
            val toolResultBlocks = buildJsonArray {
                execResults.forEach { (tc, result) ->
                    add(buildJsonObject {
                        put("type", "tool_result")
                        put("tool_use_id", tc.id)
                        if (result.success) {
                            put("content", result.content)
                        } else {
                            put("is_error", true)
                            put("content", result.errorMessage ?: "Tool execution failed")
                        }
                    })
                }
            }

            // Save tool results to DB
            val toolResultMsg = MessageEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = "user",
                content = pendingToolCalls.joinToString("\n") { tc -> "[Tool result: ${tc.name}]" },
                toolResultJson = toolResultBlocks.toString(),
                timestamp = System.currentTimeMillis()
            )
            messageDao.insertMessage(toolResultMsg)

            // Add tool results to conversation history and loop back to LLM
            conversationHistory.add(
                LlmMessage(role = "user", content = toolResultBlocks)
            )
        }

        // If we hit MAX_TOOL_LOOPS
        Log.w(TAG, "Hit max tool loop limit ($MAX_TOOL_LOOPS)")
        emit(AgentEvent.Error("Reached maximum tool call limit ($MAX_TOOL_LOOPS). Stopping."))
    }

    private fun createClient(apiKey: String, baseUrl: String): LlmClient {
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        return ClaudeClient(apiKey, okHttpClient, baseUrl)
    }
}

/** Internal data class for pending tool calls within a single LLM turn */
private data class PendingToolCall(
    val id: String,
    val name: String,
    val args: JsonObject
)
