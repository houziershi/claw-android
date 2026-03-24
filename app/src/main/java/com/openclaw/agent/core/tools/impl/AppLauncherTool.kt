package com.openclaw.agent.core.tools.impl

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.*

private const val TAG = "AppLauncherTool"

class AppLauncherTool(private val context: Context) : Tool {
    override val name = "app_launcher"
    override val description = "Launch, search, or list installed apps. Actions: 'launch' (open an app by name or package), 'search' (fuzzy search installed apps by name), 'list_recent' (list installed launchable apps sorted by name)."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add("launch"); add("search"); add("list_recent") })
                put("description", "Action: launch, search, or list_recent")
            }
            putJsonObject("app_name") {
                put("type", "string")
                put("description", "App name to launch (used with 'launch' action)")
            }
            putJsonObject("package_name") {
                put("type", "string")
                put("description", "Package name to launch (used with 'launch' action)")
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "Search query for fuzzy matching app names (used with 'search' action)")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Max number of apps to list (used with 'list_recent' action, default 10)")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("action")) })
    }

    companion object {
        private val APP_ALIASES = mapOf(
            "微信" to "com.tencent.mm",
            "支付宝" to "com.eg.android.AlipayGzhd",
            "抖音" to "com.ss.android.ugc.aweme",
            "淘宝" to "com.taobao.taobao",
            "QQ" to "com.tencent.mobileqq",
            "bilibili" to "tv.danmaku.bili",
            "B站" to "tv.danmaku.bili",
            "百度地图" to "com.baidu.BaiduMap",
            "高德地图" to "com.autonavi.minimap",
            "美团" to "com.sankuai.meituan",
            "京东" to "com.jingdong.app.mall",
            "网易云音乐" to "com.netease.cloudmusic",
            "QQ音乐" to "com.tencent.qqmusic",
            "知乎" to "com.zhihu.android",
            "小红书" to "com.xingin.xhs",
            "钉钉" to "com.alibaba.android.rimet",
            "飞书" to "com.ss.android.lark",
            "WPS" to "cn.wps.moffice_eng"
        )
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val action = args["action"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'action' parameter"
        )

        return when (action) {
            "launch" -> handleLaunch(args)
            "search" -> handleSearch(args)
            "list_recent" -> handleListRecent(args)
            else -> ToolResult(
                success = false, content = "",
                errorMessage = "Unknown action: $action. Use launch, search, or list_recent."
            )
        }
    }

    private fun handleLaunch(args: JsonObject): ToolResult {
        val packageName = args["package_name"]?.jsonPrimitive?.content
        val appName = args["app_name"]?.jsonPrimitive?.content

        if (packageName.isNullOrBlank() && appName.isNullOrBlank()) {
            return ToolResult(
                success = false, content = "",
                errorMessage = "Missing 'app_name' or 'package_name' parameter for launch action"
            )
        }

        // Resolve target package name
        val targetPackage = resolvePackageName(packageName, appName)
            ?: return ToolResult(
                success = false, content = "",
                errorMessage = "App not found: ${appName ?: packageName}"
            )

        return launchApp(targetPackage)
    }

    private fun resolvePackageName(packageName: String?, appName: String?): String? {
        // 1. If package_name is provided directly, use it
        if (!packageName.isNullOrBlank()) {
            return packageName
        }

        val name = appName ?: return null

        // 2. Check alias mapping (case-insensitive)
        val aliasMatch = APP_ALIASES.entries.firstOrNull { (alias, _) ->
            alias.equals(name, ignoreCase = true)
        }
        if (aliasMatch != null) {
            Log.d(TAG, "Resolved alias '${name}' -> '${aliasMatch.value}'")
            return aliasMatch.value
        }

        // 3. Fuzzy search via packageManager
        val launchableApps = getLaunchableApps()
        val pm = context.packageManager

        // Exact label match first
        val exactMatch = launchableApps.firstOrNull { resolveInfo ->
            resolveInfo.loadLabel(pm).toString().equals(name, ignoreCase = true)
        }
        if (exactMatch != null) {
            return exactMatch.activityInfo.packageName
        }

        // Fuzzy match (label contains query)
        val fuzzyMatch = launchableApps.firstOrNull { resolveInfo ->
            resolveInfo.loadLabel(pm).toString().contains(name, ignoreCase = true)
        }
        if (fuzzyMatch != null) {
            Log.d(TAG, "Fuzzy matched '${name}' -> '${fuzzyMatch.activityInfo.packageName}'")
            return fuzzyMatch.activityInfo.packageName
        }

        return null
    }

    private fun launchApp(packageName: String): ToolResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent == null) {
                return ToolResult(
                    success = false, content = "",
                    errorMessage = "Cannot launch app: $packageName (no launch intent found, app may not be installed)"
                )
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Launched app: $packageName")

            // Get app label for friendly response
            val label = try {
                val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName
            }

            ToolResult(success = true, content = "Successfully launched $label ($packageName)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app: $packageName", e)
            ToolResult(
                success = false, content = "",
                errorMessage = "Failed to launch app $packageName: ${e.message}"
            )
        }
    }

    private fun handleSearch(args: JsonObject): ToolResult {
        val query = args["query"]?.jsonPrimitive?.content
        if (query.isNullOrBlank()) {
            return ToolResult(
                success = false, content = "",
                errorMessage = "Missing 'query' parameter for search action"
            )
        }

        return try {
            val pm = context.packageManager
            val launchableApps = getLaunchableApps()

            val matched = launchableApps.filter { resolveInfo ->
                resolveInfo.loadLabel(pm).toString().contains(query, ignoreCase = true)
            }.map { resolveInfo ->
                val label = resolveInfo.loadLabel(pm).toString()
                val pkg = resolveInfo.activityInfo.packageName
                label to pkg
            }.sortedBy { it.first }

            if (matched.isEmpty()) {
                ToolResult(success = true, content = "No apps found matching '$query'")
            } else {
                val sb = StringBuilder()
                sb.appendLine("Found ${matched.size} app(s) matching '$query':")
                matched.forEach { (label, pkg) ->
                    sb.appendLine("  • $label ($pkg)")
                }
                ToolResult(success = true, content = sb.toString().trim())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search apps", e)
            ToolResult(
                success = false, content = "",
                errorMessage = "Failed to search apps: ${e.message}"
            )
        }
    }

    private fun handleListRecent(args: JsonObject): ToolResult {
        val limit = args["limit"]?.jsonPrimitive?.intOrNull ?: 10

        return try {
            val pm = context.packageManager
            val launchableApps = getLaunchableApps()

            val appList = launchableApps.map { resolveInfo ->
                val label = resolveInfo.loadLabel(pm).toString()
                val pkg = resolveInfo.activityInfo.packageName
                label to pkg
            }.sortedBy { it.first }
                .take(limit)

            if (appList.isEmpty()) {
                ToolResult(success = true, content = "No launchable apps found.")
            } else {
                val sb = StringBuilder()
                sb.appendLine("Installed launchable apps (showing ${appList.size} of ${launchableApps.size}):")
                appList.forEachIndexed { index, (label, pkg) ->
                    sb.appendLine("  ${index + 1}. $label ($pkg)")
                }
                ToolResult(success = true, content = sb.toString().trim())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list apps", e)
            ToolResult(
                success = false, content = "",
                errorMessage = "Failed to list apps: ${e.message}"
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun getLaunchableApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    }
}
