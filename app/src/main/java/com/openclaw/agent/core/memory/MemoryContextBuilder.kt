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
     * 3. Recent daily notes
     * 4. Relevant MEMORY.md snippets (keyword search)
     * 5. Current time + device info
     */
    suspend fun buildSystemPrompt(
        lastUserMessage: String = "",
        customPrompt: String? = null
    ): String = buildString {
        // 1. Custom prompt override (if set in Settings)
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

        // 3. Long-term memory (keyword-relevant snippets)
        if (lastUserMessage.isNotBlank()) {
            val snippets = memoryStore.search(lastUserMessage, maxResults = 5)
            if (snippets.isNotEmpty()) {
                appendLine("## Relevant Memory")
                snippets.forEach { snippet ->
                    appendLine("- [${snippet.path}] ${snippet.content.take(200)}")
                }
                appendLine()
            }
        }

        // 4. Recent daily notes (yesterday + today)
        val recentNotes = memoryStore.getRecentDailyNotes()
        if (recentNotes.isNotBlank()) {
            appendLine("## Recent Context")
            appendLine(recentNotes)
            appendLine()
        }

        // 5. Current time and device info
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        appendLine("## Current Context")
        appendLine("Time: ${now.format(formatter)}")
        appendLine("Device: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device Model: ${Build.MANUFACTURER} ${Build.MODEL}")
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
