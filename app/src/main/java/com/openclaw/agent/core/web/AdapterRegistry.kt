package com.openclaw.agent.core.web

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AdapterRegistry"

@Singleton
class AdapterRegistry @Inject constructor() {

    private val adapters = mutableMapOf<String, WebAdapter>()

    fun register(adapter: WebAdapter) {
        Log.d(TAG, "Registered adapter: ${adapter.site}")
        adapters[adapter.site] = adapter
    }

    fun getAdapter(site: String): WebAdapter? = adapters[site]

    fun getAllAdapters(): List<WebAdapter> = adapters.values.toList()
}
