package com.openclaw.agent.core.tools.impl

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ScreenCaptureTool"

class ScreenCaptureTool(private val context: Context) : Tool {
    override val name = "screenshot"
    override val description = "Screen capture utility. Actions: 'take' (guide user to take a screenshot), 'recent' (list recent screenshots from gallery, param: limit)."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add(JsonPrimitive("take")); add(JsonPrimitive("recent")) })
                put("description", "Action: take or recent")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Number of recent screenshots to list (default 5, only for 'recent' action)")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("action")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val action = args["action"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'action' parameter"
        )

        return when (action) {
            "take" -> handleTake()
            "recent" -> {
                val limit = args["limit"]?.jsonPrimitive?.intOrNull ?: 5
                handleRecent(limit)
            }
            else -> ToolResult(
                success = false, content = "", errorMessage = "Unknown action: $action. Use take or recent."
            )
        }
    }

    private fun handleTake(): ToolResult {
        return try {
            val sb = StringBuilder()
            sb.appendLine("📸 Screenshot Guide")
            sb.appendLine("─────────────────────")
            sb.appendLine()
            sb.appendLine("Android security restrictions prevent apps from taking screenshots directly.")
            sb.appendLine()
            sb.appendLine("How to take a screenshot:")
            sb.appendLine("  • Press Power + Volume Down buttons simultaneously")
            sb.appendLine("  • Or swipe down the notification bar and tap the screenshot tile")
            sb.appendLine("  • Or use your device's gesture shortcut (e.g., three-finger swipe down)")
            sb.appendLine()
            sb.appendLine("The screenshot will be saved to your gallery automatically.")
            sb.appendLine("After taking a screenshot, use the 'recent' action to list it.")

            Log.d(TAG, "Provided screenshot guidance to user")
            ToolResult(success = true, content = sb.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle take action", e)
            ToolResult(success = false, content = "", errorMessage = "Failed: ${e.message}")
        }
    }

    private suspend fun handleRecent(limit: Int): ToolResult = withContext(Dispatchers.IO) {
        try {
            // Check permission
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                android.Manifest.permission.READ_MEDIA_IMAGES
            } else {
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            }
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return@withContext ToolResult(
                    success = false,
                    content = "",
                    errorMessage = "Missing permission: $permission. Please grant storage/media access first."
                )
            }

            val screenshots = queryRecentScreenshots(limit.coerceIn(1, 50))

            if (screenshots.isEmpty()) {
                return@withContext ToolResult(success = true, content = "No screenshots found on this device.")
            }

            val sb = StringBuilder()
            sb.appendLine("📸 Recent Screenshots (${screenshots.size})")
            sb.appendLine("─────────────────────")
            screenshots.forEachIndexed { index, info ->
                sb.appendLine()
                sb.appendLine("${index + 1}. ${info.displayName}")
                sb.appendLine("   Size: ${formatFileSize(info.size)}")
                sb.appendLine("   Date: ${info.dateFormatted}")
                if (info.width > 0 && info.height > 0) {
                    sb.appendLine("   Resolution: ${info.width} × ${info.height}")
                }
                sb.appendLine("   URI: ${info.uri}")
            }

            Log.d(TAG, "Listed ${screenshots.size} recent screenshots")
            ToolResult(success = true, content = sb.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query recent screenshots", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to list screenshots: ${e.message}")
        }
    }

    private fun queryRecentScreenshots(limit: Int): List<ScreenshotInfo> {
        val results = mutableListOf<ScreenshotInfo>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATA // file path for filtering
        )

        // Filter for screenshots by path
        val selection = "${MediaStore.Images.Media.DATA} LIKE ? OR ${MediaStore.Images.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("%screenshot%", "%Screenshots%")

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val id = cursor.getLong(idColumn)
                val displayName = cursor.getString(nameColumn) ?: "unknown"
                val size = cursor.getLong(sizeColumn)
                val dateAdded = cursor.getLong(dateColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )

                results.add(
                    ScreenshotInfo(
                        displayName = displayName,
                        size = size,
                        dateFormatted = dateFormat.format(Date(dateAdded * 1000)),
                        width = width,
                        height = height,
                        uri = uri.toString()
                    )
                )
                count++
            }
        }

        return results
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }

    private data class ScreenshotInfo(
        val displayName: String,
        val size: Long,
        val dateFormatted: String,
        val width: Int,
        val height: Int,
        val uri: String
    )
}
