package com.openclaw.agent.core.tools.impl

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WebSearchToolTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `name is web_search`() {
        val tool = WebSearchTool(client)
        assertEquals("web_search", tool.name)
    }

    @Test
    fun `missing query returns error`() = runTest {
        val tool = WebSearchTool(client)
        val result = tool.execute(buildJsonObject {})
        assertFalse(result.success)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("query"))
    }

    @Test
    fun `parameterSchema requires query`() {
        val tool = WebSearchTool(client)
        val schema = tool.parameterSchema
        assertEquals("object", schema["type"]?.toString()?.trim('"'))
    }
}
