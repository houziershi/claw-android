package com.openclaw.agent.testing

import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import org.junit.rules.TestWatcher
import org.junit.runner.Description

object DeviceWakeup {
    fun ensureDeviceAwake() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        runShell("input keyevent KEYCODE_WAKEUP")
        runShell("wm dismiss-keyguard")
        runShell("input keyevent 82")
        instrumentation.waitForIdleSync()
    }

    private fun runShell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }
    }
}

class DeviceWakeupRule : TestWatcher() {
    override fun starting(description: Description) {
        DeviceWakeup.ensureDeviceAwake()
    }
}
