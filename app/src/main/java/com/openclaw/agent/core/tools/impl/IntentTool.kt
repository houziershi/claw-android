package com.openclaw.agent.core.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val TAG = "IntentTool"

class IntentTool(private val context: Context) : Tool {
    override val name = "intent"
    override val description = """Launch common Android intents. Actions:
- 'call': Open dialer with a phone number. Params: phone_number (string, required).
- 'sms': Open SMS app. Params: phone_number (string, required), message (string, optional).
- 'email': Compose email. Params: to (string, required), subject (string, optional), body (string, optional).
- 'map': Open map. Params: query (string) OR lat+lng (double). Provide either query or both lat and lng.
- 'share': Share text to other apps. Params: text (string, required).
- 'open_settings': Open system settings page. Params: page (string, required). Supported pages: wifi, bluetooth, display, battery, sound, location, security, apps, notification, accessibility, date, language, developer."""

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add(JsonPrimitive("call"))
                    add(JsonPrimitive("sms"))
                    add(JsonPrimitive("email"))
                    add(JsonPrimitive("map"))
                    add(JsonPrimitive("share"))
                    add(JsonPrimitive("open_settings"))
                })
                put("description", "Action to perform")
            }
            putJsonObject("phone_number") {
                put("type", "string")
                put("description", "Phone number (for call/sms)")
            }
            putJsonObject("message") {
                put("type", "string")
                put("description", "SMS message body (for sms)")
            }
            putJsonObject("to") {
                put("type", "string")
                put("description", "Email recipient address (for email)")
            }
            putJsonObject("subject") {
                put("type", "string")
                put("description", "Email subject (for email)")
            }
            putJsonObject("body") {
                put("type", "string")
                put("description", "Email body (for email)")
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "Map search query (for map)")
            }
            putJsonObject("lat") {
                put("type", "number")
                put("description", "Latitude (for map)")
            }
            putJsonObject("lng") {
                put("type", "number")
                put("description", "Longitude (for map)")
            }
            putJsonObject("text") {
                put("type", "string")
                put("description", "Text to share (for share)")
            }
            putJsonObject("page") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add(JsonPrimitive("wifi"))
                    add(JsonPrimitive("bluetooth"))
                    add(JsonPrimitive("display"))
                    add(JsonPrimitive("battery"))
                    add(JsonPrimitive("sound"))
                    add(JsonPrimitive("location"))
                    add(JsonPrimitive("security"))
                    add(JsonPrimitive("apps"))
                    add(JsonPrimitive("notification"))
                    add(JsonPrimitive("accessibility"))
                    add(JsonPrimitive("date"))
                    add(JsonPrimitive("language"))
                    add(JsonPrimitive("developer"))
                })
                put("description", "Settings page to open (for open_settings)")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("action")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val action = args["action"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'action' parameter"
        )

        return when (action) {
            "call" -> handleCall(args)
            "sms" -> handleSms(args)
            "email" -> handleEmail(args)
            "map" -> handleMap(args)
            "share" -> handleShare(args)
            "open_settings" -> handleOpenSettings(args)
            else -> ToolResult(
                success = false, content = "",
                errorMessage = "Unknown action: $action. Use call, sms, email, map, share, or open_settings."
            )
        }
    }

    private fun handleCall(args: JsonObject): ToolResult {
        val phoneNumber = args["phone_number"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'phone_number' parameter for call action"
        )
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened dialer for: $phoneNumber")
            ToolResult(success = true, content = "Opened dialer with number: $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open dialer", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to open dialer: ${e.message}")
        }
    }

    private fun handleSms(args: JsonObject): ToolResult {
        val phoneNumber = args["phone_number"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'phone_number' parameter for sms action"
        )
        val message = args["message"]?.jsonPrimitive?.content
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
                if (!message.isNullOrBlank()) {
                    putExtra("sms_body", message)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened SMS for: $phoneNumber")
            val desc = if (!message.isNullOrBlank()) {
                "Opened SMS to $phoneNumber with message pre-filled"
            } else {
                "Opened SMS to $phoneNumber"
            }
            ToolResult(success = true, content = desc)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open SMS", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to open SMS: ${e.message}")
        }
    }

    private fun handleEmail(args: JsonObject): ToolResult {
        val to = args["to"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'to' parameter for email action"
        )
        val subject = args["subject"]?.jsonPrimitive?.content
        val body = args["body"]?.jsonPrimitive?.content
        return try {
            val uriBuilder = StringBuilder("mailto:${Uri.encode(to)}")
            val queryParams = mutableListOf<String>()
            if (!subject.isNullOrBlank()) {
                queryParams.add("subject=${Uri.encode(subject)}")
            }
            if (!body.isNullOrBlank()) {
                queryParams.add("body=${Uri.encode(body)}")
            }
            if (queryParams.isNotEmpty()) {
                uriBuilder.append("?${queryParams.joinToString("&")}")
            }

            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse(uriBuilder.toString())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened email compose to: $to")
            ToolResult(success = true, content = "Opened email compose to: $to")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open email", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to open email: ${e.message}")
        }
    }

    private fun handleMap(args: JsonObject): ToolResult {
        val query = args["query"]?.jsonPrimitive?.content
        val lat = args["lat"]?.jsonPrimitive?.doubleOrNull
        val lng = args["lng"]?.jsonPrimitive?.doubleOrNull

        val geoUri = when {
            !query.isNullOrBlank() -> "geo:0,0?q=${Uri.encode(query)}"
            lat != null && lng != null -> "geo:$lat,$lng"
            else -> return ToolResult(
                success = false, content = "",
                errorMessage = "Missing parameters for map action. Provide 'query' (string) or both 'lat' and 'lng' (double)."
            )
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(geoUri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val desc = if (!query.isNullOrBlank()) {
                "Opened map searching for: $query"
            } else {
                "Opened map at coordinates: $lat, $lng"
            }
            Log.d(TAG, desc)
            ToolResult(success = true, content = desc)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open map", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to open map: ${e.message}")
        }
    }

    private fun handleShare(args: JsonObject): ToolResult {
        val text = args["text"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'text' parameter for share action"
        )
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Log.d(TAG, "Opened share chooser")
            ToolResult(success = true, content = "Opened share dialog with text")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share text", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to share text: ${e.message}")
        }
    }

    private fun handleOpenSettings(args: JsonObject): ToolResult {
        val page = args["page"]?.jsonPrimitive?.content ?: "main"
        return try {
            val settingsAction = when (page) {
                "wifi" -> Settings.ACTION_WIFI_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "display" -> Settings.ACTION_DISPLAY_SETTINGS
                "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                "sound" -> Settings.ACTION_SOUND_SETTINGS
                "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                "security" -> Settings.ACTION_SECURITY_SETTINGS
                "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                "notification" -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
                "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                "date" -> Settings.ACTION_DATE_SETTINGS
                "language" -> Settings.ACTION_LOCALE_SETTINGS
                "developer" -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
                else -> Settings.ACTION_SETTINGS
            }

            val intent = Intent(settingsAction).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (page == "notification") {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            }
            context.startActivity(intent)

            val pageName = if (page == "main" || settingsAction == Settings.ACTION_SETTINGS) {
                "main settings"
            } else {
                "$page settings"
            }
            Log.d(TAG, "Opened $pageName")
            ToolResult(success = true, content = "Opened $pageName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open settings: $page", e)
            ToolResult(success = false, content = "", errorMessage = "Failed to open $page settings: ${e.message}")
        }
    }
}
