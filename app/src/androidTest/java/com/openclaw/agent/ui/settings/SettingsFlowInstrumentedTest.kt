package com.openclaw.agent.ui.settings

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.openclaw.agent.MainActivity
import com.openclaw.agent.core.web.cookie.CookieVault
import com.openclaw.agent.testing.DeviceWakeupRule
import com.openclaw.agent.ui.web.WebLoginActivity
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class SettingsFlowInstrumentedTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()
    private val appContext = ApplicationProvider.getApplicationContext<Context>()
    private val cookieVault = CookieVault(appContext)

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(DeviceWakeupRule())
        .around(composeRule)

    @After
    fun tearDown() {
        clearSiteCookies()
    }

    @Test
    fun settingsFlow_savesApiConfiguration() {
        val apiKey = "sk-ins...test"
        val baseUrl = "https://example.com/v1/messages"

        openSettings()

        composeRule.onNodeWithTag("settings_api_key").performTextClearance()
        composeRule.onNodeWithTag("settings_api_key").performTextInput(apiKey)
        composeRule.onNodeWithTag("settings_base_url").performTextClearance()
        composeRule.onNodeWithTag("settings_base_url").performTextInput(baseUrl)
        composeRule.onNodeWithTag("settings_save").assertExists().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("settings_save").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithContentDescription("Toggle visibility").performClick()
        composeRule.onNodeWithTag("settings_api_key").assertTextContains(apiKey)
        composeRule.onNodeWithTag("settings_base_url").assertTextContains(baseUrl)
    }

    @Test
    fun settingsFlow_loggedInSiteCanLogout() {
        cookieVault.saveCookies(
            site = "bilibili",
            domain = ".bilibili.com",
            cookies = "SESSDATA=test-session; bili_jct=test-csrf"
        )

        openSettings()

        composeRule.onNodeWithTag("site_bilibili_title").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("site_bilibili_status").assertTextContains("已登录")
        composeRule.onNodeWithTag("site_bilibili_logout").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            !cookieVault.isLoggedIn("bilibili")
        }

        composeRule.onNodeWithTag("site_bilibili_status").assertTextContains("未登录")
        composeRule.onNodeWithTag("site_bilibili_login").assertIsDisplayed()
        assertThat(cookieVault.isLoggedIn("bilibili")).isFalse()
    }

    @Test
    fun settingsFlow_loginButtonLaunchesWebLoginActivity() {
        clearSiteCookies()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(WebLoginActivity::class.java.name, null, false)

        try {
            openSettings()
            composeRule.onNodeWithTag("site_bilibili_login").performScrollTo().performClick()

            val launchedActivity = instrumentation.waitForMonitorWithTimeout(monitor, 10_000)
            assertThat(launchedActivity).isNotNull()
            launchedActivity as Activity
            assertThat(launchedActivity.intent.getStringExtra(WebLoginActivity.EXTRA_SITE)).isEqualTo("bilibili")
            launchedActivity.finish()
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun openSettings() {
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    private fun clearSiteCookies() {
        listOf("bilibili", "zhihu", "weibo", "xiaohongshu", "douban", "v2ex").forEach(cookieVault::logout)
    }
}
