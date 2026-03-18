package com.openclaw.agent.core.memory

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FileMemoryStore"
private const val MEMORY_DIR = "memory"
private const val SOUL_FILE = "SOUL.md"
private const val USER_FILE = "USER.md"
private const val MEMORY_FILE = "MEMORY.md"
private const val DAILY_DIR = "daily"

@Singleton
class FileMemoryStore @Inject constructor(
    @ApplicationContext private val context: Context
) : MemoryStore {

    private val memoryRoot: File
        get() = File(context.filesDir, MEMORY_DIR)

    private fun resolveFile(path: String): File =
        File(memoryRoot, path).canonicalFile.also { file ->
            // Security: ensure the resolved path stays within memoryRoot
            require(file.path.startsWith(memoryRoot.canonicalPath)) {
                "Path traversal attempt: $path"
            }
        }

    override suspend fun read(path: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = resolveFile(path)
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read $path", e)
            null
        }
    }

    override suspend fun write(path: String, content: String) {
        withContext(Dispatchers.IO) {
            try {
                val file = resolveFile(path)
                file.parentFile?.mkdirs()
                file.writeText(content)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write $path", e)
            }
        }
    }

    override suspend fun append(path: String, line: String) {
        withContext(Dispatchers.IO) {
            try {
                val file = resolveFile(path)
                file.parentFile?.mkdirs()
                file.appendText(line + "\n")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to append to $path", e)
            }
        }
    }

    override suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            resolveFile(path).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete $path", e)
            false
        }
    }

    override suspend fun list(directory: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val dir = if (directory.isEmpty()) memoryRoot else resolveFile(directory)
            dir.walkTopDown()
                .filter { it.isFile }
                .map { it.relativeTo(memoryRoot).path }
                .toList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list $directory", e)
            emptyList()
        }
    }

    override suspend fun search(query: String, maxResults: Int): List<MemorySnippet> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()

            val keywords = query.lowercase().split(Regex("\\s+")).filter { it.length > 2 }
            val results = mutableListOf<MemorySnippet>()

            memoryRoot.walkTopDown()
                .filter { it.isFile && it.extension == "md" }
                .forEach { file ->
                    try {
                        val lines = file.readLines()
                        val relativePath = file.relativeTo(memoryRoot).path
                        lines.forEachIndexed { index, line ->
                            val lineLower = line.lowercase()
                            val matchCount = keywords.count { kw -> lineLower.contains(kw) }
                            if (matchCount > 0) {
                                // Include a window of context around the matching line
                                val start = maxOf(0, index - 1)
                                val end = minOf(lines.size - 1, index + 1)
                                val snippet = lines.subList(start, end + 1).joinToString("\n")
                                results.add(
                                    MemorySnippet(
                                        path = relativePath,
                                        content = snippet,
                                        relevance = matchCount.toFloat() / keywords.size
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error searching ${file.name}", e)
                    }
                }

            results.sortedByDescending { it.relevance }.take(maxResults)
        }

    override suspend fun getRecentDailyNotes(): String = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE

        val todayPath = "$DAILY_DIR/${today.format(formatter)}.md"
        val yesterdayPath = "$DAILY_DIR/${yesterday.format(formatter)}.md"

        val sb = StringBuilder()
        read(yesterdayPath)?.let {
            sb.appendLine("### ${yesterday.format(formatter)}")
            sb.appendLine(it)
            sb.appendLine()
        }
        read(todayPath)?.let {
            sb.appendLine("### ${today.format(formatter)}")
            sb.appendLine(it)
        }
        sb.toString()
    }

    override suspend fun appendToDailyNote(content: String) {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        val path = "$DAILY_DIR/${today.format(formatter)}.md"

        val existingContent = read(path)
        if (existingContent == null) {
            write(path, "# ${today.format(formatter)}\n\n$content\n")
        } else {
            append(path, "\n$content")
        }
    }

    /**
     * Ensure the default memory files exist with starter content.
     * Called once at app startup.
     */
    fun initializeDefaultFiles() {
        memoryRoot.mkdirs()
        File(memoryRoot, DAILY_DIR).mkdirs()

        val soulFile = resolveFile(SOUL_FILE)
        if (!soulFile.exists()) {
            soulFile.writeText(DEFAULT_SOUL)
        }

        val userFile = resolveFile(USER_FILE)
        if (!userFile.exists()) {
            userFile.writeText(DEFAULT_USER)
        }

        val memoryFile = resolveFile(MEMORY_FILE)
        if (!memoryFile.exists()) {
            memoryFile.writeText(DEFAULT_MEMORY)
        }
    }

    companion object {
        val DEFAULT_SOUL = """
# Soul

You are Claw, a helpful AI assistant running on Android.
You are curious, concise, and genuinely helpful.
You have access to various tools on this device and can use them to assist the user.
Always be honest about what you can and cannot do.
        """.trimIndent()

        val DEFAULT_USER = """
# User

Name: (not set)
Timezone: (auto-detected)
Preferences: (none yet)

Update this file as you learn more about the user.
        """.trimIndent()

        val DEFAULT_MEMORY = """
# Memory

This file contains important long-term memories.
Add key facts, preferences, and important information here.
        """.trimIndent()
    }
}
