package com.openclaw.agent.core.mijia

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MiotSpecCache"
private const val SPEC_BASE_URL = "https://home.miot-spec.com/spec/"

@Singleton
class MiotSpecCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) {
    private val cacheDir by lazy { File(context.filesDir, "mijia_spec_cache").also { it.mkdirs() } }
    private val json = Json { ignoreUnknownKeys = true }

    // Built-in common device specs (from device_catalogs.md)
    private val builtinSpecs: Map<String, MiotSpec> = mapOf(
        "lumi.switch.b2nacn02" to MiotSpec(
            name = "Aqara智能墙壁开关D1（零火双键版）",
            model = "lumi.switch.b2nacn02",
            properties = listOf(
                MiotProperty("on", "左键开关 / Left switch", "bool", "rw", null, null, null, 2, 1),
                MiotProperty("switch-on", "右键开关 / Right switch", "bool", "rw", null, null, null, 3, 1),
                MiotProperty("electric-power", "当前功率 / Current power", "float", "r", "W", null, null, 4, 1)
            ),
            actions = listOf(
                MiotAction("toggle", "切换左键 / Toggle left", 2, 1),
                MiotAction("switch-toggle", "切换右键 / Toggle right", 3, 1)
            )
        ),
        "lumi.switch.b1nacn02" to MiotSpec(
            name = "Aqara智能墙壁开关D1（零火单键版）",
            model = "lumi.switch.b1nacn02",
            properties = listOf(
                MiotProperty("on", "开关状态 / Switch state", "bool", "rw", null, null, null, 2, 1)
            ),
            actions = emptyList()
        ),
        "zhimi.airpurifier.ma2" to MiotSpec(
            name = "米家空气净化器2",
            model = "zhimi.airpurifier.ma2",
            properties = listOf(
                MiotProperty("on", "开关 / Power", "bool", "rw", null, null, null, 2, 1),
                MiotProperty("mode", "运行模式 / Mode", "int", "rw", null, listOf(0, 3, 1),
                    listOf(MiotValueItem(0, "Auto"), MiotValueItem(1, "Silent"), MiotValueItem(2, "Favorite")), 2, 4),
                MiotProperty("pm25-density", "PM2.5浓度 / PM2.5", "int", "r", "μg/m³", null, null, 3, 4)
            ),
            actions = emptyList()
        )
    )

    suspend fun getSpec(model: String): MiotSpec = withContext(Dispatchers.IO) {
        // 1. Builtin
        builtinSpecs[model]?.let { return@withContext it }

        // 2. File cache
        val cacheFile = File(cacheDir, "$model.json")
        if (cacheFile.exists()) {
            try {
                return@withContext parseSpecJson(cacheFile.readText(), model)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse cached spec for $model, re-fetching")
                cacheFile.delete()
            }
        }

        // 3. Network
        fetchAndCache(model)
    }

    private suspend fun fetchAndCache(model: String): MiotSpec = withContext(Dispatchers.IO) {
        Log.d(TAG, "Fetching spec for $model from miot-spec.org")
        val request = Request.Builder()
            .url("$SPEC_BASE_URL$model")
            .addHeader("User-Agent", "mijiaAPI/3.0.5")
            .build()

        val response = httpClient.newCall(request).execute()
        val html = response.body?.string() ?: throw MijiaApiException("Empty response from miot-spec.org")

        // Parse data-page attribute from HTML
        val matchResult = Regex("""data-page="(.*?)">""", RegexOption.DOT_MATCHES_ALL).find(html)
            ?: throw MijiaApiException("Cannot find device spec for model: $model")

        val rawJson = matchResult.groupValues[1].replace("&quot;", "\"")
        val spec = parseSpecJson(rawJson, model)

        // Cache to file
        try {
            File(cacheDir, "$model.json").writeText(rawJson)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache spec for $model")
        }
        spec
    }

