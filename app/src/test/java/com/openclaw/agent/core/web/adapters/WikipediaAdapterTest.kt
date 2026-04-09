package com.openclaw.agent.core.web.adapters

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WikipediaAdapterTest {

    private lateinit var adapter: WikipediaAdapter

    @Before
    fun setup() {
        adapter = WikipediaAdapter(OkHttpClient())
    }

    @Test
    fun `metadata is correct`() {
        assertEquals("wikipedia", adapter.site)
        assertEquals("Wikipedia", adapter.displayName)
    }

    @Test
    fun `has 3 commands`() {
        val names = adapter.commands.map { it.name }.toSet()
        assertEquals(setOf("search", "summary", "random"), names)
    }

    @Test
    fun `search without query returns error`() = runTest {
        val result = adapter.execute("search", emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("query"))
    }

    @Test
    fun `summary without title returns error`() = runTest {
        val result = adapter.execute("summary", emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("title"))
    }

    @Test
    fun `unknown command returns error`() = runTest {
        val result = adapter.execute("invalid", emptyMap())
        assertFalse(result.success)
    }
}
