package com.openclaw.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.openclaw.agent.data.preferences.SettingsStore
import com.openclaw.agent.ui.ClawApp
import com.openclaw.agent.ui.theme.ClawTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsStore.themeModeFlow.collectAsState(initial = "system")
            ClawTheme(themeMode = themeMode) {
                ClawApp()
            }
        }
    }
}
