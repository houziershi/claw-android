package com.openclaw.agent.core.web.adapters

import com.openclaw.agent.core.web.AuthStrategy
import com.openclaw.agent.core.web.cookie.CookieVault
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class WeiboAdapterTest {

    private lateinit var cookieVault: CookieVault

    @Before
    fun setup() {
        cookieVault = mock()
    }

    @Test
    fun `metadata is correct`() {
        val adapter = WeiboAdapter(OkHttpClient(), cookieVault)
        assertEquals("weibo", adapter.site)
        assertEquals(AuthStrategy.COOKIE, adapter.authStrategy)
    }

    @Test
    fun `has hot and search commands`() {
        val adapter = WeiboAdapter(OkHttpClient(), cookieVault)
        val names = adapter.commands.map { it.name }.toSet()
        assertEquals(setOf("hot", "search"), names)
    }

    @Test
    fun `search without query returns error`() = runTest {
        val adapter = WeiboAdapter(OkHttpClient(), cookieVault)
        val result = adapter.execute("search", emptyMap())
        assertFalse(result.success)
    }

    @Test
    fun `search without login returns login prompt`() = runTest {
        whenever(cookieVault.isLoggedIn("weibo")).thenReturn(false)
        val adapter = WeiboAdapter(OkHttpClient(), cookieVault)
        val result = adapter.execute("search", mapOf("query" to "热搜"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("登录"))
    }

    @Test
    fun `unknown command returns error`() = runTest {
        val adapter = WeiboAdapter(OkHttpClient(), cookieVault)
        val result = adapter.execute("invalid", emptyMap())
        assertFalse(result.success)
    }
}
