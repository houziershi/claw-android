package com.openclaw.agent.core.tools.impl

import android.content.Context
import android.media.AudioManager
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class VolumeTool(private val context: Context) : Tool {
    override val name = "volume"
    override val description = "Get or set the device volume. Use action 'get' to read current volume levels, or 'set' with stream and level to change volume."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", kotlinx.serialization.json.buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("get"))
                    add(kotlinx.serialization.json.JsonPrimitive("set"))
                })
                put("description", "Action: 'get' to read volume, 'set' to change volume")
            }
            putJsonObject("stream") {
                put("type", "string")
                put("enum", kotlinx.serialization.json.buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("media"))
                    add(kotlinx.serialization.json.JsonPrimitive("ring"))
                    add(kotlinx.serialization.json.JsonPrimitive("alarm"))
                    add(kotlinx.serialization.json.JsonPrimitive("notification"))
                })
                put("description", "Audio stream type (for 'set' action)")
            }
            putJsonObject("level") {
                put("type", "integer")
                put("description", "Volume level to set (0 to max, varies by stream)")
            }
        }
        put("required", kotlinx.serialization.json.buildJsonArray {
            add(kotlinx.serialization.json.JsonPrimitive("action"))
        })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val action = args["action"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'action' parameter"
        )

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        return when (action) {
            "get" -> {
                val info = buildString {
                    appendLine("## Volume Levels")

                    val mediaVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val mediaMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val mediaPct = if (mediaMax > 0) (mediaVol * 100 / mediaMax) else 0
                    appendLine("- 🎵 Media: $mediaVol/$mediaMax ($mediaPct%)")

                    val ringVol = audio.getStreamVolume(AudioManager.STREAM_RING)
                    val ringMax = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
                    val ringPct = if (ringMax > 0) (ringVol * 100 / ringMax) else 0
                    appendLine("- 🔔 Ring: $ringVol/$ringMax ($ringPct%)")

                    val alarmVol = audio.getStreamVolume(AudioManager.STREAM_ALARM)
                    val alarmMax = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                    val alarmPct = if (alarmMax > 0) (alarmVol * 100 / alarmMax) else 0
                    appendLine("- ⏰ Alarm: $alarmVol/$alarmMax ($alarmPct%)")

                    val notifVol = audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                    val notifMax = audio.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
                    val notifPct = if (notifMax > 0) (notifVol * 100 / notifMax) else 0
                    appendLine("- 🔔 Notification: $notifVol/$notifMax ($notifPct%)")

                    val ringerMode = when (audio.ringerMode) {
                        AudioManager.RINGER_MODE_SILENT -> "Silent"
                        AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                        AudioManager.RINGER_MODE_NORMAL -> "Normal"
                        else -> "Unknown"
                    }
                    appendLine("\n- Ringer Mode: $ringerMode")
                }
                ToolResult(success = true, content = info)
            }
            "set" -> {
                val streamName = args["stream"]?.jsonPrimitive?.content ?: return ToolResult(
                    success = false, content = "", errorMessage = "Missing 'stream' parameter for set action"
                )
                val level = args["level"]?.jsonPrimitive?.content?.toIntOrNull() ?: return ToolResult(
                    success = false, content = "", errorMessage = "Missing or invalid 'level' parameter"
                )

                val streamType = when (streamName) {
                    "media" -> AudioManager.STREAM_MUSIC
                    "ring" -> AudioManager.STREAM_RING
                    "alarm" -> AudioManager.STREAM_ALARM
                    "notification" -> AudioManager.STREAM_NOTIFICATION
                    else -> return ToolResult(
                        success = false, content = "", errorMessage = "Unknown stream: $streamName"
                    )
                }

                val maxVol = audio.getStreamMaxVolume(streamType)
                val safeLevel = level.coerceIn(0, maxVol)
                audio.setStreamVolume(streamType, safeLevel, 0)
                ToolResult(success = true, content = "Set $streamName volume to $safeLevel/$maxVol")
            }
            else -> ToolResult(success = false, content = "", errorMessage = "Unknown action: $action. Use 'get' or 'set'.")
        }
    }
}
