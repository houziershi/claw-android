package com.openclaw.agent.core.web.cookie

import org.junit.Assert.*
import org.junit.Test

class SiteConfigTest {

    @Test
    fun `ALL has 6 sites`() {
        assertEquals(6, SiteConfig.ALL.size)
    }

    @Test
    fun `findBySite returns correct config`() {
        val bilibili = SiteConfig.findBySite("bilibili")
        assertNotNull(bilibili)
        assertEquals("哔哩哔哩", bilibili!!.displayName)
        assertTrue(bilibili.requiredCookies.contains("SESSDATA"))
    }

    @Test
    fun `findBySite returns null for unknown site`() {
        assertNull(SiteConfig.findBySite("nonexistent"))
    }

    @Test
    fun `all sites have login URL and domain`() {
        SiteConfig.ALL.forEach { config ->
            assertTrue("${config.site} missing loginUrl", config.loginUrl.startsWith("https://"))
            assertTrue("${config.site} missing domain", config.domain.startsWith("."))
        }
    }

    @Test
    fun `required sites all have required cookies`() {
        listOf("bilibili", "zhihu", "weibo").forEach { site ->
            val config = SiteConfig.findBySite(site)
            assertNotNull(config)
            assertTrue("$site should have required cookies", config!!.requiredCookies.isNotEmpty())
        }
    }
}
