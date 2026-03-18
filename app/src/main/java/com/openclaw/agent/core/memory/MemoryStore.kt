package com.openclaw.agent.core.memory

/**
 * Abstraction for reading and writing memory files.
 * Memory files live under the app's internal storage: files/memory/
 *
 * Standard files:
 *   SOUL.md      — agent persona / character
 *   USER.md      — user profile and preferences
 *   MEMORY.md    — long-term memory snippets
 *   daily/       — daily notes (YYYY-MM-DD.md)
 */
interface MemoryStore {

    /** Read the content of a memory file. Returns null if the file doesn't exist. */
    suspend fun read(path: String): String?

    /** Write or overwrite a memory file. Creates parent directories as needed. */
    suspend fun write(path: String, content: String)

    /** Append a line to a memory file. Creates the file if it doesn't exist. */
    suspend fun append(path: String, line: String)

    /** Delete a memory file. Returns true if deleted, false if it didn't exist. */
    suspend fun delete(path: String): Boolean

    /** List all files in a directory relative to the memory root. */
    suspend fun list(directory: String = ""): List<String>

    /**
     * Keyword-based search across all memory files.
     * Returns matching snippets with their source path.
     */
    suspend fun search(query: String, maxResults: Int = 5): List<MemorySnippet>

    /** Get the combined text of today's and yesterday's daily notes. */
    suspend fun getRecentDailyNotes(): String

    /**
     * Append a summary to today's daily note.
     * Creates the file with a date header if it doesn't exist.
     */
    suspend fun appendToDailyNote(content: String)
}

data class MemorySnippet(
    val path: String,
    val content: String,
    val relevance: Float = 1.0f
)
