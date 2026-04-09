package com.openclaw.agent.core.web.cookie

/**
 * Configuration for sites that require login via WebView.
 */
data class SiteConfig(
    val site: String,
    val displayName: String,
    val loginUrl: String,
    val domain: String,
    /** URL pattern that indicates successful login (redirect after login) */
    val successUrlPattern: String,
    /** Cookie names to check for login success (any match = logged in) */
    val requiredCookies: List<String> = emptyList(),
    val icon: String = ""
) {
    companion object {
        val ALL = listOf(
            SiteConfig(
                site = "bilibili",
                displayName = "哔哩哔哩",
                loginUrl = "https://passport.bilibili.com/login",
                domain = ".bilibili.com",
                successUrlPattern = "bilibili.com",
                requiredCookies = listOf("SESSDATA", "bili_jct"),
                icon = "🅱️"
            ),
            SiteConfig(
                site = "zhihu",
                displayName = "知乎",
                loginUrl = "https://www.zhihu.com/signin",
                domain = ".zhihu.com",
                successUrlPattern = "zhihu.com",
                requiredCookies = listOf("z_c0"),
                icon = "📘"
            ),
            SiteConfig(
                site = "weibo",
                displayName = "微博",
                loginUrl = "https://passport.weibo.com/sso/signin",
                domain = ".weibo.com",
                successUrlPattern = "weibo.com",
                requiredCookies = listOf("SUB", "SUBP"),
                icon = "🔴"
            ),
            SiteConfig(
                site = "xiaohongshu",
                displayName = "小红书",
                loginUrl = "https://www.xiaohongshu.com/explore",
                domain = ".xiaohongshu.com",
                successUrlPattern = "xiaohongshu.com",
                requiredCookies = listOf("web_session"),
                icon = "📕"
            ),
            SiteConfig(
                site = "douban",
                displayName = "豆瓣",
                loginUrl = "https://accounts.douban.com/passport/login",
                domain = ".douban.com",
                successUrlPattern = "douban.com",
                requiredCookies = listOf("dbcl2"),
                icon = "🌿"
            ),
            SiteConfig(
                site = "v2ex",
                displayName = "V2EX",
                loginUrl = "https://www.v2ex.com/signin",
                domain = ".v2ex.com",
                successUrlPattern = "v2ex.com",
                requiredCookies = listOf("A2"),
                icon = "💬"
            )
        )

        fun findBySite(site: String): SiteConfig? = ALL.find { it.site == site }
    }
}
