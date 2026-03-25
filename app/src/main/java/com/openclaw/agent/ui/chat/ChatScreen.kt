package com.openclaw.agent.ui.chat

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openclaw.agent.ui.components.AssistantAvatar
import com.openclaw.agent.ui.theme.ClawShapes
import com.openclaw.agent.ui.theme.ClawSpacing
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    val isStreaming by viewModel.isStreaming.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val error by viewModel.error.collectAsState()
    val activeToolCalls by viewModel.activeToolCalls.collectAsState()
    val showToolCalls by viewModel.showToolCalls.collectAsState(initial = false)
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // ── Tool output sheet state ──────────────────────────────────────────
    var selectedToolCall by remember { mutableStateOf<ToolCallUiState?>(null) }
    val showToolSheet = selectedToolCall != null

    // ── Voice input (STT) state ──────────────────────────────────────────
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var hasAudioPermission by remember { mutableStateOf(false) }

    val speechRecognizer = remember {
        SpeechRecognizer.createSpeechRecognizer(context)
    }
    DisposableEffect(Unit) {
        onDispose { speechRecognizer.destroy() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            startListening(speechRecognizer, context, onResult = { result ->
                inputText = result
                isListening = false
            }, onError = {
                isListening = false
            })
            isListening = true
        }
    }

    fun beginVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        if (hasAudioPermission) {
            startListening(speechRecognizer, context, onResult = { result ->
                inputText = result
                isListening = false
            }, onError = {
                isListening = false
            })
            isListening = true
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // ── End voice input state ────────────────────────────────────────────

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }

    // Auto-scroll to bottom on new messages / tool calls / streaming
    LaunchedEffect(messages.size, streamingText, activeToolCalls.size) {
        if (messages.isNotEmpty()) {
            scope.launch {
                val chatItems = ChatItemBuilder.build(messages, showToolCalls)
                val extraItems = (if (activeToolCalls.isNotEmpty()) 1 else 0) +
                    (if (isStreaming && streamingText.isNotEmpty()) 1 else 0) +
                    (if (isStreaming && streamingText.isEmpty() && activeToolCalls.isEmpty()) 1 else 0)
                listState.animateScrollToItem(maxOf(0, chatItems.size - 1 + extraItems))
            }
        }
    }

    // Model name from last message for TopBar badge
    val lastModel = remember(messages) {
        messages.lastOrNull { it.model != null }?.model
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ClawSpacing.sm)
                    ) {
                        Text("Chat", maxLines = 1)
                        if (!lastModel.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = ClawShapes.chip
                            ) {
                                Text(
                                    text = lastModel
                                        .removePrefix("claude-")
                                        .replace(Regex("-\\d{8}$"), ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            val chatItems = remember(messages, showToolCalls) {
                ChatItemBuilder.build(messages, showToolCalls)
            }

            // Messages list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = ClawSpacing.lg, vertical = ClawSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(ClawSpacing.groupSpacing)
            ) {
                items(chatItems.size, key = { chatItems[it].key }) { index ->
                    when (val item = chatItems[index]) {
                        is ChatItem.DateSeparator -> DateDivider(
                            date = item.date,
                            modifier = Modifier.animateItem()
                        )
                        is ChatItem.MessageGroup -> MessageGroupView(
                            group = item,
                            modifier = Modifier.animateItem()
                        )
                        is ChatItem.ToolCallsBlock -> { /* reserved */ }
                    }
                }

                // Active tool calls (collapsible)
                if (activeToolCalls.isNotEmpty()) {
                    item(key = "tool_calls") {
                        CollapsibleToolCards(
                            toolCalls = activeToolCalls,
                            onToolClick = { selectedToolCall = it }
                        )
                    }
                }

                // Streaming bubble (with avatar layout for consistency)
                if (isStreaming && streamingText.isNotEmpty()) {
                    item(key = "streaming") {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            AssistantAvatar()
                            Spacer(Modifier.width(ClawSpacing.avatarGap))
                            Column(modifier = Modifier.weight(1f)) {
                                MessageBubble(
                                    content = streamingText,
                                    isUser = false,
                                    isStreaming = true
                                )
                            }
                        }
                    }
                }

                // Thinking indicator (three-dot pulse)
                if (isStreaming && streamingText.isEmpty() && activeToolCalls.isEmpty()) {
                    item(key = "thinking") {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            AssistantAvatar()
                            Spacer(Modifier.width(ClawSpacing.avatarGap))
                            ReadingIndicator()
                        }
                    }
                }
            }

            // Error banner with Retry button
            error?.let { errorMsg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = ClawSpacing.md, vertical = ClawSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            errorMsg,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { viewModel.retryLastMessage() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("重试")
                        }
                    }
                }
            }

            // Input bar
            ChatInputBar(
                inputText = inputText,
                onInputChange = { inputText = it },
                isStreaming = isStreaming,
                isListening = isListening,
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText.trim())
                        inputText = ""
                    }
                },
                onStop = { viewModel.stopGeneration() },
                onVoiceToggle = {
                    if (isListening) {
                        speechRecognizer.stopListening()
                        isListening = false
                    } else {
                        beginVoiceInput()
                    }
                }
            )
        }

        // ── Tool Output BottomSheet ──────────────────────────────────────────
        if (showToolSheet && selectedToolCall != null) {
            ToolOutputSheet(
                toolCall = selectedToolCall!!,
                onDismiss = { selectedToolCall = null }
            )
        }
    }
}

// ── Private helper: configure and start the SpeechRecognizer ─────────────
private fun startListening(
    speechRecognizer: SpeechRecognizer,
    context: android.content.Context,
    onResult: (String) -> Unit,
    onError: () -> Unit
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }

    speechRecognizer.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) { onError() }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull().orEmpty()
            if (text.isNotBlank()) onResult(text) else onError()
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    })

    speechRecognizer.startListening(intent)
}
