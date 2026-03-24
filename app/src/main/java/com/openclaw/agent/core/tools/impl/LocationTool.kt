package com.openclaw.agent.core.tools.impl

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.Locale
import kotlin.coroutines.resume

private const val TAG = "LocationTool"
private const val LOCATION_MAX_AGE_MS = 5 * 60 * 1000L // 5 minutes
private const val LOCATION_TIMEOUT_MS = 10_000L // 10 seconds

class LocationTool(private val context: Context) : Tool {
    override val name = "location"
    override val description = "Get device location or convert between addresses and coordinates. Actions: 'current' (get current location with optional accuracy 'coarse'/'fine'), 'geocode' (address to lat/lng), 'reverse_geocode' (lat/lng to address)."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add(JsonPrimitive("current"))
                    add(JsonPrimitive("geocode"))
                    add(JsonPrimitive("reverse_geocode"))
                })
                put("description", "Action: 'current' to get current location, 'geocode' to convert address to coordinates, 'reverse_geocode' to convert coordinates to address")
            }
            putJsonObject("accuracy") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add(JsonPrimitive("coarse"))
                    add(JsonPrimitive("fine"))
                })
                put("description", "Location accuracy for 'current' action. 'coarse' uses network, 'fine' uses GPS. Default: 'coarse'")
            }
            putJsonObject("address") {
                put("type", "string")
                put("description", "Address string for 'geocode' action")
            }
            putJsonObject("lat") {
                put("type", "number")
                put("description", "Latitude for 'reverse_geocode' action")
            }
            putJsonObject("lng") {
                put("type", "number")
                put("description", "Longitude for 'reverse_geocode' action")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("action")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val action = args["action"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'action' parameter"
        )

        return when (action) {
            "current" -> {
                val accuracy = args["accuracy"]?.jsonPrimitive?.content ?: "coarse"
                getCurrentLocation(accuracy)
            }
            "geocode" -> {
                val address = args["address"]?.jsonPrimitive?.content ?: return ToolResult(
                    success = false, content = "", errorMessage = "Missing 'address' parameter for geocode action"
                )
                geocode(address)
            }
            "reverse_geocode" -> {
                val lat = args["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return ToolResult(
                    success = false, content = "", errorMessage = "Missing or invalid 'lat' parameter for reverse_geocode action"
                )
                val lng = args["lng"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return ToolResult(
                    success = false, content = "", errorMessage = "Missing or invalid 'lng' parameter for reverse_geocode action"
                )
                reverseGeocode(lat, lng)
            }
            else -> ToolResult(
                success = false, content = "", errorMessage = "Unknown action: $action. Use 'current', 'geocode', or 'reverse_geocode'."
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(accuracy: String): ToolResult = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            return@withContext ToolResult(
                success = false, content = "", errorMessage = "Location permission not granted. Need ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION."
            )
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext ToolResult(
                success = false, content = "", errorMessage = "LocationManager not available on this device."
            )

        val provider = if (accuracy == "fine") {
            LocationManager.GPS_PROVIDER
        } else {
            LocationManager.NETWORK_PROVIDER
        }

        if (!locationManager.isProviderEnabled(provider)) {
            return@withContext ToolResult(
                success = false, content = "",
                errorMessage = "Location provider '$provider' is disabled. Please enable ${if (accuracy == "fine") "GPS" else "network location"} in device settings."
            )
        }

        // Try last known location first (fast path)
        var location: Location? = locationManager.getLastKnownLocation(provider)
        val now = System.currentTimeMillis()

        if (location == null || (now - location.time) > LOCATION_MAX_AGE_MS) {
            Log.d(TAG, "Last known location is null or stale, requesting fresh location via $provider")
            // Request a fresh location update
            location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                requestSingleLocation(locationManager, provider)
            }
        }

        if (location == null) {
            return@withContext ToolResult(
                success = false, content = "", errorMessage = "Failed to get location within ${LOCATION_TIMEOUT_MS / 1000}s timeout. Make sure location services are enabled."
            )
        }

        val result = buildString {
            appendLine("## Current Location")
            appendLine("- Latitude: ${location.latitude}")
            appendLine("- Longitude: ${location.longitude}")
            appendLine("- Accuracy: ${"%.1f".format(location.accuracy)} m")
            appendLine("- Provider: ${location.provider}")

            // Try reverse geocoding for address
            val address = tryReverseGeocode(location.latitude, location.longitude)
            if (address != null) {
                appendLine("\n## Address")
                address.countryName?.let { appendLine("- Country: $it") }
                address.adminArea?.let { appendLine("- Province/State: $it") }
                address.locality?.let { appendLine("- City: $it") }
                address.subLocality?.let { appendLine("- District: $it") }
                address.thoroughfare?.let { street ->
                    val full = if (address.subThoroughfare != null) "$street ${address.subThoroughfare}" else street
                    appendLine("- Street: $full")
                }
                address.getAddressLine(0)?.let { appendLine("- Full: $it") }
            }
        }

        ToolResult(success = true, content = result.trim())
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleLocation(
        locationManager: LocationManager,
        provider: String
    ): Location = suspendCancellableCoroutine { cont ->
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d(TAG, "Received fresh location: ${location.latitude}, ${location.longitude}")
                locationManager.removeUpdates(this)
                if (cont.isActive) {
                    cont.resume(location)
                }
            }

            @Deprecated("Deprecated in API level 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())

        cont.invokeOnCancellation {
            Log.d(TAG, "Location request cancelled, removing updates")
            locationManager.removeUpdates(listener)
        }
    }

    private suspend fun geocode(address: String): ToolResult = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) {
            return@withContext ToolResult(
                success = false, content = "", errorMessage = "Geocoder is not available on this device."
            )
        }

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocationName(address, 5)

            if (results.isNullOrEmpty()) {
                return@withContext ToolResult(
                    success = false, content = "", errorMessage = "No results found for address: $address"
                )
            }

            val content = buildString {
                appendLine("## Geocode Results for \"$address\"")
                results.forEachIndexed { index, addr ->
                    appendLine("\n### Result ${index + 1}")
                    appendLine("- Latitude: ${addr.latitude}")
                    appendLine("- Longitude: ${addr.longitude}")
                    addr.getAddressLine(0)?.let { appendLine("- Address: $it") }
                    addr.countryName?.let { appendLine("- Country: $it") }
                    addr.adminArea?.let { appendLine("- Province/State: $it") }
                    addr.locality?.let { appendLine("- City: $it") }
                    addr.subLocality?.let { appendLine("- District: $it") }
                    addr.thoroughfare?.let { street ->
                        val full = if (addr.subThoroughfare != null) "$street ${addr.subThoroughfare}" else street
                        appendLine("- Street: $full")
                    }
                }
            }

            ToolResult(success = true, content = content.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Geocode failed for: $address", e)
            ToolResult(success = false, content = "", errorMessage = "Geocode failed: ${e.message}")
        }
    }

    private suspend fun reverseGeocode(lat: Double, lng: Double): ToolResult = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) {
            return@withContext ToolResult(
                success = false, content = "", errorMessage = "Geocoder is not available on this device."
            )
        }

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocation(lat, lng, 3)

            if (results.isNullOrEmpty()) {
                return@withContext ToolResult(
                    success = false, content = "", errorMessage = "No address found for coordinates: ($lat, $lng)"
                )
            }

            val content = buildString {
                appendLine("## Reverse Geocode Results for ($lat, $lng)")
                results.forEachIndexed { index, addr ->
                    appendLine("\n### Result ${index + 1}")
                    addr.getAddressLine(0)?.let { appendLine("- Full Address: $it") }
                    addr.countryName?.let { appendLine("- Country: $it") }
                    addr.adminArea?.let { appendLine("- Province/State: $it") }
                    addr.locality?.let { appendLine("- City: $it") }
                    addr.subLocality?.let { appendLine("- District: $it") }
                    addr.thoroughfare?.let { street ->
                        val full = if (addr.subThoroughfare != null) "$street ${addr.subThoroughfare}" else street
                        appendLine("- Street: $full")
                    }
                }
            }

            ToolResult(success = true, content = content.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Reverse geocode failed for: ($lat, $lng)", e)
            ToolResult(success = false, content = "", errorMessage = "Reverse geocode failed: ${e.message}")
        }
    }

    private fun tryReverseGeocode(lat: Double, lng: Double): android.location.Address? {
        return try {
            if (!Geocoder.isPresent()) return null
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocation(lat, lng, 1)
            results?.firstOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocode failed for ($lat, $lng): ${e.message}")
            null
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }
}
