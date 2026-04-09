package com.openclaw.agent.core.web.adapters

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class YahooFinanceAdapterTest {

    private lateinit var adapter: YahooFinanceAdapter

    @Before
    fun setup() {
        adapter = YahooFinanceAdapter(OkHttpClient())
    }

    @Test
    fun `metadata is correct`() {
        assertEquals("yahoofinance", adapter.site)
        assertEquals("Yahoo Finance", adapter.displayName)
    }

    @Test
    fun `has quote command`() {
        assertEquals(1, adapter.commands.size)
        assertEquals("quote", adapter.commands[0].name)
    }

    @Test
    fun `quote without symbol returns error`() = runTest {
        val result = adapter.execute("quote", emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("symbol"))
    }

    @Test
    fun `unknown command returns error`() = runTest {
        val result = adapter.execute("invalid", emptyMap())
        assertFalse(result.success)
    }
}
