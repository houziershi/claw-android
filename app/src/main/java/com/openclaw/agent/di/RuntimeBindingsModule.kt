package com.openclaw.agent.di

import com.openclaw.agent.core.runtime.AgentRuntime
import com.openclaw.agent.core.runtime.ChatRuntime
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RuntimeBindingsModule {
    @Binds
    @Singleton
    abstract fun bindChatRuntime(impl: AgentRuntime): ChatRuntime
}
