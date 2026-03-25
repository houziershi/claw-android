package com.openclaw.agent.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.openclaw.agent.ui.theme.ClawShapes
import com.openclaw.agent.ui.theme.ClawSpacing
import com.openclaw.agent.ui.theme.LocalClawColors
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    content: String,
    isUser: Boolean,
    isError: Boolean = false,
    isStreaming: Boolean = false,
    isFirstInGroup: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clawColors = LocalClawColors.current
    val alignment = if (isUser) Alignment.End else Alignment.Start

    val shape = when {
        isError -> ClawShapes.bubbleContinue
        isUser -> if (isFirstInGroup) ClawShapes.bubbleUser else ClawShapes.bubbleContinue
        else -> if (isFirstInGroup) ClawShapes.bubbleBot else ClawShapes.bubbleContinue
    }

    val containerColor = when {
        isError -> MaterialTheme.colorScheme.errorContainer
        isUser -> clawColors.userBubble
        else -> clawColors.botBubble
    }

    val textColor: Color = when {
        isError -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> clawColors.userBubbleText
        else -> clawColors.botBubbleText
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = containerColor,
            shape = shape,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .clip(shape)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { copyToClipboard(context, content) }
                )
        ) {
            if (isUser) {
                SelectionContainer {
                    Text(
                        text = content,
                        modifier = Modifier.padding(
                            horizontal = ClawSpacing.bubbleHPadding,
                            vertical = ClawSpacing.bubbleVPadding
                        ),
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Column {
                    MarkdownText(
                        markdown = content,
                        modifier = Modifier.padding(
                            start = ClawSpacing.bubbleHPadding,
                            end = ClawSpacing.bubbleHPadding,
                            top = ClawSpacing.bubbleVPadding,
                            bottom = 4.dp
                        ),
                        color = textColor,
                        selectable = true
                    )
                    if (!isStreaming && content.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(end = 4.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            IconButton(
                                onClick = { copyToClipboard(context, content) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    modifier = Modifier.size(14.dp),
                                    tint = clawColors.botBubbleText.copy(alpha = 0.5f)
                                )
                            }
                            TtsButton(
                                text = content,
                                iconTint = clawColors.botBubbleText.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val cleanText = text
        .replace(Regex("[*_~`#>|]"), "")
        .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
        .replace(Regex("!\\[([^]]*)]\\([^)]+\\)"), "$1")
        .trim()
    clipboard.setPrimaryClip(ClipData.newPlainText("Claw Message", cleanText))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

@Composable
private fun TtsButton(
    text: String,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isSpeaking by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { t ->
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
            tint = iconTint
        )
    }
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color,
    selectable: Boolean = false
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
                setTextIsSelectable(selectable)
            }
        },
        update = { textView ->
            markwon.setMarkdown(textView, markdown)
            textView.setTextColor(argbColor)
        }
    )
}
