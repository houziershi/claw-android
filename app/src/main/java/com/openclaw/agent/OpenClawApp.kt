package com.openclaw.agent

import android.app.Application
import com.openclaw.agent.core.memory.FileMemoryStore
import com.openclaw.agent.core.skill.SkillEngine
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OpenClawApp : Application() {

    @Inject
    lateinit var memoryStore: FileMemoryStore

    @Inject
    lateinit var skillEngine: SkillEngine

    override fun onCreate() {
        super.onCreate()
        memoryStore.initializeDefaultFiles()
        skillEngine.loadSkills()
    }
}
