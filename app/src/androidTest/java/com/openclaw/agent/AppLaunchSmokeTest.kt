package com.openclaw.agent

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.openclaw.agent.testing.DeviceWakeup
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLaunchSmokeTest {

    @Test
    fun mainActivity_launchesIntoResumedState() {
        DeviceWakeup.ensureDeviceAwake()
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        try {
            assertThat(scenario.state).isEqualTo(Lifecycle.State.RESUMED)

            scenario.onActivity { activity ->
                assertThat(activity.isFinishing).isFalse()
                assertThat(activity.isDestroyed).isFalse()
                assertThat(activity.window).isNotNull()
                assertThat(activity.applicationContext.packageName).isEqualTo("com.openclaw.agent")
            }
        } finally {
            scenario.close()
        }
    }
}
