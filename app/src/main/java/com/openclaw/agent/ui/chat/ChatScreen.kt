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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // ── Voice input (STT) state ──────────────────────────────────────────
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var hasAudioPermission by remember { mutableStateOf(false) }

    // SpeechRecognizer kept across recompositions, destroyed on leave
    val speechRecognizer = remember {
        SpeechRecognizer.createSpeechRecognizer(context)
    }
    DisposableEffect(Unit) {
        onDispose { speechRecognizer.destroy() }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            // Permission just granted – start listening immediately
            startListening(speechRecognizer, context, onResult = { result ->
                inputText = result
                isListening = false
            }, onError = {
                isListening = false
            })
            isListening = true
        }
    }

    // Helper: kick off recognition
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
                // Calculate total items: messages + tool calls + streaming bubble + thinking
                val extraItems = (if (activeToolCalls.isNotEmpty()) 1 else 0) +
                    (if (isStreaming && streamingText.isNotEmpty()) 1 else 0) +
                    (if (isStreaming && streamingText.isEmpty() && activeToolCalls.isEmpty()) 1 else 0)
                listState.animateScrollToItem(maxOf(0, messages.size - 1 + extraItems))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat", maxLines = 1) },
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
        ) {
            // Messages list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    // Skip tool-internal messages (tool_result) in display
                    if (message.toolResultJson != null) return@items

                    MessageBubble(
                        content = message.content,
                        isUser = message.role == "user",
                        isError = message.isError
                    )
                }

                // Active tool calls
                if (activeToolCalls.isNotEmpty()) {
                    item(key = "tool_calls") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            activeToolCalls.forEach { tc ->
                                ToolCallCard(toolCall = tc)
                            }
                        }
                    }
                }

                // Streaming bubble
                if (isStreaming && streamingText.isNotEmpty()) {
                    item(key = "streaming") {
                        MessageBubble(
                            content = streamingText,
                            isUser = false,
                            isStreaming = true
                        )
                    }
                }

                // Thinking indicator
                if (isStreaming && streamingText.isEmpty() && activeToolCalls.isEmpty()) {
                    item(key = "thinking") {
                        Row(
                            modifier = Modifier.padding(start = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Thinking...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Error banner
            error?.let { errorMsg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        errorMsg,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Input bar
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message...") },
                        maxLines = 5,
                        enabled = !isStreaming,
                        shape = MaterialTheme.shapes.extraLarge
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // 🎤 Microphone button
                    IconButton(
                        onClick = {
                            if (isListening) {
                                speechRecognizer.stopListening()
                                isListening = false
                            } else {
                                beginVoiceInput()
                            }
                        },
                        enabled = !isStreaming
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop recording" else "Voice input",
                            tint = if (isListening)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Send button
                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText.trim())
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank() && !isStreaming
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send")
                    }
                }
            }
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

        override fun onError(error: Int) {
            onError()
        }

        override fun onResults(results: Bundle?) {
            val matches = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                onResult(text)
            } else {
                onError()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    })

    speechRecognizer.startListening(intent)
}
