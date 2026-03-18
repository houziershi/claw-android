package com.openclaw.agent.core.memory

import android.os.Build
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the system prompt context from memory files.
 * Called before every LLM request to inject relevant memory.
 */
@Singleton
class MemoryContextBuilder @Inject constructor(
    private val memoryStore: MemoryStore
) {

    /**
     * Build the full system prompt by combining:
     * 1. SOUL.md  — persona
     * 2. USER.md  — user profile
     * 3. MEMORY.md — long-term facts
     * 4. Recent daily notes
     * 5. Relevant memory snippets (keyword search)
     * 6. Current time + device info
     * 7. Active skill context (if matched)
     * 8. Memory usage instructions
     */
    suspend fun buildSystemPrompt(
        lastUserMessage: String = "",
        customPrompt: String? = null,
        skillContext: String? = null
    ): String = buildString {
        // Custom prompt override (if set in Settings)
        if (customPrompt != null) {
            appendLine(customPrompt)
            appendLine()
            return@buildString
        }

        // 1. Agent persona
        val soul = memoryStore.read("SOUL.md")
        if (!soul.isNullOrBlank()) {
            appendLine(soul)
            appendLine()
        }

        // 2. User profile
        val user = memoryStore.read("USER.md")
        if (!user.isNullOrBlank()) {
            appendLine("## User Profile")
            appendLine(user)
            appendLine()
        }

        // 3. Long-term memory
        val memory = memoryStore.read("MEMORY.md")
        if (!memory.isNullOrBlank()) {
            appendLine("## Long-term Memory")
            appendLine(memory)
            appendLine()
        }

        // 4. Recent daily notes (yesterday + today)
        val recentNotes = memoryStore.getRecentDailyNotes()
        if (recentNotes.isNotBlank()) {
            appendLine("## Recent Activity")
            appendLine(recentNotes)
            appendLine()
        }

        // 5. Relevant memory snippets (keyword search from user message)
        if (lastUserMessage.isNotBlank()) {
            val snippets = memoryStore.search(lastUserMessage, maxResults = 3)
            if (snippets.isNotEmpty()) {
                appendLine("## Relevant Memory")
                snippets.forEach { snippet ->
                    appendLine("- [${snippet.path}] ${snippet.content.take(200)}")
                }
                appendLine()
            }
        }

        // 6. Current time and device info
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        appendLine("## Current Context")
        appendLine("Time: ${now.format(formatter)}")
        appendLine("Device: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device Model: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine()

        // 7. Active skill context (if matched)
        if (!skillContext.isNullOrBlank()) {
            appendLine(skillContext)
            appendLine()
        }

        // 8. Memory usage instructions
        appendLine("## Memory Instructions")
        appendLine("You have memory tools to persist important information:")
        appendLine("- Use `memory_write` to save user preferences, important facts, or notes to MEMORY.md or USER.md")
        appendLine("- Use `memory_read` to recall stored information")
        appendLine("- Use `memory_search` to find relevant past information")
        appendLine("- Proactively remember: when the user shares preferences, their name, or important context, save it")
        appendLine("- Daily notes are auto-recorded; you don't need to write them manually")
    }

    /**
     * Build a shorter context for token-constrained situations.
     */
    suspend fun buildCompactPrompt(): String = buildString {
        val soul = memoryStore.read("SOUL.md")
        if (!soul.isNullOrBlank()) {
            appendLine(soul)
            appendLine()
        }
        val now = LocalDateTime.now()
        appendLine("Current time: ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}")
    }
}
