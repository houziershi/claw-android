package com.openclaw.agent.core.web.pipeline

import android.content.Context
import android.util.Log
import com.openclaw.agent.core.web.WebAdapter
import java.io.File

private const val TAG = "YamlAdapterLoader"

class YamlAdapterLoader(
    private val parser: SimpleYamlParser,
    private val runner: PipelineRunner
) {
    /**
     * Load YAML adapters from an Android assets directory.
     * Multiple files with the same site are grouped into one MultiCommandYamlAdapter.
     */
    fun loadFromAssets(context: Context, assetPath: String = "adapters"): List<WebAdapter> {
        val defs = mutableListOf<YamlPipelineDef>()
        try {
            val files = context.assets.list(assetPath) ?: return emptyList()
            for (file in files) {
                if (!file.endsWith(".yaml") && !file.endsWith(".yml")) continue
                try {
                    val yaml = context.assets.open("$assetPath/$file")
                        .bufferedReader().readText()
                    val def = parser.parse(yaml)
                    if (def != null) {
                        // Skip browser-required adapters
                        if (def.browser) {
                            Log.d(TAG, "Skipping browser adapter: ${def.site}/${def.name}")
                        } else {
                            defs.add(def)
                            Log.d(TAG, "Loaded YAML adapter: ${def.site}/${def.name}")
                        }
                    } else {
                        Log.w(TAG, "Failed to parse: $assetPath/$file")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading $assetPath/$file", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing assets at $assetPath", e)
        }
        return groupIntoAdapters(defs)
    }

    /**
     * Load YAML adapters from a file system directory.
     */
    fun loadFromDirectory(dir: File): List<WebAdapter> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val defs = mutableListOf<YamlPipelineDef>()
        dir.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".yaml") || it.name.endsWith(".yml")) }
            .forEach { file ->
                try {
                    val def = parser.parse(file.readText())
                    if (def != null && !def.browser) {
                        defs.add(def)
                        Log.d(TAG, "Loaded from file: ${def.site}/${def.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading ${file.path}", e)
                }
            }
        return groupIntoAdapters(defs)
    }

    private fun groupIntoAdapters(defs: List<YamlPipelineDef>): List<WebAdapter> {
        // Group by site
        val bySite = defs.groupBy { it.site }
        return bySite.map { (site, siteDefs) ->
            if (siteDefs.size == 1) {
                YamlAdapter(siteDefs.first(), runner)
            } else {
                val commandMap = siteDefs.associate { def ->
                    def.name to YamlAdapter(def, runner)
                }
                MultiCommandYamlAdapter(site, commandMap)
            }
        }
    }
}
