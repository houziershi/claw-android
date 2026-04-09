package com.openclaw.agent.core.web.cookie

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CookieVault"
private const val PREFS_NAME = "cookie_vault_prefs"

/**
 * Encrypted storage for website login cookies.
 * Uses Android Keystore via EncryptedSharedPreferences.
 */
@Singleton
open class CookieVault @Inject constructor(private val context: Context?) {

    private val prefs by lazy {
        requireNotNull(context) { "CookieVault requires non-null context" }
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted prefs, falling back to plain", e)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Save cookies for a site.
     * @param site Site identifier (e.g. "bilibili", "zhihu")
     * @param domain Cookie domain (e.g. ".bilibili.com")
     * @param cookies Full cookie string
     */
    open fun saveCookies(site: String, domain: String, cookies: String) {
        prefs.edit()
            .putString("${site}_cookies", cookies)
            .putString("${site}_domain", domain)
            .putLong("${site}_saved_at", System.currentTimeMillis())
            .apply()
        Log.d(TAG, "Saved cookies for $site (${cookies.length} chars)")
    }

    /**
     * Get stored cookies for a site.
     */
    open fun getCookies(site: String): String? {
        return prefs.getString("${site}_cookies", null)
    }

    /**
     * Get the domain for a site's cookies.
     */
    open fun getDomain(site: String): String? {
        return prefs.getString("${site}_domain", null)
    }

    /**
     * Check if we have stored cookies for a site.
     */
    open fun isLoggedIn(site: String): Boolean {
        return !getCookies(site).isNullOrBlank()
    }

    /**
     * Get timestamp when cookies were saved.
     */
    open fun getSavedAt(site: String): Long {
        return prefs.getLong("${site}_saved_at", 0)
    }

    /**
     * Clear cookies for a site.
     */
    open fun logout(site: String) {
        prefs.edit()
            .remove("${site}_cookies")
            .remove("${site}_domain")
            .remove("${site}_saved_at")
            .apply()
        Log.d(TAG, "Cleared cookies for $site")
    }

    /**
     * Get all logged-in sites.
     */
    open fun getLoggedInSites(): List<String> {
        return prefs.all.keys
            .filter { it.endsWith("_cookies") }
            .map { it.removeSuffix("_cookies") }
            .filter { isLoggedIn(it) }
    }
}