    private fun parseSpecJson(pageJson: String, model: String): MiotSpec {
        val page = Json { ignoreUnknownKeys = true }.parseToJsonElement(pageJson).jsonObject
        val props = page["props"]?.jsonObject ?: throw MijiaApiException("No props in spec")
        val product = props["product"]?.jsonObject
        val spec = props["spec"]?.jsonObject ?: throw MijiaApiException("No spec in props")

        val name = product?.get("name")?.jsonPrimitive?.content
            ?: spec["name"]?.jsonPrimitive?.content
            ?: model
        val services = spec["services"]?.jsonObject ?: JsonObject(emptyMap())

        val properties = mutableListOf<MiotProperty>()
        val actions = mutableListOf<MiotAction>()
        val propNames = mutableSetOf<String>()
        val actionNames = mutableSetOf<String>()

        services.forEach { siidStr, serviceElem ->
            val siid = siidStr.toIntOrNull() ?: return@forEach
            val service = serviceElem.jsonObject
            val serviceName = service["name"]?.jsonPrimitive?.content ?: ""

            service["properties"]?.jsonObject?.forEach { piidStr, propElem ->
                val piid = piidStr.toIntOrNull() ?: return@forEach
                val prop = propElem.jsonObject
                var propName = prop["name"]?.jsonPrimitive?.content ?: return@forEach

                // Avoid name collisions across services
                if (propNames.contains(propName)) propName = "$serviceName-$propName"
                propNames.add(propName)

                val format = prop["format"]?.jsonPrimitive?.content ?: "string"
                val type = when {
                    format.startsWith("int") -> "int"
                    format.startsWith("uint") -> "uint"
                    else -> format
                }
                val access = prop["access"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
                val rw = buildString {
                    if ("read" in access) append("r")
                    if ("write" in access) append("w")
                }
                val valueRange = prop["value-range"]?.jsonArray?.mapNotNull {
                    it.jsonPrimitive.doubleOrNull
                }
                val valueList = prop["value-list"]?.jsonArray?.mapNotNull { elem ->
                    val obj = elem.jsonObject
                    MiotValueItem(
                        value = obj["value"]?.jsonPrimitive?.int ?: return@mapNotNull null,
                        description = obj["description"]?.jsonPrimitive?.content ?: ""
                    )
                }

                properties.add(MiotProperty(
                    name = propName,
                    description = buildString {
                        prop["description"]?.jsonPrimitive?.content?.let { append(it) }
                        prop["desc_zh_cn"]?.jsonPrimitive?.content?.let { append(" / $it") }
                    },
                    type = type,
                    rw = rw,
                    unit = prop["unit"]?.jsonPrimitive?.content?.takeIf { it != "none" },
                    range = valueRange,
                    valueList = valueList,
                    siid = siid,
                    piid = piid
                ))
            }

            service["actions"]?.jsonObject?.forEach { aiidStr, actElem ->
                val aiid = aiidStr.toIntOrNull() ?: return@forEach
                val act = actElem.jsonObject
                var actName = act["name"]?.jsonPrimitive?.content ?: return@forEach
                if (actionNames.contains(actName)) actName = "$serviceName-$actName"
                actionNames.add(actName)
                actions.add(MiotAction(
                    name = actName,
                    description = buildString {
                        act["description"]?.jsonPrimitive?.content?.let { append(it) }
                        act["desc_zh_cn"]?.jsonPrimitive?.content?.let { append(" / $it") }
                    },
                    siid = siid,
                    aiid = aiid
                ))
            }
        }
        return MiotSpec(name = name, model = model, properties = properties, actions = actions)
    }

    /** Fuzzy-match a property name (supports Chinese descriptions) */
    fun fuzzyMatchProperty(spec: MiotSpec, query: String): MiotProperty? {
        val q = query.lowercase().trim()
        // Exact name match first
        spec.properties.firstOrNull { it.name.lowercase() == q }?.let { return it }
        // Contains match on name or description
        return spec.properties.firstOrNull {
            it.name.lowercase().contains(q) || it.description.lowercase().contains(q)
        }
    }
}
