package com.openclaw.agent.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

/**
 * Three-dot bouncing animation shown while waiting for the first token.
 * Similar to iMessage/WhatsApp typing indicator.
 */
@Composable
fun ReadingIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    val dotAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at 0
                1f at 200
                0.3f at 400
                0.3f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot1"
    )

    val dotAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at 0
                0.3f at 200
                1f at 400
                0.3f at 600
                0.3f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot2"
    )

    val dotAlpha3 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at 0
                0.3f at 400
                1f at 600
                0.3f at 800
                0.3f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot3"
    )

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Dot(alpha = dotAlpha1)
            Dot(alpha = dotAlpha2)
            Dot(alpha = dotAlpha3)
        }
    }
}

@Composable
private fun Dot(alpha: Float) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .alpha(alpha)
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = CircleShape
            )
    )
}

@Composable
private fun Surface(
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color,
    shape: androidx.compose.ui.graphics.Shape,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        color = color,
        shape = shape,
        content = content
    )
}
