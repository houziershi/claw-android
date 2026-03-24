package com.openclaw.agent.core.mijia

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MijiaApiClient"
private const val API_BASE = "https://api.mijia.tech/app"

@Singleton
class MijiaApiClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val authStore: MijiaAuthStore
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun isAuthenticated(): Boolean = authStore.isAuthenticated()

    // ── Core request ──────────────────────────────────────────────────────────

    private suspend fun request(uri: String, data: Map<String, Any>): JsonObject =
        withContext(Dispatchers.IO) {
            val auth = authStore.load()
                ?: throw MijiaApiException("Not authenticated. Please login first.", -1)

            val dataStr = buildJsonObject {
                data.forEach { (k, v) ->
                    when (v) {
                        is Boolean -> put(k, v)
                        is Int -> put(k, v)
                        is Long -> put(k, v)
                        is Double -> put(k, v)
                        is String -> put(k, v)
                        is List<*> -> put(k, buildJsonArray { v.forEach { add(JsonPrimitive(it.toString())) } })
                        else -> put(k, v.toString())
                    }
                }
            }.toString()

            val nonce = MijiaRequestSigner.genNonce()
            val signedNonce = MijiaRequestSigner.getSignedNonce(auth.ssecurity, nonce)

            val params = mutableMapOf("data" to dataStr)
            val encParams = MijiaRequestSigner.generateEncParams(
                uri, "POST", signedNonce, nonce, params, auth.ssecurity
            )

            val formBody = FormBody.Builder().apply {
                encParams.forEach { (k, v) -> add(k, v) }
            }.build()

            val request = Request.Builder()
                .url("$API_BASE$uri")
                .post(formBody)
                .addHeader("User-Agent", auth.ua)
                .addHeader("accept-encoding", "identity")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("miot-accept-encoding", "GZIP")
                .addHeader("miot-encrypt-algorithm", "ENCRYPT-RC4")
                .addHeader("x-xiaomi-protocal-flag-cli", "PROTOCAL-HTTP2")
                .addHeader("Cookie", buildCookie(auth))
                .build()

            // Debug: cross-platform RC4 test with Python-generated values
            val fixedKey = "Hq3U8w+y13f7cNb2OI9jfOBygk2HUXV/EgDTUZ0dmA4="
            val pythonEncrypted = "KqqMLqm6+pS7AZ9QM0LYwnJULTV8FoutCQEUN9GNpyRj"
            val expectedPlain = """{"code":0,"result":{"test":true}}"""
            val crossDec = MijiaRequestSigner.decryptRc4(fixedKey, pythonEncrypted)
            Log.d(TAG, "Cross-platform RC4 test: dec='$crossDec', expected='$expectedPlain', match=${crossDec == expectedPlain}")
            // Also test own roundtrip
            val testEnc = MijiaRequestSigner.encryptRc4(fixedKey, expectedPlain)
            Log.d(TAG, "Kotlin encrypted: $testEnc, Python encrypted: $pythonEncrypted, match=${testEnc == pythonEncrypted}")
            Log.d(TAG, "signedNonce=$signedNonce")

            val response = httpClient.newCall(request).execute()
            Log.d(TAG, "Response code: ${response.code}")
            val bodyBytes = response.body?.bytes() ?: throw MijiaApiException("Empty response", -1)
            val bodyStr = bodyBytes.toString(Charsets.UTF_8)
            Log.d(TAG, "Raw response for $uri (${bodyBytes.size} bytes, ${bodyStr.length} chars)")
            Log.d(TAG, "First 200 chars: ${bodyStr.take(200)}")
            Log.d(TAG, "First 20 bytes hex: ${bodyBytes.take(20).joinToString("") { "%02x".format(it) }}")
            Log.d(TAG, "Auth: ssecurity=${auth.ssecurity.take(10)}..., serviceToken=${auth.serviceToken.take(20)}..., userId=${auth.userId}, cUserId=${auth.cUserId.take(10)}")

            // Try JSON parse first; if fails, decrypt RC4
            val resultJson = try {
                json.parseToJsonElement(bodyStr).jsonObject
            } catch (e: Exception) {
                Log.d(TAG, "Not JSON, trying RC4 decrypt with signedNonce=${signedNonce.take(20)}...")
                try {
                    // Get raw decrypted bytes to check for GZIP
                    val decryptedBytes = MijiaRequestSigner.decryptRc4Bytes(signedNonce, bodyStr)
                    Log.d(TAG, "Decrypted bytes len=${decryptedBytes.size}, first 20 hex=${decryptedBytes.take(20).joinToString("") { "%02x".format(it) }}")
                    val decrypted = MijiaRequestSigner.decryptRc4(signedNonce, bodyStr)
                    Log.d(TAG, "Decrypted: ${decrypted.take(200)}")
                    json.parseToJsonElement(decrypted).jsonObject
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to decrypt/parse response", e2)
                    throw MijiaApiException("Failed to parse response: ${e2.message}", -1)
                }
            }

            Log.d(TAG, "Response for $uri: ${resultJson.toString().take(500)}")
            Log.d(TAG, "Auth: ssecurity=${auth.ssecurity.take(10)}..., serviceToken=${auth.serviceToken.take(20)}..., userId=${auth.userId}, cUserId=${auth.cUserId.take(10)}")
            Log.d(TAG, "Cookie sent: ${buildCookie(auth).take(200)}")
            Log.d(TAG, "Data payload: ${dataStr.take(200)}")

            val code = resultJson["code"]?.jsonPrimitive?.intOrNull ?: 0
            if (code != 0 || "result" !in resultJson) {
                val msg = resultJson["message"]?.jsonPrimitive?.content
                    ?: resultJson["desc"]?.jsonPrimitive?.content
                    ?: "Unknown error"
                throw MijiaApiException("API error: $msg", code)
            }
            resultJson["result"]!!.jsonObject
        }

    private fun buildCookie(auth: MijiaAuth): String {
        val tz = java.util.TimeZone.getDefault()
        val offsetMs = tz.rawOffset
        val offsetH = offsetMs / 3600000
        val offsetM = (Math.abs(offsetMs) % 3600000) / 60000
        val tzStr = "GMT%+03d:%02d".format(offsetH, offsetM)
        return "cUserId=${auth.cUserId};" +
                "yetAnotherServiceToken=${auth.serviceToken};" +
                "serviceToken=${auth.serviceToken};" +
                "timezone_id=${tz.id};" +
                "timezone=$tzStr;" +
                "is_daylight=0;" +
                "dst_offset=0;" +
                "channel=MI_APP_STORE;" +
                "countryCode=${auth.locale.split("_").getOrElse(1) { "CN" }};" +
                "PassportDeviceId=${auth.deviceId};" +
                "locale=${auth.locale}"
    }

    // ── API Methods ───────────────────────────────────────────────────────────

    suspend fun getHomesList(): List<JsonObject> = withContext(Dispatchers.IO) {
        val result = request("/v2/homeroom/gethome_merged", mapOf(
            "fg" to true, "fetch_share" to true, "fetch_share_dev" to true,
            "fetch_cariot" to true, "limit" to 300, "app_ver" to 7, "plat_form" to 0
        ))
        result["homelist"]?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
    }

    private suspend fun getHomeOwnerUid(homeId: String): Long {
        return getHomesList()
            .firstOrNull { it["id"]?.jsonPrimitive?.content == homeId }
            ?.get("uid")?.jsonPrimitive?.long
            ?: throw MijiaApiException("Home not found: $homeId")
    }

    suspend fun getDevicesList(homeId: String? = null): List<MijiaDevice> =
        withContext(Dispatchers.IO) {
            if (homeId != null) {
                fetchDevicesForHome(homeId)
            } else {
                getHomesList().flatMap { home ->
                    fetchDevicesForHome(home["id"]?.jsonPrimitive?.content ?: return@flatMap emptyList())
                }
            }
        }

    private suspend fun fetchDevicesForHome(homeId: String): List<MijiaDevice> {
        val ownerUid = getHomeOwnerUid(homeId)
        val devices = mutableListOf<MijiaDevice>()
        var startDid = ""
        var hasMore = true

        while (hasMore) {
            val result = request("/home/home_device_list", mapOf(
                "home_owner" to ownerUid,
                "home_id" to homeId.toLong(),
                "limit" to 200,
                "start_did" to startDid,
                "get_split_device" to true,
                "support_smart_home" to true,
                "get_cariot_device" to true,
                "get_third_device" to true
            ))
            val deviceInfo = result["device_info"]?.jsonArray ?: break
            deviceInfo.forEach { elem ->
                val obj = elem.jsonObject
                devices.add(MijiaDevice(
                    did = obj["did"]?.jsonPrimitive?.content ?: return@forEach,
                    name = obj["name"]?.jsonPrimitive?.content ?: "Unknown",
                    model = obj["model"]?.jsonPrimitive?.content ?: "",
                    isOnline = obj["isOnline"]?.jsonPrimitive?.boolean ?: false,
                    roomName = obj["roomName"]?.jsonPrimitive?.content ?: "未知房间",
                    homeId = homeId
                ))
            }
            startDid = result["max_did"]?.jsonPrimitive?.content ?: ""
            hasMore = result["has_more"]?.jsonPrimitive?.boolean == true && startDid.isNotEmpty()
        }
        return devices
    }

    suspend fun getSharedDevicesList(): List<MijiaDevice> = withContext(Dispatchers.IO) {
        val result = request("/v2/home/device_list_page", mapOf(
            "ssid" to "<unknown ssid>",
            "bssid" to "02:00:00:00:00:00",
            "getVirtualModel" to true,
            "getHuamiDevices" to 1,
            "get_split_device" to true,
            "support_smart_home" to true,
            "get_cariot_device" to true,
            "get_third_device" to true,
            "get_phone_device" to true,
            "get_miwear_device" to true
        ))
        result["list"]?.jsonArray
            ?.map { it.jsonObject }
            ?.filter { it["owner"]?.jsonPrimitive?.boolean == true }
            ?.map { obj ->
                MijiaDevice(
                    did = obj["did"]?.jsonPrimitive?.content ?: "",
                    name = obj["name"]?.jsonPrimitive?.content ?: "Unknown",
                    model = obj["model"]?.jsonPrimitive?.content ?: "",
                    isOnline = obj["isOnline"]?.jsonPrimitive?.boolean ?: false,
                    homeId = "shared"
                )
            } ?: emptyList()
    }

    suspend fun getAllDevices(): List<MijiaDevice> = withContext(Dispatchers.IO) {
        getDevicesList() + getSharedDevicesList()
    }

    suspend fun getDevicesProp(params: List<Map<String, Any>>): List<JsonObject> =
        withContext(Dispatchers.IO) {
            val result = request("/miotspec/prop/get", mapOf(
                "params" to params,
                "datasource" to 1
            ))
            result.values.firstOrNull()?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
        }

    suspend fun setDevicesProp(params: List<Map<String, Any>>): List<JsonObject> =
        withContext(Dispatchers.IO) {
            val result = request("/miotspec/prop/set", mapOf("params" to params))
            result.values.firstOrNull()?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
        }

    suspend fun runAction(did: String, siid: Int, aiid: Int, value: List<Any>? = null): JsonObject =
        withContext(Dispatchers.IO) {
            val param = mutableMapOf<String, Any>("did" to did, "siid" to siid, "aiid" to aiid)
            if (value != null) param["value"] = value
            request("/miotspec/action", mapOf("params" to param))
        }

    suspend fun getScenesList(homeId: String? = null): List<JsonObject> =
        withContext(Dispatchers.IO) {
            if (homeId != null) {
                fetchScenesForHome(homeId)
            } else {
                getHomesList().flatMap { home ->
                    fetchScenesForHome(home["id"]?.jsonPrimitive?.content ?: return@flatMap emptyList())
                }
            }
        }

    private suspend fun fetchScenesForHome(homeId: String): List<JsonObject> {
        val ownerUid = getHomeOwnerUid(homeId)
        return try {
            val result = request("/appgateway/miot/appsceneservice/AppSceneService/GetSimpleSceneList", mapOf(
                "app_version" to 12,
                "get_type" to 2,
                "home_id" to homeId,
                "owner_uid" to ownerUid
            ))
            result["manual_scene_info_list"]?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get scenes for home $homeId: ${e.message}")
            emptyList()
        }
    }

    suspend fun runScene(sceneId: String, homeId: String): Boolean = withContext(Dispatchers.IO) {
        val ownerUid = getHomeOwnerUid(homeId)
        try {
            request("/appgateway/miot/appsceneservice/AppSceneService/NewRunScene", mapOf(
                "scene_id" to sceneId,
                "scene_type" to 2,
                "phone_id" to "null",
                "home_id" to homeId,
                "owner_uid" to ownerUid
            ))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to run scene $sceneId: ${e.message}")
            false
        }
    }

    suspend fun checkAuth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val beginAt = System.currentTimeMillis() / 1000 - 3600
            request("/v2/message/v2/check_new_msg", mapOf("begin_at" to beginAt))
            true
        } catch (e: Exception) {
            Log.w(TAG, "Auth check failed: ${e.message}")
            false
        }
    }
}
