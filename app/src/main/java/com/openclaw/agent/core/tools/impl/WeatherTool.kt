package com.openclaw.agent.core.tools.impl

import android.util.Log
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "WeatherTool"

class WeatherTool(private val okHttpClient: OkHttpClient) : Tool {
    override val name = "get_weather"
    override val description = "Get current weather and forecast for a location. Returns temperature, conditions, wind, humidity, and 3-day forecast. No API key needed."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("location") {
                put("type", "string")
                put("description", "City name, e.g. 'Beijing', 'New York', 'Tokyo'. Chinese city names like '武汉' are supported.")
            }
            putJsonObject("format") {
                put("type", "string")
                put("description", "Output format: 'current' for current conditions only, 'forecast' for 3-day forecast. Default: 'current'")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("location")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val location = args["location"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'location' parameter"
        )
        val format = args["format"]?.jsonPrimitive?.content ?: "current"

        return try {
            val result = when (format) {
                "forecast" -> getForecast(location)
                else -> getCurrentWeather(location)
            }
            ToolResult(success = true, content = result)
        } catch (e: Exception) {
            Log.e(TAG, "Weather fetch failed", e)
            ToolResult(success = false, content = "", errorMessage = "Weather fetch failed: ${e.message}")
        }
    }

    private fun getCurrentWeather(location: String): String {
        // Use wttr.in JSON API
        val url = "https://wttr.in/${java.net.URLEncoder.encode(location, "UTF-8")}?format=j1"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "curl/8.0")
            .get()
            .build()

        val response = okHttpClient.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty response")
        response.close()

        val json = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
        val current = json["current_condition"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return "Weather data not available for: $location"

        val tempC = current["temp_C"]?.jsonPrimitive?.content ?: "?"
        val feelsLikeC = current["FeelsLikeC"]?.jsonPrimitive?.content ?: "?"
        val humidity = current["humidity"]?.jsonPrimitive?.content ?: "?"
        val windSpeedKmph = current["windspeedKmph"]?.jsonPrimitive?.content ?: "?"
        val windDir = current["winddir16Point"]?.jsonPrimitive?.content ?: "?"
        val weatherDesc = current["weatherDesc"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("value")?.jsonPrimitive?.content ?: "?"
        val visibility = current["visibility"]?.jsonPrimitive?.content ?: "?"
        val pressure = current["pressure"]?.jsonPrimitive?.content ?: "?"
        val uvIndex = current["uvIndex"]?.jsonPrimitive?.content ?: "?"

        // Get area name
        val nearestArea = json["nearest_area"]?.jsonArray?.firstOrNull()?.jsonObject
        val areaName = nearestArea?.get("areaName")?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("value")?.jsonPrimitive?.content ?: location
        val country = nearestArea?.get("country")?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("value")?.jsonPrimitive?.content ?: ""

        return buildString {
            appendLine("📍 $areaName, $country")
            appendLine("🌡️ Temperature: ${tempC}°C (Feels like ${feelsLikeC}°C)")
            appendLine("☁️ Conditions: $weatherDesc")
            appendLine("💨 Wind: ${windSpeedKmph} km/h $windDir")
            appendLine("💧 Humidity: ${humidity}%")
            appendLine("👁️ Visibility: ${visibility} km")
            appendLine("📊 Pressure: ${pressure} hPa")
            appendLine("☀️ UV Index: $uvIndex")
        }
    }

    private fun getForecast(location: String): String {
        val url = "https://wttr.in/${java.net.URLEncoder.encode(location, "UTF-8")}?format=j1"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "curl/8.0")
            .get()
            .build()

        val response = okHttpClient.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty response")
        response.close()

        val json = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject

        // Current
        val current = getCurrentWeather(location)

        // Forecast
        val weather = json["weather"]?.jsonArray ?: return current
        val forecastLines = StringBuilder()
        forecastLines.appendLine("\n📅 3-Day Forecast:")
        forecastLines.appendLine("─".repeat(30))

        weather.forEach { day ->
            val dayObj = day.jsonObject
            val date = dayObj["date"]?.jsonPrimitive?.content ?: "?"
            val maxTemp = dayObj["maxtempC"]?.jsonPrimitive?.content ?: "?"
            val minTemp = dayObj["mintempC"]?.jsonPrimitive?.content ?: "?"
            val avgTemp = dayObj["avgtempC"]?.jsonPrimitive?.content ?: "?"
            val totalSnow = dayObj["totalSnow_cm"]?.jsonPrimitive?.content ?: "0"
            val sunHour = dayObj["sunHour"]?.jsonPrimitive?.content ?: "?"
            val uvIndex = dayObj["uvIndex"]?.jsonPrimitive?.content ?: "?"

            // Get hourly description for midday
            val hourly = dayObj["hourly"]?.jsonArray
            val middayDesc = hourly?.getOrNull(4)?.jsonObject
                ?.get("weatherDesc")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("value")?.jsonPrimitive?.content ?: "?"
            val chanceOfRain = hourly?.getOrNull(4)?.jsonObject
                ?.get("chanceofrain")?.jsonPrimitive?.content ?: "?"

            forecastLines.appendLine("$date: $middayDesc")
            forecastLines.appendLine("  🌡️ $minTemp°C ~ $maxTemp°C (avg $avgTemp°C)")
            forecastLines.appendLine("  🌧️ Rain: $chanceOfRain% | ☀️ Sun: ${sunHour}h | UV: $uvIndex")
        }

        return current + forecastLines.toString()
    }
}
