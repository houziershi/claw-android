package com.openclaw.agent.core.web.adapters

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StackOverflowAdapterTest {

    private lateinit var adapter: StackOverflowAdapter

    @Before
    fun setup() {
        adapter = StackOverflowAdapter(OkHttpClient())
    }

    @Test
    fun `metadata is correct`() {
        assertEquals("stackoverflow", adapter.site)
        assertEquals("Stack Overflow", adapter.displayName)
    }

    @Test
    fun `has hot and search commands`() {
        val names = adapter.commands.map { it.name }.toSet()
        assertEquals(setOf("hot", "search"), names)
    }

    @Test
    fun `search without query returns error`() = runTest {
        val result = adapter.execute("search", emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("query"))
    }

    @Test
    fun `unknown command returns error`() = runTest {
        val result = adapter.execute("invalid", emptyMap())
        assertFalse(result.success)
    }
}
