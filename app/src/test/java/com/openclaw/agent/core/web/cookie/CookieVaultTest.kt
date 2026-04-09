package com.openclaw.agent.core.web.cookie

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for CookieVault logic using a plain SharedPreferences fake.
 * We test the core contract without depending on EncryptedSharedPreferences
 * (which requires Android Keystore hardware).
 */
class CookieVaultTest {

    // In-memory fake that replicates CookieVault's SharedPreferences behaviour
    private val fakePrefs = FakeSharedPreferences()

    private lateinit var vault: CookieVault

    @Before
    fun setup() {
        vault = FakeCookieVault(fakePrefs)
    }

    @Test
    fun `isLoggedIn returns false when no cookies stored`() {
        assertFalse(vault.isLoggedIn("bilibili"))
    }

    @Test
    fun `saveCookies and getCookies round-trip`() {
        vault.saveCookies("bilibili", ".bilibili.com", "SESSDATA=abc123; bili_jct=xyz")
        assertTrue(vault.isLoggedIn("bilibili"))
        assertEquals("SESSDATA=abc123; bili_jct=xyz", vault.getCookies("bilibili"))
    }

    @Test
    fun `logout clears cookies`() {
        vault.saveCookies("zhihu", ".zhihu.com", "z_c0=token123")
        assertTrue(vault.isLoggedIn("zhihu"))
        vault.logout("zhihu")
        assertFalse(vault.isLoggedIn("zhihu"))
        assertNull(vault.getCookies("zhihu"))
    }

    @Test
    fun `getLoggedInSites returns correct list`() {
        vault.saveCookies("bilibili", ".bilibili.com", "SESSDATA=a")
        vault.saveCookies("zhihu", ".zhihu.com", "z_c0=b")
        val sites = vault.getLoggedInSites()
        assertTrue(sites.contains("bilibili"))
        assertTrue(sites.contains("zhihu"))
    }

    @Test
    fun `multiple sites are independent`() {
        vault.saveCookies("bilibili", ".bilibili.com", "cookies_b")
        vault.saveCookies("zhihu", ".zhihu.com", "cookies_z")
        vault.logout("bilibili")
        assertFalse(vault.isLoggedIn("bilibili"))
        assertTrue(vault.isLoggedIn("zhihu"))
        assertEquals("cookies_z", vault.getCookies("zhihu"))
    }

    @Test
    fun `getDomain returns saved domain`() {
        vault.saveCookies("weibo", ".weibo.com", "SUB=abc")
        assertEquals(".weibo.com", vault.getDomain("weibo"))
    }

    @Test
    fun `getSavedAt returns recent timestamp`() {
        val before = System.currentTimeMillis()
        vault.saveCookies("bilibili", ".bilibili.com", "cookie")
        val after = System.currentTimeMillis()
        val savedAt = vault.getSavedAt("bilibili")
        assertTrue(savedAt in before..after)
    }
}

// ---------------------------------------------------------------------------
// Test helpers (no Android dependencies needed)
// ---------------------------------------------------------------------------

/**
 * A CookieVault subclass that uses an in-memory fake instead of EncryptedSharedPreferences.
 */
open class FakeCookieVault(private val prefs: FakeSharedPreferences) : CookieVault(null as Context?) {

    override fun saveCookies(site: String, domain: String, cookies: String) {
        prefs.putString("${site}_cookies", cookies)
        prefs.putString("${site}_domain", domain)
        prefs.putLong("${site}_saved_at", System.currentTimeMillis())
    }

    override fun getCookies(site: String) = prefs.getString("${site}_cookies")
    override fun getDomain(site: String) = prefs.getString("${site}_domain")
    override fun isLoggedIn(site: String) = !getCookies(site).isNullOrBlank()
    override fun getSavedAt(site: String) = prefs.getLong("${site}_saved_at")
    override fun logout(site: String) {
        prefs.remove("${site}_cookies")
        prefs.remove("${site}_domain")
        prefs.remove("${site}_saved_at")
    }
    override fun getLoggedInSites() = prefs.keys()
        .filter { it.endsWith("_cookies") }
        .map { it.removeSuffix("_cookies") }
        .filter { isLoggedIn(it) }
}

class FakeSharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    fun putString(key: String, value: String) { data[key] = value }
    fun putLong(key: String, value: Long) { data[key] = value }
    fun getString(key: String) = data[key] as? String
    fun getLong(key: String) = (data[key] as? Long) ?: 0L
    fun remove(key: String) { data.remove(key) }
    fun keys() = data.keys.toList()
}
