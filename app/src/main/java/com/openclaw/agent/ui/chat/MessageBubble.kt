package com.openclaw.agent.ui.chat

import android.speech.tts.TextToSpeech
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import java.util.Locale

@Composable
fun MessageBubble(
    content: String,
    isUser: Boolean,
    isError: Boolean = false,
    isStreaming: Boolean = false
) {
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val containerColor = when {
        isError -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        isError -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = containerColor,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(MaterialTheme.shapes.large)
        ) {
            if (isUser) {
                Text(
                    text = content,
                    modifier = Modifier.padding(12.dp),
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Column {
                    MarkdownText(
                        markdown = content,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                        color = textColor
                    )
                    // TTS button for assistant messages (not while streaming)
                    if (!isStreaming && content.isNotBlank()) {
                        TtsButton(
                            text = content,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(end = 4.dp, bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TtsButton(
    text: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isSpeaking by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { t ->
                    // Try Chinese first, fallback to default
                    val result = t.setLanguage(Locale.CHINESE)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        t.setLanguage(Locale.getDefault())
                    }
                }
            }
        }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }

    IconButton(
        onClick = {
            tts?.let { t ->
                if (isSpeaking) {
                    t.stop()
                    isSpeaking = false
                } else {
                    // Strip markdown formatting for cleaner speech
                    val cleanText = text
                        .replace(Regex("[*_~`#>|]"), "")
                        .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
                        .replace(Regex("!\\[([^]]*)]\\([^)]+\\)"), "$1")
                        .trim()
                    t.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "msg_tts")
                    isSpeaking = true
                    t.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) { isSpeaking = false }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) { isSpeaking = false }
                    })
                }
            }
        },
        modifier = modifier.size(32.dp)
    ) {
        Icon(
            if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
            contentDescription = if (isSpeaking) "Stop" else "Read aloud",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color
) {
    val context = LocalContext.current
    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .build()
    }

    val argbColor = android.graphics.Color.argb(
        (color.alpha * 255).toInt(),
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt()
    )

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(argbColor)
                textSize = 16f
                setLineSpacing(0f, 1.2f)
            }
        },
        update = { textView ->
            markwon.setMarkdown(textView, markdown)
            textView.setTextColor(argbColor)
        }
    )
}
