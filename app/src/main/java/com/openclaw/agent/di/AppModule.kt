package com.openclaw.agent.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.openclaw.agent.core.memory.FileMemoryStore
import com.openclaw.agent.core.memory.MemoryStore
import com.openclaw.agent.core.tools.ToolRegistry
import com.openclaw.agent.core.tools.impl.*
import com.openclaw.agent.data.db.AppDatabase
import com.openclaw.agent.data.db.MessageDao
import com.openclaw.agent.data.db.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS) // Long timeout for streaming
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration()
        .build()

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides
    @Singleton
    fun provideMemoryStore(@ApplicationContext context: Context): FileMemoryStore =
        FileMemoryStore(context)

    @Provides
    @Singleton
    fun bindMemoryStore(impl: FileMemoryStore): MemoryStore = impl

    @Provides
    @Singleton
    fun provideToolRegistry(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        memoryStore: MemoryStore
    ): ToolRegistry {
        val registry = ToolRegistry()

        // Device tools
        registry.register(CurrentTimeTool())
        registry.register(DeviceInfoTool(context))
        registry.register(ClipboardTool(context))
        registry.register(VolumeTool(context))
        registry.register(AlarmTool(context))
        registry.register(BluetoothTool(context))

        // Web tools
        registry.register(WebSearchTool(okHttpClient))
        registry.register(WebFetchTool(okHttpClient))

        // Memory tools
        registry.register(MemoryReadTool(memoryStore))
        registry.register(MemoryWriteTool(memoryStore))
        registry.register(MemorySearchTool(memoryStore))
        registry.register(MemoryListTool(memoryStore))

        return registry
    }
}
