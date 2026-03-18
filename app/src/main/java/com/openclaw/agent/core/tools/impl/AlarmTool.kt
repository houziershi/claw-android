package com.openclaw.agent.core.tools.impl

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.room.Room
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import com.openclaw.agent.data.db.AppDatabase
import com.openclaw.agent.data.db.entities.ScheduledTaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private const val TAG = "AlarmTool"
private const val CHANNEL_ID = "claw_alarm"
private const val CHANNEL_NAME = "Claw Alarms"

class AlarmTool(private val context: Context) : Tool {
    override val name = "alarm"
    override val description = """Manage alarms and scheduled tasks.
Actions:
- 'set': Set alarm. REQUIRED params: hour, minute, message. Optional: task_type ('simple'|'agent'), prompt (for agent), repeat ('once'|'daily'|'weekly'|'weekdays'), day_of_week (1-7 for weekly).
  Example: {"action":"set","hour":19,"minute":30,"message":"天气提醒","task_type":"agent","prompt":"查武汉天气","repeat":"daily"}
- 'list': Show all tasks
- 'delete': Cancel by id
- 'enable'/'disable': Toggle by id"""

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add("set"); add("list"); add("delete"); add("enable"); add("disable")
                })
                put("description", "Action to perform")
            }
            putJsonObject("hour") {
                put("type", "integer")
                put("description", "Hour 0-23. REQUIRED for 'set'.")
            }
            putJsonObject("minute") {
                put("type", "integer")
                put("description", "Minute 0-59. REQUIRED for 'set'.")
            }
            putJsonObject("message") {
                put("type", "string")
                put("description", "Alarm label. REQUIRED for 'set'.")
            }
            putJsonObject("task_type") {
                put("type", "string")
                put("enum", buildJsonArray { add("simple"); add("agent") })
                put("description", "simple=notify only, agent=run LLM prompt at trigger time. Default: simple")
            }
            putJsonObject("prompt") {
                put("type", "string")
                put("description", "LLM prompt to run when alarm fires. Required when task_type=agent.")
            }
            putJsonObject("repeat") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add("once"); add("daily"); add("weekly"); add("weekdays")
                })
                put("description", "Repeat mode. Default: once")
            }
            putJsonObject("day_of_week") {
                put("type", "integer")
                put("description", "1=Mon..7=Sun (for weekly)")
            }
            putJsonObject("id") {
                put("type", "string")
                put("description", "Task ID (for delete/enable/disable)")
            }
        }
        put("required", buildJsonArray {
            add(JsonPrimitive("action"))
        })
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm and scheduled task notifications"
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val action = args["action"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'action'"
        )
        return when (action) {
            "set" -> setTask(args)
            "list" -> listTasks()
            "delete" -> deleteTask(args)
            "enable" -> toggleTask(args, true)
            "disable" -> toggleTask(args, false)
            else -> ToolResult(success = false, content = "", errorMessage = "Unknown action: $action")
        }
    }

    private suspend fun setTask(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val hour = args["hour"]?.jsonPrimitive?.int ?: return@withContext ToolResult(
                success = false, content = "", errorMessage = "Missing required parameters. You MUST provide: hour (0-23), minute (0-59), message (string). For agent type also provide: prompt (string). Example: {\"action\":\"set\",\"hour\":19,\"minute\":30,\"message\":\"天气提醒\",\"task_type\":\"agent\",\"prompt\":\"查询武汉天气并给出穿衣建议\"}"
            )
            val minute = args["minute"]?.jsonPrimitive?.int ?: return@withContext ToolResult(
                success = false, content = "", errorMessage = "Missing 'minute' parameter. You MUST provide minute (0-59) along with hour."
            )
            val message = args["message"]?.jsonPrimitive?.content ?: "Claw 提醒"
            val taskType = args["task_type"]?.jsonPrimitive?.content ?: "simple"
            var prompt = args["prompt"]?.jsonPrimitive?.content
            val repeat = args["repeat"]?.jsonPrimitive?.content ?: "once"

            // Auto-infer prompt from message if agent type but no prompt provided
            if (taskType == "agent" && prompt.isNullOrBlank()) {
                prompt = message
                Log.d(TAG, "Auto-inferred prompt from message: $prompt")
            }
            val dayOfWeek = args["day_of_week"]?.jsonPrimitive?.intOrNull

            val id = UUID.randomUUID().toString().take(8)
            val nextRunAt = calculateNextRun(hour, minute, repeat, dayOfWeek)

            val task = ScheduledTaskEntity(
                id = id,
                hour = hour,
                minute = minute,
                type = taskType,
                message = message,
                prompt = if (taskType == "agent") prompt else null,
                repeat = repeat,
                dayOfWeek = dayOfWeek,
                enabled = true,
                createdAt = System.currentTimeMillis(),
                nextRunAt = nextRunAt
            )

            // Save to DB
            val db = getDb()
            db.scheduledTaskDao().insert(task)

            // Register alarm
            registerAlarm(task)

            // Also set system alarm for simple type
            if (taskType == "simple") {
                try {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, message)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "System alarm failed: ${e.message}")
                }
            }

            val timeStr = "%02d:%02d".format(hour, minute)
            val repeatStr = when (repeat) {
                "daily" -> "每天"
                "weekly" -> "每周${dayOfWeekStr(dayOfWeek)}"
                "weekdays" -> "工作日"
                else -> "一次性"
            }
            val typeStr = if (taskType == "agent") " [智能任务]" else ""

            ToolResult(
                success = true,
                content = "✅ 已设置$repeatStr $timeStr$typeStr — $message (ID: $id)"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set task", e)
            ToolResult(success = false, content = "", errorMessage = "Failed: ${e.message}")
        }
    }

    private suspend fun listTasks(): ToolResult = withContext(Dispatchers.IO) {
        try {
            val tasks = getDb().scheduledTaskDao().getAll()
            if (tasks.isEmpty()) {
                return@withContext ToolResult(success = true, content = "No scheduled tasks.")
            }

            val sb = StringBuilder("Scheduled tasks:\n")
            tasks.forEach { t ->
                val time = "%02d:%02d".format(t.hour, t.minute)
                val status = if (t.enabled) "✅" else "⏸️"
                val typeIcon = if (t.type == "agent") "🤖" else "⏰"
                val repeatStr = when (t.repeat) {
                    "daily" -> "每天"
                    "weekly" -> "每周${dayOfWeekStr(t.dayOfWeek)}"
                    "weekdays" -> "工作日"
                    else -> "一次"
                }
                val lastRun = if (t.lastRunAt != null) {
                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(t.lastRunAt))
                } else "从未"
                sb.appendLine("$status $typeIcon [$t.id] $time $repeatStr — ${t.message} (上次: $lastRun)")
            }
            ToolResult(success = true, content = sb.toString().trim())
        } catch (e: Exception) {
            ToolResult(success = false, content = "", errorMessage = "Failed: ${e.message}")
        }
    }

    private suspend fun deleteTask(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val id = args["id"]?.jsonPrimitive?.content ?: return@withContext ToolResult(
            success = false, content = "", errorMessage = "Missing 'id'"
        )
        try {
            val db = getDb()
            val task = db.scheduledTaskDao().getById(id)
            if (task != null) {
                cancelAlarm(task)
                db.scheduledTaskDao().deleteById(id)
                ToolResult(success = true, content = "✅ Task $id deleted")
            } else {
                ToolResult(success = false, content = "", errorMessage = "Task $id not found")
            }
        } catch (e: Exception) {
            ToolResult(success = false, content = "", errorMessage = "Failed: ${e.message}")
        }
    }

    private suspend fun toggleTask(args: JsonObject, enabled: Boolean): ToolResult = withContext(Dispatchers.IO) {
        val id = args["id"]?.jsonPrimitive?.content ?: return@withContext ToolResult(
            success = false, content = "", errorMessage = "Missing 'id'"
        )
        try {
            val db = getDb()
            val task = db.scheduledTaskDao().getById(id) ?: return@withContext ToolResult(
                success = false, content = "", errorMessage = "Task $id not found"
            )
            db.scheduledTaskDao().setEnabled(id, enabled)
            if (enabled) {
                val updated = task.copy(enabled = true, nextRunAt = calculateNextRun(task.hour, task.minute, task.repeat, task.dayOfWeek))
                db.scheduledTaskDao().update(updated)
                registerAlarm(updated)
            } else {
                cancelAlarm(task)
            }
            val action = if (enabled) "enabled" else "disabled"
            ToolResult(success = true, content = "✅ Task $id $action")
        } catch (e: Exception) {
            ToolResult(success = false, content = "", errorMessage = "Failed: ${e.message}")
        }
    }

    // ── Alarm Registration ──

    fun registerAlarm(task: ScheduledTaskEntity) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("task_id", task.id)
            putExtra("message", task.message)
            putExtra("type", task.type)
            putExtra("prompt", task.prompt ?: "")
            putExtra("repeat", task.repeat)
            putExtra("hour", task.hour)
            putExtra("minute", task.minute)
            putExtra("day_of_week", task.dayOfWeek ?: -1)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, task.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.nextRunAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.nextRunAt, pendingIntent)
            }
            Log.d(TAG, "Alarm registered: ${task.id} at ${Date(task.nextRunAt)}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register alarm: ${task.id}", e)
        }
    }

    private fun cancelAlarm(task: ScheduledTaskEntity) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, task.id.hashCode(), intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Alarm cancelled: ${task.id}")
        }
    }

    private fun getDb(): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()
    }

    companion object {
        fun calculateNextRun(hour: Int, minute: Int, repeat: String, dayOfWeek: Int?): Long {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            when (repeat) {
                "weekly" -> {
                    val targetDay = when (dayOfWeek) {
                        1 -> Calendar.MONDAY; 2 -> Calendar.TUESDAY; 3 -> Calendar.WEDNESDAY
                        4 -> Calendar.THURSDAY; 5 -> Calendar.FRIDAY; 6 -> Calendar.SATURDAY
                        7 -> Calendar.SUNDAY; else -> Calendar.MONDAY
                    }
                    cal.set(Calendar.DAY_OF_WEEK, targetDay)
                    if (cal.timeInMillis <= System.currentTimeMillis()) {
                        cal.add(Calendar.WEEK_OF_YEAR, 1)
                    }
                }
                "weekdays" -> {
                    if (cal.timeInMillis <= System.currentTimeMillis()) {
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                        cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                else -> { // once, daily
                    if (cal.timeInMillis <= System.currentTimeMillis()) {
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
            }
            return cal.timeInMillis
        }

        fun dayOfWeekStr(day: Int?): String = when (day) {
            1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"
            5 -> "五"; 6 -> "六"; 7 -> "日"; else -> ""
        }
    }
}

/**
 * BroadcastReceiver — handles both simple alarms and agent tasks.
 * For agent tasks: sends fallback notification immediately, then enqueues LLM work.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("task_id") ?: ""
        val message = intent.getStringExtra("message") ?: "Claw 提醒"
        val type = intent.getStringExtra("type") ?: "simple"
        val prompt = intent.getStringExtra("prompt") ?: ""
        val repeat = intent.getStringExtra("repeat") ?: "once"
        val hour = intent.getIntExtra("hour", 0)
        val minute = intent.getIntExtra("minute", 0)
        val dayOfWeek = intent.getIntExtra("day_of_week", -1)

        Log.d(TAG, "Alarm fired: id=$taskId type=$type message=$message")

        // Ensure notification channel exists
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply { enableVibration(true) }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val notifId = taskId.hashCode()

        if (type == "agent" && prompt.isNotBlank()) {
            // 1. Send fallback notification immediately (guaranteed)
            showNotification(context, notifId, "⏰ $message", "正在执行智能任务...")

            // 2. Enqueue LLM work in background
            val workData = androidx.work.Data.Builder()
                .putString("task_id", taskId)
                .putString("prompt", prompt)
                .putString("message", message)
                .putInt("notif_id", notifId)
                .build()

            val workRequest = androidx.work.OneTimeWorkRequestBuilder<AgentTaskWorker>()
                .setInputData(workData)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .build()
            androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
        } else {
            // Simple alarm — just notify
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ $message")
                .setContentText("%02d:%02d".format(hour, minute))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSound(alarmSound)
                .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
                .setAutoCancel(true)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(notifId, notification)
        }

        // 3. Schedule next run for repeating tasks
        if (repeat != "once" && taskId.isNotBlank()) {
            val dow = if (dayOfWeek >= 1) dayOfWeek else null
            val nextRun = AlarmTool.calculateNextRun(hour, minute, repeat, dow)

            // Re-register alarm for next occurrence
            val nextIntent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("task_id", taskId)
                putExtra("message", message)
                putExtra("type", type)
                putExtra("prompt", prompt)
                putExtra("repeat", repeat)
                putExtra("hour", hour)
                putExtra("minute", minute)
                putExtra("day_of_week", dayOfWeek)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, taskId.hashCode(), nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRun, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRun, pendingIntent)
                }
                Log.d(TAG, "Next alarm scheduled: $taskId at ${Date(nextRun)}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule next alarm", e)
            }

            // Update DB (async via thread since we're in BroadcastReceiver)
            Thread {
                try {
                    val db = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
                        .fallbackToDestructiveMigration().build()
                    db.scheduledTaskDao().let { dao ->
                        kotlinx.coroutines.runBlocking {
                            dao.updateRunTime(taskId, System.currentTimeMillis(), nextRun)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update task run time", e)
                }
            }.start()
        }
    }

    private fun showNotification(context: Context, id: Int, title: String, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, notification)
    }
}

/**
 * Boot receiver — re-registers all enabled alarms after device reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d(TAG, "Boot completed — restoring alarms")

        Thread {
            try {
                val db = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
                    .fallbackToDestructiveMigration().build()
                val tasks = kotlinx.coroutines.runBlocking {
                    db.scheduledTaskDao().getAllEnabledSync()
                }
                val tool = AlarmTool(context)
                tasks.forEach { task ->
                    // Recalculate next run if it's in the past
                    val nextRun = if (task.nextRunAt <= System.currentTimeMillis()) {
                        AlarmTool.calculateNextRun(task.hour, task.minute, task.repeat, task.dayOfWeek)
                    } else {
                        task.nextRunAt
                    }
                    val updated = task.copy(nextRunAt = nextRun)
                    kotlinx.coroutines.runBlocking {
                        db.scheduledTaskDao().update(updated)
                    }
                    tool.registerAlarm(updated)
                }
                Log.d(TAG, "Restored ${tasks.size} alarms after boot")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore alarms after boot", e)
            }
        }.start()
    }
}
