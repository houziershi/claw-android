package com.openclaw.agent

import android.app.Application
import com.openclaw.agent.core.memory.FileMemoryStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OpenClawApp : Application() {

    @Inject
    lateinit var memoryStore: FileMemoryStore

    override fun onCreate() {
        super.onCreate()
        memoryStore.initializeDefaultFiles()
    }
}
