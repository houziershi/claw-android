package com.openclaw.agent.core.web.cookie

import android.util.Log
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CookieChecker"

/**
 * Checks if stored cookies are likely expired.
 *
 * Strategies:
 * 1. Age-based: cookies older than maxAge are considered expired
 * 2. Response-based: detect login redirects or error codes
 */
@Singleton
class CookieChecker @Inject constructor(
    private val cookieVault: CookieVault
) {
    /** Default max age: 30 days */
    private val defaultMaxAgeMs = TimeUnit.DAYS.toMillis(30)

    /** Per-site max ages (some sites expire faster) */
    private val siteMaxAge = mapOf(
        "bilibili" to TimeUnit.DAYS.toMillis(30),
        "zhihu" to TimeUnit.DAYS.toMillis(14),
        "weibo" to TimeUnit.DAYS.toMillis(7),
        "xiaohongshu" to TimeUnit.DAYS.toMillis(7),
        "douban" to TimeUnit.DAYS.toMillis(30),
        "v2ex" to TimeUnit.DAYS.toMillis(30)
    )

    /**
     * Check if cookies for a site are likely expired based on age.
     */
    fun isLikelyExpired(site: String): Boolean {
        if (!cookieVault.isLoggedIn(site)) return true

        val savedAt = cookieVault.getSavedAt(site)
        if (savedAt == 0L) return true

        val maxAge = siteMaxAge[site] ?: defaultMaxAgeMs
        val age = System.currentTimeMillis() - savedAt
        val expired = age > maxAge

        if (expired) {
            Log.d(TAG, "$site cookies likely expired (age=${TimeUnit.MILLISECONDS.toDays(age)} days)")
        }
        return expired
    }

    /**
     * Check if an HTTP response body indicates the user needs to re-login.
     * Common patterns: redirect to login page, error code for "not logged in".
     */
    fun isLoginRequired(site: String, responseBody: String, statusCode: Int): Boolean {
        // HTTP 401/403 usually means auth failed
        if (statusCode == 401 || statusCode == 403) return true

        return when (site) {
            "bilibili" -> {
                // Bilibili returns code=-101 when not logged in
                responseBody.contains("\"code\":-101")
            }
            "zhihu" -> {
                responseBody.contains("\"error\"") && responseBody.contains("login")
            }
            "weibo" -> {
                responseBody.contains("\"errno\":\"100005\"") ||
                    responseBody.contains("\"ok\":0") && responseBody.contains("login")
            }
            else -> false
        }
    }

    /**
     * Build a user-friendly message suggesting re-login.
     */
    fun buildReLoginMessage(site: String): String {
        val config = SiteConfig.findBySite(site)
        val displayName = config?.displayName ?: site
        return "${displayName}的登录状态已过期，请在设置 → 站点账号中重新登录。"
    }
}
