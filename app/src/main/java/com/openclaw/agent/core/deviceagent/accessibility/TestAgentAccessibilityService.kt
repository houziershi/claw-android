package com.openclaw.agent.core.deviceagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.openclaw.agent.core.deviceagent.capability.AccessibilityCapability
import com.openclaw.agent.core.deviceagent.model.UiLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

private const val TAG = "TestAgentA11yService"

class TestAgentAccessibilityService : AccessibilityService(), AccessibilityCapability {

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceRef = WeakReference(this)
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Phase 1 skeleton: keep lightweight until snapshot/action layers arrive.
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceRef = null
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.Default) {
        val expectedComponent = ComponentName(this@TestAgentAccessibilityService, TestAgentAccessibilityService::class.java)
            .flattenToString()
        isEnabled(this@TestAgentAccessibilityService, expectedComponent)
    }

    fun currentRootNode(): AccessibilityNodeInfo? = rootInActiveWindow

    fun findNode(locator: UiLocator): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val match = AccessibilityNodeFinder.findFirst(AccessibilityNodeInfoAdapter(root), locator)
        return (match as? AccessibilityNodeInfoAdapter)?.node
    }

    fun click(locator: UiLocator): Boolean {
        val root = rootInActiveWindow ?: return false
        val match = AccessibilityNodeFinder.findFirst(AccessibilityNodeInfoAdapter(root), locator)
        return AccessibilityNodeFinder.clickNearestClickable(match)
    }

    fun setText(locator: UiLocator, text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val match = AccessibilityNodeFinder.findFirst(AccessibilityNodeInfoAdapter(root), locator)
        return AccessibilityNodeFinder.setText(match, text)
    }

    fun scroll(locator: UiLocator, direction: ScrollDirection): Boolean {
        val root = rootInActiveWindow ?: return false
        val match = AccessibilityNodeFinder.findFirst(AccessibilityNodeInfoAdapter(root), locator)
        return AccessibilityNodeFinder.scroll(match, direction)
    }

    fun performGlobal(action: AccessibilityGlobalAction): Boolean {
        return AccessibilityGlobalActionDispatcher(
            GlobalActionPerformer { platformAction -> super.performGlobalAction(platformAction) }
        ).dispatch(action)
    }

    companion object {
        @Volatile
        private var serviceRef: WeakReference<TestAgentAccessibilityService>? = null

        fun current(): TestAgentAccessibilityService? = serviceRef?.get()

        fun isEnabled(context: Context, expectedComponent: String): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return AccessibilityServiceStatus.isServiceEnabled(enabledServices, expectedComponent)
        }
    }
}
