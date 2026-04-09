package com.openclaw.agent.ui.settings

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.openclaw.agent.MainActivity
import com.openclaw.agent.core.web.cookie.CookieVault
import com.openclaw.agent.testing.DeviceWakeupRule
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class SettingsFlowDemoInstrumentedTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()
    private val appContext = ApplicationProvider.getApplicationContext<Context>()
    private val cookieVault = CookieVault(appContext)

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(DeviceWakeupRule())
        .around(composeRule)

    @After
    fun tearDown() {
        listOf("bilibili", "zhihu", "weibo", "xiaohongshu", "douban", "v2ex").forEach(cookieVault::logout)
    }

    @Test
    fun demo_settingsFlow_runsSlowlyForScreenRecording() {
        val apiKey = "sk-demo-visible-test"
        val baseUrl = "https://example.com/v1/messages"

        pause("home_visible")
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()

        pause("before_open_settings")
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()

        pause("before_edit_api_key")
        composeRule.onNodeWithTag("settings_api_key").performTextClearance()
        pause("after_clear_api_key")
        composeRule.onNodeWithTag("settings_api_key").performTextInput(apiKey)

        pause("before_edit_base_url")
        composeRule.onNodeWithTag("settings_base_url").performTextClearance()
        pause("after_clear_base_url")
        composeRule.onNodeWithTag("settings_base_url").performTextInput(baseUrl)

        pause("before_save")
        composeRule.onNodeWithTag("settings_save").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("settings_save").fetchSemanticsNodes().isEmpty()
        }

        pause("before_toggle_visibility")
        composeRule.onNodeWithContentDescription("Toggle visibility").performClick()
        pause("after_toggle_visibility")

        composeRule.onNodeWithTag("settings_api_key").assertTextContains("sk-demo-visible-test")

        pause("demo_complete")
    }

    private fun pause(label: String, millis: Long = 1_500L) {
        composeRule.waitForIdle()
        SystemClock.sleep(millis)
    }
}
