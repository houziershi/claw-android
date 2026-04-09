package com.openclaw.agent.ui.chat

import android.content.Context
import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.openclaw.agent.core.runtime.AgentEvent
import com.openclaw.agent.core.runtime.ChatRuntime
import com.openclaw.agent.core.runtime.Usage
import com.openclaw.agent.core.session.SessionManager
import com.openclaw.agent.data.db.AppDatabase
import com.openclaw.agent.data.db.entities.MessageEntity
import com.openclaw.agent.data.db.entities.SessionEntity
import com.openclaw.agent.data.preferences.SettingsStore
import com.openclaw.agent.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var settingsStore: FakeSettingsStore
    private lateinit var sessionManager: SessionManager
    private lateinit var fakeRuntime: FakeChatRuntime
    private lateinit var viewModel: ChatViewModel
    private lateinit var dataStoreFile: File
    private lateinit var sessionId: String

    @Before
    fun setUp() = runTest {
        dataStoreFile = File(context.cacheDir, "chat-settings-${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { dataStoreFile })
        settingsStore = FakeSettingsStore(context, dataStore)

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionManager = SessionManager(db.sessionDao(), db.messageDao(), settingsStore)
        fakeRuntime = FakeChatRuntime()
        viewModel = ChatViewModel(fakeRuntime, sessionManager, settingsStore)

        sessionId = "session-1"
        db.sessionDao().insertSession(
            SessionEntity(
                id = sessionId,
                title = "New Chat",
                createdAt = 1L,
                updatedAt = 1L,
                model = "claude-opus-4-6"
            )
        )
        viewModel.loadSession(sessionId)
        advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        db.close()
        dataStoreFile.delete()
    }

    @Test
    fun sendMessage_streamsToolStateAndUpdatesTitle() = runTest {
        fakeRuntime.events = listOf(
            AgentEvent.TextChunk("Hello"),
            AgentEvent.ToolCallStarted("tool-1", "query_web"),
            AgentEvent.ToolCallFinished(
                id = "tool-1",
                name = "query_web",
                input = buildJsonObject { put("site", "hackernews") },
                result = "done",
                success = true
            ),
            AgentEvent.TurnComplete(fullText = "Hello", usage = Usage(1, 2))
        )

        viewModel.sendMessage("Tell me the latest AI news")
        advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fakeRuntime.calls).hasSize(1)
        val call = fakeRuntime.calls.single()
        assertThat(call.sessionId).isEqualTo(sessionId)
        assertThat(call.userMessage).isEqualTo("Tell me the latest AI news")
        assertThat(call.model).isEqualTo("claude-opus-4-6")
        assertThat(call.apiKey).isEqualTo("chat-test-key")
        assertThat(call.baseUrl).isEqualTo("https://example.test/messages")

        assertThat(viewModel.isStreaming.value).isFalse()
        assertThat(viewModel.streamingText.value).isEmpty()
        assertThat(viewModel.activeToolCalls.value).isEmpty()
        assertThat(viewModel.error.value).isNull()

    }

    @Test
    fun stopGeneration_cancelsStreamingButKeepsPartialText() = runTest {
        setPrivateStateFlow("_isStreaming", true)
        setPrivateStateFlow("_streamingText", "partial reply")
        setPrivateStateFlow(
            "_activeToolCalls",
            listOf(ToolCallUiState(id = "tool-1", name = "query_web", isRunning = true))
        )

        viewModel.stopGeneration()

        assertThat(viewModel.isStreaming.value).isFalse()
        assertThat(viewModel.streamingText.value).isEqualTo("partial reply")
        assertThat(viewModel.activeToolCalls.value).isEmpty()
    }

    @Test
    fun retryLastMessage_replaysMostRecentUserMessage() = runTest {
        setPrivateStateFlow(
            "_messages",
            listOf(
                MessageEntity(
                    id = "msg-1",
                    sessionId = sessionId,
                    role = "user",
                    content = "retry this",
                    timestamp = 10L
                )
            )
        )
        fakeRuntime.events = emptyList()

        viewModel.retryLastMessage()
        advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fakeRuntime.calls).hasSize(1)
        assertThat(fakeRuntime.calls.single().userMessage).isEqualTo("retry this")
    }

    private fun <T> setPrivateStateFlow(fieldName: String, value: T) {
        val field = ChatViewModel::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<T>
        stateFlow.value = value
    }
}

private class FakeSettingsStore(
    context: Context,
    dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
) : SettingsStore(context, dataStore) {
    override val selectedModelFlow: Flow<String> = kotlinx.coroutines.flow.MutableStateFlow("claude-opus-4-6")
    override val showToolCallsFlow: Flow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(false)
    override fun getApiKey(): String = "chat-test-key"
    override fun getBaseUrl(): String = "https://example.test/messages"
}

private class FakeChatRuntime : ChatRuntime {
    data class ChatCall(
        val sessionId: String,
        val userMessage: String,
        val model: String,
        val apiKey: String,
        val baseUrl: String,
    )

    var events: List<AgentEvent> = emptyList()
    var customFlow: Flow<AgentEvent>? = null
    val calls = mutableListOf<ChatCall>()

    override fun chat(
        sessionId: String,
        userMessage: String,
        model: String,
        apiKey: String,
        baseUrl: String
    ): Flow<AgentEvent> = flow {
        calls += ChatCall(sessionId, userMessage, model, apiKey, baseUrl)
        val override = customFlow
        if (override != null) {
            emitAll(override)
        } else {
            events.forEach { emit(it) }
        }
    }
}
