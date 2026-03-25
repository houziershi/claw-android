package com.openclaw.agent.core.tools.impl

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Field

/**
 * Test AppLauncherTool alias mapping via reflection (no Context needed).
 */
class AppLauncherToolAliasTest {

    private val aliases: Map<String, String> by lazy {
        val field = AppLauncherTool::class.java.getDeclaredField("APP_ALIASES")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        field.get(null) as Map<String, String>
    }

    @Test
    fun `alias map contains WeChat`() {
        assertEquals("com.tencent.mm", aliases["微信"])
    }

    @Test
    fun `alias map contains Alipay`() {
        assertEquals("com.eg.android.AlipayGzhd", aliases["支付宝"])
    }

    @Test
    fun `alias map contains TikTok`() {
        assertEquals("com.ss.android.ugc.aweme", aliases["抖音"])
    }

    @Test
    fun `alias map contains QQ`() {
        assertEquals("com.tencent.mobileqq", aliases["QQ"])
    }

    @Test
    fun `alias map contains Bilibili with two names`() {
        assertEquals("tv.danmaku.bili", aliases["bilibili"])
        assertEquals("tv.danmaku.bili", aliases["B站"])
    }

    @Test
    fun `alias map has at least 15 entries`() {
        assertTrue("Should have at least 15 aliases, got ${aliases.size}", aliases.size >= 15)
    }

    @Test
    fun `all alias values are valid package names`() {
        aliases.values.forEach { pkg ->
            assertTrue("Package '$pkg' should contain dots", pkg.contains("."))
            assertFalse("Package '$pkg' should not be blank", pkg.isBlank())
        }
    }
}
