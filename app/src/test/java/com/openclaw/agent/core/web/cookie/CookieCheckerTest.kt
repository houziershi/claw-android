package com.openclaw.agent.core.web.cookie

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CookieCheckerTest {

    private lateinit var cookieVault: CookieVault
    private lateinit var checker: CookieChecker

    @Before
    fun setup() {
        cookieVault = mock()
        checker = CookieChecker(cookieVault)
    }

    @Test
    fun `isLikelyExpired returns true when not logged in`() {
        whenever(cookieVault.isLoggedIn("bilibili")).thenReturn(false)
        assertTrue(checker.isLikelyExpired("bilibili"))
    }

    @Test
    fun `isLikelyExpired returns false for fresh cookies`() {
        whenever(cookieVault.isLoggedIn("bilibili")).thenReturn(true)
        whenever(cookieVault.getSavedAt("bilibili")).thenReturn(System.currentTimeMillis() - 1000)
        assertFalse(checker.isLikelyExpired("bilibili"))
    }

    @Test
    fun `isLikelyExpired returns true for old cookies`() {
        whenever(cookieVault.isLoggedIn("bilibili")).thenReturn(true)
        // 60 days ago
        whenever(cookieVault.getSavedAt("bilibili"))
            .thenReturn(System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000)
        assertTrue(checker.isLikelyExpired("bilibili"))
    }

    @Test
    fun `isLoginRequired detects bilibili code -101`() {
        assertTrue(checker.isLoginRequired("bilibili", """{"code":-101,"message":"账号未登录"}""", 200))
    }

    @Test
    fun `isLoginRequired detects 401`() {
        assertTrue(checker.isLoginRequired("zhihu", "", 401))
    }

    @Test
    fun `isLoginRequired returns false for normal response`() {
        assertFalse(checker.isLoginRequired("bilibili", """{"code":0,"data":{}}""", 200))
    }

    @Test
    fun `buildReLoginMessage includes site name`() {
        val msg = checker.buildReLoginMessage("bilibili")
        assertTrue(msg.contains("哔哩哔哩"))
        assertTrue(msg.contains("设置"))
    }
}
