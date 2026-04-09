package com.openclaw.agent.core.web.adapters

import com.openclaw.agent.core.web.AuthStrategy
import com.openclaw.agent.core.web.cookie.CookieVault
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BilibiliAdapterTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var cookieVault: CookieVault

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
        cookieVault = mock()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `metadata is correct`() {
        val adapter = BilibiliAdapter(client, cookieVault)
        assertEquals("bilibili", adapter.site)
        assertEquals("哔哩哔哩", adapter.displayName)
        assertEquals(AuthStrategy.COOKIE, adapter.authStrategy)
    }

    @Test
    fun `has hot search ranking commands`() {
        val adapter = BilibiliAdapter(client, cookieVault)
        val cmdNames = adapter.commands.map { it.name }.toSet()
        assertEquals(setOf("hot", "search", "ranking"), cmdNames)
    }

    @Test
    fun `search without query returns error`() = runTest {
        val adapter = BilibiliAdapter(client, cookieVault)
        val result = adapter.execute("search", emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("query"))
    }

    @Test
    fun `search without login returns login prompt`() = runTest {
        whenever(cookieVault.isLoggedIn("bilibili")).thenReturn(false)
        val adapter = BilibiliAdapter(client, cookieVault)
        val result = adapter.execute("search", mapOf("query" to "kotlin"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("登录"))
    }

    @Test
    fun `unknown command returns error`() = runTest {
        val adapter = BilibiliAdapter(client, cookieVault)
        val result = adapter.execute("invalid", emptyMap())
        assertFalse(result.success)
    }
}
