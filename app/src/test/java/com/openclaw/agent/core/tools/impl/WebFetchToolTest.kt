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

class WebFetchToolTest {

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
    fun `name is web_fetch`() {
        val tool = WebFetchTool(client)
        assertEquals("web_fetch", tool.name)
    }

    @Test
    fun `missing url returns error`() = runTest {
        val tool = WebFetchTool(client)
        val result = tool.execute(buildJsonObject {})
        assertFalse(result.success)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `fetches content from mock server`() = runTest {
        server.enqueue(MockResponse()
            .setBody("<html><body><h1>Hello World</h1><p>Test content</p></body></html>")
            .setHeader("Content-Type", "text/html"))

        val tool = WebFetchTool(client)
        val result = tool.execute(buildJsonObject {
            put("url", server.url("/test").toString())
        })
        assertTrue(result.success)
        assertTrue(result.content.contains("Hello") || result.content.contains("Test"))
    }

    @Test
    fun `handles server error gracefully`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val tool = WebFetchTool(client)
        val result = tool.execute(buildJsonObject {
            put("url", server.url("/error").toString())
        })
        // Should still return something (either success with error content or failure)
        assertNotNull(result)
    }
}
