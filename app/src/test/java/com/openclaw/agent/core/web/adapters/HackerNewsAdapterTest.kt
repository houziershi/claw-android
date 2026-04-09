package com.openclaw.agent.core.web.adapters

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for HackerNewsAdapter.
 * Tests that don't hit real API: metadata, parameter validation, error handling.
 */
class HackerNewsAdapterTest {

    private lateinit var adapter: HackerNewsAdapter
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        client = OkHttpClient()
        adapter = HackerNewsAdapter(client)
    }

    @Test
    fun `metadata is correct`() {
        assertEquals("hackernews", adapter.site)
        assertEquals("Hacker News", adapter.displayName)
        assertEquals(com.openclaw.agent.core.web.AuthStrategy.PUBLIC, adapter.authStrategy)
    }

    @Test
    fun `has 4 commands`() {
        val names = adapter.commands.map { it.name }.toSet()
        assertEquals(setOf("top", "new", "best", "search"), names)
    }

    @Test
    fun `search command has required query arg`() {
        val searchCmd = adapter.commands.first { it.name == "search" }
        val queryArg = searchCmd.args.first { it.name == "query" }
        assertTrue(queryArg.required)
    }

    @Test
    fun `unknown command returns error`() = runTest {
        val result = adapter.execute("invalid", emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("Unknown command"))
    }

    @Test
    fun `search without query returns error`() = runTest {
        val result = adapter.execute("search", emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("query"))
    }
}
