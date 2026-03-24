package com.openclaw.agent.core.tools.impl

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "CalendarTool"

class CalendarTool(private val context: Context) : Tool {
    override val name = "calendar"
    override val description = """Manage device calendar events.
Actions:
- 'today': Show all events for today. No extra params needed.
- 'query': Query events in a date range. Params: start_date (yyyy-MM-dd, required), end_date (yyyy-MM-dd, required), keyword (optional, filter by title).
- 'create': Create a new event. Params: title (required), start_time (yyyy-MM-dd'T'HH:mm:ss, required), end_time (optional, default start+1h), description (optional), location (optional), reminder_minutes (optional, default 15).
- 'delete': Delete an event by ID. Params: event_id (required)."""

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add("today"); add("query"); add("create"); add("delete")
                })
                put("description", "Action to perform")
            }
            putJsonObject("start_date") {
                put("type", "string")
                put("description", "Start date in yyyy-MM-dd format (for 'query')")
            }
            putJsonObject("end_date") {
                put("type", "string")
                put("description", "End date in yyyy-MM-dd format (for 'query')")
            }
            putJsonObject("keyword") {
                put("type", "string")
                put("description", "Optional keyword to filter events by title (for 'query')")
            }
            putJsonObject("title") {
                put("type", "string")
                put("description", "Event title (required for 'create')")
            }
            putJsonObject("start_time") {
                put("type", "string")
                put("description", "Event start time in yyyy-MM-dd'T'HH:mm:ss format (required for 'create')")
            }
            putJsonObject("end_time") {
                put("type", "string")
                put("description", "Event end time in yyyy-MM-dd'T'HH:mm:ss format (optional, default start+1h)")
            }
            putJsonObject("description") {
                put("type", "string")
                put("description", "Event description (optional for 'create')")
            }
            putJsonObject("location") {
                put("type", "string")
                put("description", "Event location (optional for 'create')")
            }
            putJsonObject("reminder_minutes") {
                put("type", "integer")
                put("description", "Reminder before event in minutes (optional, default 15)")
            }
            putJsonObject("event_id") {
                put("type", "string")
                put("description", "Event ID to delete (required for 'delete')")
            }
        }
        put("required", buildJsonArray {
            add(JsonPrimitive("action"))
        })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val action = args["action"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'action' parameter"
        )
        return when (action) {
            "today" -> queryToday()
            "query" -> queryRange(args)
            "create" -> createEvent(args)
            "delete" -> deleteEvent(args)
            else -> ToolResult(
                success = false, content = "",
                errorMessage = "Unknown action: $action. Use 'today', 'query', 'create', or 'delete'."
            )
        }
    }

    // ── Permission Check ──

    private fun hasReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    // ── Today ──

    private suspend fun queryToday(): ToolResult = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) {
            return@withContext ToolResult(
                success = false, content = "",
                errorMessage = "READ_CALENDAR permission not granted. Please grant calendar permission in system settings."
            )
        }
        try {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startMillis = cal.timeInMillis

            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val endMillis = cal.timeInMillis

            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val events = queryInstances(startMillis, endMillis, null)
            if (events.isEmpty()) {
                ToolResult(success = true, content = "📅 今天 ($dateStr) 没有日程事件。")
            } else {
                val sb = StringBuilder("📅 今天 ($dateStr) 共 ${events.size} 个事件：\n")
                events.forEach { sb.appendLine(it) }
                ToolResult(success = true, content = sb.toString().trim())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query today's events", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to query today's events: ${e.message}")
        }
    }

    // ── Query Range ──

    private suspend fun queryRange(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) {
            return@withContext ToolResult(
                success = false, content = "",
                errorMessage = "READ_CALENDAR permission not granted. Please grant calendar permission in system settings."
            )
        }
        try {
            val startDateStr = args["start_date"]?.jsonPrimitive?.content
                ?: return@withContext ToolResult(
                    success = false, content = "", errorMessage = "Missing 'start_date' parameter (yyyy-MM-dd)"
                )
            val endDateStr = args["end_date"]?.jsonPrimitive?.content
                ?: return@withContext ToolResult(
                    success = false, content = "", errorMessage = "Missing 'end_date' parameter (yyyy-MM-dd)"
                )
            val keyword = args["keyword"]?.jsonPrimitive?.content

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val startDate = dateFormat.parse(startDateStr)
                ?: return@withContext ToolResult(
                    success = false, content = "", errorMessage = "Invalid start_date format. Use yyyy-MM-dd."
                )
            val endDate = dateFormat.parse(endDateStr)
                ?: return@withContext ToolResult(
                    success = false, content = "", errorMessage = "Invalid end_date format. Use yyyy-MM-dd."
                )

            val startCal = Calendar.getInstance().apply {
                time = startDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val endCal = Calendar.getInstance().apply {
                time = endDate
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }

            val events = queryInstances(startCal.timeInMillis, endCal.timeInMillis, keyword)
            if (events.isEmpty()) {
                val keywordHint = if (keyword != null) " (关键词: $keyword)" else ""
                ToolResult(
                    success = true,
                    content = "📅 $startDateStr ~ $endDateStr 期间没有找到事件$keywordHint。"
                )
            } else {
                val keywordHint = if (keyword != null) " (关键词: $keyword)" else ""
                val sb = StringBuilder("📅 $startDateStr ~ $endDateStr 共 ${events.size} 个事件$keywordHint：\n")
                events.forEach { sb.appendLine(it) }
                ToolResult(success = true, content = sb.toString().trim())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query events", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to query events: ${e.message}")
        }
    }

    // ── Query Instances Helper ──

    private fun queryInstances(startMillis: Long, endMillis: Long, keyword: String?): List<String> {
        val events = mutableListOf<String>()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.ALL_DAY
        )

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                builder.build(), projection, null, null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )
            cursor?.let {
                val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                while (it.moveToNext()) {
                    val eventId = it.getLong(0)
                    val title = it.getString(1) ?: "(无标题)"
                    val begin = it.getLong(2)
                    val end = it.getLong(3)
                    val location = it.getString(4) ?: ""
                    val description = it.getString(5) ?: ""
                    val allDay = it.getInt(6) == 1

                    // Keyword filter
                    if (keyword != null && !title.contains(keyword, ignoreCase = true)) {
                        continue
                    }

                    val sb = StringBuilder()
                    sb.append("• [ID:$eventId] ")
                    if (allDay) {
                        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        sb.append("全天 (${dayFormat.format(Date(begin))})")
                    } else {
                        sb.append("${timeFormat.format(Date(begin))} ~ ${timeFormat.format(Date(end))}")
                    }
                    sb.append(" | $title")
                    if (location.isNotBlank()) sb.append(" | 📍$location")
                    if (description.isNotBlank()) sb.append(" | 📝${description.take(100)}")
                    events.add(sb.toString())
                }
            }
        } finally {
            cursor?.close()
        }
        return events
    }

    // ── Create Event ──

    private suspend fun createEvent(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) {
            return@withContext ToolResult(
                success = false, content = "",
                errorMessage = "WRITE_CALENDAR permission not granted. Please grant calendar permission in system settings."
            )
        }
        try {
            val title = args["title"]?.jsonPrimitive?.content
                ?: return@withContext ToolResult(
                    success = false, content = "", errorMessage = "Missing 'title' parameter"
                )
            val startTimeStr = args["start_time"]?.jsonPrimitive?.content
                ?: return@withContext ToolResult(
                    success = false, content = "",
                    errorMessage = "Missing 'start_time' parameter (yyyy-MM-dd'T'HH:mm:ss)"
                )
            val endTimeStr = args["end_time"]?.jsonPrimitive?.content
            val description = args["description"]?.jsonPrimitive?.content
            val location = args["location"]?.jsonPrimitive?.content
            val reminderMinutes = args["reminder_minutes"]?.jsonPrimitive?.intOrNull ?: 15

            val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val startTime = dateTimeFormat.parse(startTimeStr)
                ?: return@withContext ToolResult(
                    success = false, content = "",
                    errorMessage = "Invalid start_time format. Use yyyy-MM-dd'T'HH:mm:ss."
                )
            val startMillis = startTime.time

            val endMillis = if (endTimeStr != null) {
                val endTime = dateTimeFormat.parse(endTimeStr)
                    ?: return@withContext ToolResult(
                        success = false, content = "",
                        errorMessage = "Invalid end_time format. Use yyyy-MM-dd'T'HH:mm:ss."
                    )
                endTime.time
            } else {
                startMillis + 60 * 60 * 1000 // default +1 hour
            }

            // Find default calendar ID
            val calendarId = getDefaultCalendarId()
                ?: return@withContext ToolResult(
                    success = false, content = "",
                    errorMessage = "No writable calendar found on this device. Please add a calendar account first."
                )

            // Insert event
            val eventValues = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                if (description != null) put(CalendarContract.Events.DESCRIPTION, description)
                if (location != null) put(CalendarContract.Events.EVENT_LOCATION, location)
                put(CalendarContract.Events.HAS_ALARM, 1)
            }

            val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, eventValues)
                ?: return@withContext ToolResult(
                    success = false, content = "", errorMessage = "Failed to insert calendar event."
                )
            val eventId = ContentUris.parseId(eventUri)

            // Insert reminder
            val reminderValues = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, reminderMinutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)

            val displayFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val sb = StringBuilder("✅ 日程已创建：\n")
            sb.appendLine("• ID: $eventId")
            sb.appendLine("• 标题: $title")
            sb.appendLine("• 时间: ${displayFormat.format(Date(startMillis))} ~ ${displayFormat.format(Date(endMillis))}")
            if (location != null) sb.appendLine("• 地点: $location")
            if (description != null) sb.appendLine("• 描述: $description")
            sb.appendLine("• 提醒: 提前${reminderMinutes}分钟")

            ToolResult(success = true, content = sb.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create event", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to create event: ${e.message}")
        }
    }

    // ── Delete Event ──

    private suspend fun deleteEvent(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) {
            return@withContext ToolResult(
                success = false, content = "",
                errorMessage = "WRITE_CALENDAR permission not granted. Please grant calendar permission in system settings."
            )
        }
        try {
            val eventIdStr = args["event_id"]?.jsonPrimitive?.content
                ?: return@withContext ToolResult(
                    success = false, content = "", errorMessage = "Missing 'event_id' parameter"
                )
            val eventId = eventIdStr.toLongOrNull()
                ?: return@withContext ToolResult(
                    success = false, content = "", errorMessage = "Invalid event_id: must be a numeric ID."
                )

            val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val rowsDeleted = context.contentResolver.delete(deleteUri, null, null)

            if (rowsDeleted > 0) {
                ToolResult(success = true, content = "✅ 已删除事件 (ID: $eventId)")
            } else {
                ToolResult(
                    success = false, content = "",
                    errorMessage = "Event not found or already deleted (ID: $eventId)"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete event", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to delete event: ${e.message}")
        }
    }

    // ── Helper: Get Default Calendar ID ──

    private fun getDefaultCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection, selection, selectionArgs, null
            )
            cursor?.let {
                var firstWritableId: Long? = null
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val isPrimary = it.getInt(1) == 1
                    if (isPrimary) {
                        return id
                    }
                    if (firstWritableId == null) {
                        firstWritableId = id
                    }
                }
                return firstWritableId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query calendars", e)
        } finally {
            cursor?.close()
        }
        return null
    }
}
