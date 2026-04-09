package com.openclaw.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeInstrumentationTest {
    @Test
    fun appContext_usesAppPackage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertThat(context.packageName).isEqualTo("com.openclaw.agent")
    }
}
