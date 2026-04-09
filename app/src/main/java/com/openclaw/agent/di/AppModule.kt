package com.openclaw.agent.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.openclaw.agent.core.memory.FileMemoryStore
import com.openclaw.agent.core.memory.MemoryStore
import com.openclaw.agent.core.mijia.MijiaApiClient
import com.openclaw.agent.core.mijia.MijiaAuthStore
import com.openclaw.agent.core.mijia.MijiaTokenRefresher
import com.openclaw.agent.core.mijia.MiotSpecCache
import com.openclaw.agent.core.tools.ToolRegistry
import com.openclaw.agent.core.tools.impl.*
import com.openclaw.agent.core.web.AdapterRegistry
import com.openclaw.agent.core.web.adapters.ArxivAdapter
import com.openclaw.agent.core.web.adapters.BbcNewsAdapter
import com.openclaw.agent.core.web.adapters.HackerNewsAdapter
import com.openclaw.agent.core.web.adapters.StackOverflowAdapter
import com.openclaw.agent.core.web.adapters.WikipediaAdapter
import com.openclaw.agent.core.web.adapters.YahooFinanceAdapter
import com.openclaw.agent.core.web.adapters.BilibiliAdapter
import com.openclaw.agent.core.web.adapters.ZhihuAdapter
import com.openclaw.agent.core.web.adapters.WeiboAdapter
import com.openclaw.agent.core.web.cookie.CookieVault
import com.openclaw.agent.core.web.pipeline.PipelineRunner
import com.openclaw.agent.core.web.pipeline.SimpleYamlParser
import com.openclaw.agent.core.web.pipeline.TemplateEngine
import com.openclaw.agent.core.web.pipeline.YamlAdapterLoader
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
        ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
        .build()

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideScheduledTaskDao(db: AppDatabase): com.openclaw.agent.data.db.ScheduledTaskDao = db.scheduledTaskDao()

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
    fun provideMijiaAuthStore(@ApplicationContext context: Context): MijiaAuthStore =
        MijiaAuthStore(context)

    @Provides
    @Singleton
    fun provideMijiaApiClient(
        okHttpClient: OkHttpClient,
        authStore: MijiaAuthStore
    ): MijiaApiClient = MijiaApiClient(okHttpClient, authStore)

    @Provides
    @Singleton
    fun provideMijiaTokenRefresher(
        okHttpClient: OkHttpClient,
        authStore: MijiaAuthStore
    ): MijiaTokenRefresher = MijiaTokenRefresher(okHttpClient, authStore)

    @Provides
    @Singleton
    fun provideMiotSpecCache(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): MiotSpecCache = MiotSpecCache(context, okHttpClient)

    @Provides
    @Singleton
    fun provideCookieVault(@ApplicationContext context: Context): CookieVault =
        CookieVault(context)

    @Provides
    @Singleton
    fun provideAdapterRegistry(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        cookieVault: CookieVault
    ): AdapterRegistry {
        val registry = AdapterRegistry()

        // Phase 1: Kotlin adapters — public API (no login)
        registry.register(HackerNewsAdapter(okHttpClient))
        registry.register(WikipediaAdapter(okHttpClient))
        registry.register(ArxivAdapter(okHttpClient))
        registry.register(BbcNewsAdapter(okHttpClient))
        registry.register(StackOverflowAdapter(okHttpClient))
        registry.register(YahooFinanceAdapter(okHttpClient))

        // Phase 3: Cookie-based adapters (require login)
        registry.register(BilibiliAdapter(okHttpClient, cookieVault))
        registry.register(ZhihuAdapter(okHttpClient, cookieVault))
        registry.register(WeiboAdapter(okHttpClient, cookieVault))

        // Phase 2: YAML pipeline adapters (skip if site already registered)
        val templateEngine = TemplateEngine()
        val pipelineRunner = PipelineRunner(okHttpClient, templateEngine)
        val yamlParser = SimpleYamlParser()
        val yamlLoader = YamlAdapterLoader(yamlParser, pipelineRunner)
        val existingSites = registry.getAllAdapters().map { it.site }.toSet()
        yamlLoader.loadFromAssets(context, "adapters").forEach { adapter ->
            if (adapter.site !in existingSites) {
                registry.register(adapter)
            }
        }

        return registry
    }

    @Provides
    @Singleton
    fun provideToolRegistry(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        memoryStore: MemoryStore,
        mijiaApiClient: MijiaApiClient,
        miotSpecCache: MiotSpecCache,
        adapterRegistry: AdapterRegistry
    ): ToolRegistry {
        val registry = ToolRegistry()

        // Device tools
        registry.register(CurrentTimeTool())
        registry.register(DeviceInfoTool(context))
        registry.register(ClipboardTool(context))
        registry.register(VolumeTool(context))
        registry.register(AlarmTool(context))
        registry.register(BluetoothTool(context))
        registry.register(DndTool(context))

        // Web tools
        registry.register(WebSearchTool(okHttpClient))
        registry.register(WebFetchTool(okHttpClient))

        // Memory tools
        registry.register(MemoryReadTool(memoryStore))
        registry.register(MemoryWriteTool(memoryStore))
        registry.register(MemorySearchTool(memoryStore))
        registry.register(MemoryListTool(memoryStore))

        // System tools (no extra permissions needed)
        registry.register(FlashlightTool(context))
        registry.register(AppLauncherTool(context))
        registry.register(IntentTool(context))
        registry.register(BrightnessTool(context))
        registry.register(WiFiTool(context))

        // PIM tools (permissions already declared)
        registry.register(ContactsTool(context))
        registry.register(CalendarTool(context))
        registry.register(LocationTool(context))
        registry.register(MediaTool(context))

        // Notification & system tools
        registry.register(NotificationTool(context))
        registry.register(UsageStatsTool(context))
        registry.register(ScreenCaptureTool(context))
        registry.register(SystemActionTool(context))
        val androidDeviceCapabilitySet = AndroidDeviceCapabilitySet(context)
        val snapshotSource = AccessibilityServiceSnapshotSource(context)
        registry.register(
            DeviceSnapshotTool(
                snapshotSource = snapshotSource,
                capabilityStatusSource = CapabilitySetStatusSource(androidDeviceCapabilitySet.capabilities)
            )
        )
        val deviceArtifactStore = com.openclaw.agent.core.deviceagent.report.ArtifactStore(
            context.filesDir.toPath().resolve("deviceagent-artifacts")
        )
        registry.register(
            DeviceExecutePlanTool(
                planExecutor = DefaultDevicePlanExecutor(
                    actionExecutor = com.openclaw.agent.core.deviceagent.execution.ActionExecutor(
                        handlers = listOf(
                            com.openclaw.agent.core.deviceagent.execution.AccessibilityActionRouteHandler(),
                            com.openclaw.agent.core.deviceagent.execution.AppControlActionRouteHandler(context),
                            com.openclaw.agent.core.deviceagent.shell.ShellActionRouteHandler(
                                shellRunner = com.openclaw.agent.core.deviceagent.shell.ShellCommandRunner()
                            )
                        )
                    ),
                    capabilitySet = androidDeviceCapabilitySet.capabilities,
                    recorder = com.openclaw.agent.core.deviceagent.report.ExecutionRecorder(deviceArtifactStore),
                    formatter = com.openclaw.agent.core.deviceagent.report.ExecutionSummaryFormatter(),
                    snapshotSource = snapshotSource
                )
            )
        )
        registry.register(
            DeviceCollectArtifactsTool(
                collector = DefaultDeviceArtifactCollector(
                    artifactStore = deviceArtifactStore,
                    snapshotSource = snapshotSource,
                    logcatSource = AndroidLogcatSource(context)
                )
            )
        )

        // Mijia smart home tools
        registry.register(MijiaListDevicesTool(mijiaApiClient))
        registry.register(MijiaControlTool(mijiaApiClient, miotSpecCache))
        registry.register(MijiaDeviceInfoTool(mijiaApiClient, miotSpecCache))
        registry.register(MijiaSceneTool(mijiaApiClient))

        // Web adapter tools
        registry.register(ListAdaptersTool(adapterRegistry))
        registry.register(QueryWebTool(adapterRegistry))

        return registry
    }
}
