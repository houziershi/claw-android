package com.openclaw.agent.core.tools.impl

import com.openclaw.agent.core.mijia.MijiaApiClient
import com.openclaw.agent.core.mijia.MijiaApiException
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.*

class MijiaSceneTool(
    private val apiClient: MijiaApiClient
) : Tool {
    override val name = "mijia_scene"
    override val description = """List or execute Xiaomi Mijia home automation scenes.
- action 'list': get all available manual scenes
- action 'run': execute a scene by name or scene_id"""

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add("list"); add("run") })
                put("description", "Action: 'list' or 'run'")
            }
            putJsonObject("scene_name") {
                put("type", "string")
                put("description", "Scene name to run (fuzzy match)")
            }
            putJsonObject("scene_id") {
                put("type", "string")
                put("description", "Scene ID to run (exact match, use if name is ambiguous)")
            }
        }
        put("required", buildJsonArray { add("action") })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        if (!apiClient.isAuthenticated()) {
            return ToolResult(
                success = false, content = "",
                errorMessage = "AUTH_REQUIRED: 未登录小米账号。请前往 设置 → 米家账号 完成登录。"
            )
        }

        val action = args["action"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, content = "", errorMessage = "Missing 'action'")

        return try {
            when (action) {
                "list" -> {
                    val scenes = apiClient.getScenesList()
                    if (scenes.isEmpty()) {
                        return ToolResult(success = true, content = "未找到场景。请先在米家 App 中创建手动场景（智能 → + → 手动执行）。")
                    }
                    val sb = StringBuilder("可用场景:\n")
                    scenes.forEach { scene ->
                        val id = scene["scene_id"]?.jsonPrimitive?.content ?: ""
                        val name = scene["name"]?.jsonPrimitive?.content ?: "未命名"
                        sb.appendLine("- $name (ID: $id)")
                    }
                    ToolResult(success = true, content = sb.toString().trim())
                }
                "run" -> {
                    val sceneName = args["scene_name"]?.jsonPrimitive?.content
                    val sceneId = args["scene_id"]?.jsonPrimitive?.content

                    val scenes = apiClient.getScenesList()
                    val target = when {
                        sceneId != null -> scenes.firstOrNull {
                            it["scene_id"]?.jsonPrimitive?.content == sceneId
                        }
                        sceneName != null -> scenes.firstOrNull { scene ->
                            val name = scene["name"]?.jsonPrimitive?.content ?: ""
                            name == sceneName || name.contains(sceneName) || sceneName.contains(name)
                        }
                        else -> return ToolResult(success = false, content = "", errorMessage = "请提供 scene_name 或 scene_id")
                    } ?: return ToolResult(
                        success = false, content = "",
                        errorMessage = "未找到场景 '${sceneName ?: sceneId}'。可用: ${scenes.map { it["name"]?.jsonPrimitive?.content }.joinToString(", ")}"
                    )

                    val sid = target["scene_id"]?.jsonPrimitive?.content ?: ""
                    val homeId = target["home_id"]?.jsonPrimitive?.content ?: ""
                    val sName = target["name"]?.jsonPrimitive?.content ?: sid

                    val success = apiClient.runScene(sid, homeId)
                    if (success) {
                        ToolResult(success = true, content = "✅ 场景 '$sName' 已执行")
                    } else {
                        ToolResult(success = false, content = "", errorMessage = "执行场景 '$sName' 失败")
                    }
                }
                else -> ToolResult(success = false, content = "", errorMessage = "Unknown action: $action")
            }
        } catch (e: MijiaApiException) {
            ToolResult(success = false, content = "", errorMessage = "米家 API 错误: ${e.message}")
        } catch (e: Exception) {
            ToolResult(success = false, content = "", errorMessage = "场景操作失败: ${e.message}")
        }
    }
}
