package com.openclaw.agent.core.tools.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val TAG = "MediaTool"
private const val MAX_RESULTS = 20

class MediaTool(private val context: Context) : Tool {
    override val name = "media"
    override val description = "Query media files on the device. Actions: 'photos' (list photos, optional date filter), 'videos' (list videos, optional date filter), 'music' (search music files), 'stats' (media file statistics)."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add(JsonPrimitive("photos"))
                    add(JsonPrimitive("videos"))
                    add(JsonPrimitive("music"))
                    add(JsonPrimitive("stats"))
                })
                put("description", "Action: photos, videos, music, or stats")
            }
            putJsonObject("date") {
                put("type", "string")
                put("description", "Filter by date (yyyy-MM-dd). Used by 'photos' and 'videos' actions.")
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "Search query for music (matches title or artist). Used by 'music' action.")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Max number of results to return. Default 10, max $MAX_RESULTS.")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("action")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        if (!checkPermissions()) {
            return ToolResult(
                success = false,
                content = "",
                errorMessage = "Media permissions not granted. Please grant storage/media permissions in system settings."
            )
        }

        val action = args["action"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'action' parameter"
        )

        return when (action) {
            "photos" -> {
                val date = args["date"]?.jsonPrimitive?.content
                val limit = (args["limit"]?.jsonPrimitive?.intOrNull ?: 10).coerceIn(1, MAX_RESULTS)
                queryPhotos(date, limit)
            }
            "videos" -> {
                val date = args["date"]?.jsonPrimitive?.content
                val limit = (args["limit"]?.jsonPrimitive?.intOrNull ?: 10).coerceIn(1, MAX_RESULTS)
                queryVideos(date, limit)
            }
            "music" -> {
                val query = args["query"]?.jsonPrimitive?.content
                val limit = (args["limit"]?.jsonPrimitive?.intOrNull ?: 10).coerceIn(1, MAX_RESULTS)
                queryMusic(query, limit)
            }
            "stats" -> queryStats()
            else -> ToolResult(
                success = false, content = "", errorMessage = "Unknown action: $action. Use photos, videos, music, or stats."
            )
        }
    }

    private fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun parseDateRange(dateStr: String): Pair<Long, Long>? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(dateStr) ?: return null
            val cal = Calendar.getInstance().apply { time = date }
            val startMs = cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
            val endMs = cal.timeInMillis
            startMs to endMs
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun queryPhotos(date: String?, limit: Int): ToolResult = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.DATA
            )

            var selection: String? = null
            var selectionArgs: Array<String>? = null

            if (date != null) {
                val range = parseDateRange(date)
                if (range != null) {
                    selection = "${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} < ?"
                    selectionArgs = arrayOf(range.first.toString(), range.second.toString())
                } else {
                    return@withContext ToolResult(
                        success = false, content = "", errorMessage = "Invalid date format. Use yyyy-MM-dd."
                    )
                }
            }

            val photos = mutableListOf<String>()
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val dateIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val widthIdx = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightIdx = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val dataIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val name = cursor.getString(nameIdx) ?: "Unknown"
                    val size = cursor.getLong(sizeIdx)
                    val dateTaken = cursor.getLong(dateIdx)
                    val width = cursor.getInt(widthIdx)
                    val height = cursor.getInt(heightIdx)
                    val path = cursor.getString(dataIdx) ?: ""

                    val dateFormatted = formatDate(dateTaken)
                    val sizeFormatted = formatSize(size)

                    photos.add("• $name | ${width}x${height} | $sizeFormatted | $dateFormatted\n  Path: $path")
                    count++
                }
            }

            if (photos.isEmpty()) {
                val dateInfo = if (date != null) " for date $date" else ""
                ToolResult(success = true, content = "No photos found$dateInfo.")
            } else {
                val dateInfo = if (date != null) " (date: $date)" else ""
                ToolResult(
                    success = true,
                    content = "Photos$dateInfo (${photos.size}):\n\n${photos.joinToString("\n")}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Query photos failed", e)
            ToolResult(success = false, content = "", errorMessage = "Query photos failed: ${e.message}")
        }
    }

    private suspend fun queryVideos(date: String?, limit: Int): ToolResult = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_TAKEN,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT
            )

            var selection: String? = null
            var selectionArgs: Array<String>? = null

            if (date != null) {
                val range = parseDateRange(date)
                if (range != null) {
                    selection = "${MediaStore.Video.Media.DATE_TAKEN} >= ? AND ${MediaStore.Video.Media.DATE_TAKEN} < ?"
                    selectionArgs = arrayOf(range.first.toString(), range.second.toString())
                } else {
                    return@withContext ToolResult(
                        success = false, content = "", errorMessage = "Invalid date format. Use yyyy-MM-dd."
                    )
                }
            }

            val videos = mutableListOf<String>()
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Video.Media.DATE_TAKEN} DESC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val dateIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN)
                val durationIdx = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val widthIdx = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightIdx = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val name = cursor.getString(nameIdx) ?: "Unknown"
                    val size = cursor.getLong(sizeIdx)
                    val dateTaken = cursor.getLong(dateIdx)
                    val duration = cursor.getLong(durationIdx)
                    val width = cursor.getInt(widthIdx)
                    val height = cursor.getInt(heightIdx)

                    val dateFormatted = formatDate(dateTaken)
                    val sizeFormatted = formatSize(size)
                    val durationFormatted = formatDuration(duration)

                    videos.add("• $name | ${width}x${height} | $durationFormatted | $sizeFormatted | $dateFormatted")
                    count++
                }
            }

            if (videos.isEmpty()) {
                val dateInfo = if (date != null) " for date $date" else ""
                ToolResult(success = true, content = "No videos found$dateInfo.")
            } else {
                val dateInfo = if (date != null) " (date: $date)" else ""
                ToolResult(
                    success = true,
                    content = "Videos$dateInfo (${videos.size}):\n\n${videos.joinToString("\n")}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Query videos failed", e)
            ToolResult(success = false, content = "", errorMessage = "Query videos failed: ${e.message}")
        }
    }

    private suspend fun queryMusic(query: String?, limit: Int): ToolResult = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.TITLE
            )

            // Only query music files (not ringtones, notifications, etc.)
            var selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val selectionArgsList = mutableListOf<String>()

            if (!query.isNullOrBlank()) {
                selection += " AND (${MediaStore.Audio.Media.TITLE} LIKE ? OR ${MediaStore.Audio.Media.ARTIST} LIKE ?)"
                selectionArgsList.add("%$query%")
                selectionArgsList.add("%$query%")
            }

            val selectionArgs = if (selectionArgsList.isNotEmpty()) selectionArgsList.toTypedArray() else null

            val songs = mutableListOf<String>()
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val durationIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val artistIdx = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumIdx = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val titleIdx = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val displayName = cursor.getString(nameIdx) ?: "Unknown"
                    val size = cursor.getLong(sizeIdx)
                    val duration = cursor.getLong(durationIdx)
                    val artist = cursor.getString(artistIdx) ?: "Unknown"
                    val album = cursor.getString(albumIdx) ?: "Unknown"
                    val title = cursor.getString(titleIdx) ?: displayName

                    val sizeFormatted = formatSize(size)
                    val durationFormatted = formatDuration(duration)

                    songs.add("• $title — $artist | Album: $album | $durationFormatted | $sizeFormatted")
                    count++
                }
            }

            if (songs.isEmpty()) {
                val queryInfo = if (!query.isNullOrBlank()) " matching \"$query\"" else ""
                ToolResult(success = true, content = "No music found$queryInfo.")
            } else {
                val queryInfo = if (!query.isNullOrBlank()) " matching \"$query\"" else ""
                ToolResult(
                    success = true,
                    content = "Music$queryInfo (${songs.size}):\n\n${songs.joinToString("\n")}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Query music failed", e)
            ToolResult(success = false, content = "", errorMessage = "Query music failed: ${e.message}")
        }
    }

    private suspend fun queryStats(): ToolResult = withContext(Dispatchers.IO) {
        try {
            val photoStats = queryMediaStats(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            val videoStats = queryMediaStats(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            val musicStats = queryMediaStats(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, musicOnly = true)

            val totalCount = photoStats.first + videoStats.first + musicStats.first
            val totalSize = photoStats.second + videoStats.second + musicStats.second

            val sb = StringBuilder()
            sb.appendLine("## Media Statistics")
            sb.appendLine()
            sb.appendLine("| Type   | Count | Size |")
            sb.appendLine("|--------|-------|------|")
            sb.appendLine("| Photos | ${photoStats.first} | ${formatSize(photoStats.second)} |")
            sb.appendLine("| Videos | ${videoStats.first} | ${formatSize(videoStats.second)} |")
            sb.appendLine("| Music  | ${musicStats.first} | ${formatSize(musicStats.second)} |")
            sb.appendLine("|--------|-------|------|")
            sb.appendLine("| **Total** | **$totalCount** | **${formatSize(totalSize)}** |")

            ToolResult(success = true, content = sb.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Query stats failed", e)
            ToolResult(success = false, content = "", errorMessage = "Query stats failed: ${e.message}")
        }
    }

    private fun queryMediaStats(
        uri: android.net.Uri,
        musicOnly: Boolean = false
    ): Pair<Long, Long> {
        val selection = if (musicOnly) "${MediaStore.Audio.Media.IS_MUSIC} != 0" else null

        var count = 0L
        var totalSize = 0L

        context.contentResolver.query(
            uri,
            arrayOf("COUNT(*) AS count", "SUM(${MediaStore.MediaColumns.SIZE}) AS total_size"),
            selection,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                count = cursor.getLong(0)
                totalSize = cursor.getLong(1)
            }
        }

        return count to totalSize
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        return when {
            bytes >= 1L * 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024))
            bytes >= 1L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024))
            bytes >= 1024L -> String.format(Locale.getDefault(), "%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }

    private fun formatDate(timestampMs: Long): String {
        if (timestampMs <= 0) return "Unknown"
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(timestampMs)
    }
}
