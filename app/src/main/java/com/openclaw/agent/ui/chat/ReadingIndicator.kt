package com.openclaw.agent.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.openclaw.agent.ui.theme.ClawShapes
import com.openclaw.agent.ui.theme.Neutral400

/**
 * Three-dot pulse animation shown while waiting for the first token.
 * Dots scale up/down with staggered timing.
 */
@Composable
fun ReadingIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    val dotScale1 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.6f at 0
                1f at 200
                0.6f at 400
                0.6f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot1"
    )

    val dotScale2 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.6f at 0
                0.6f at 200
                1f at 400
                0.6f at 600
                0.6f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot2"
    )

    val dotScale3 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.6f at 0
                0.6f at 400
                1f at 600
                0.6f at 800
                0.6f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot3"
    )

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = ClawShapes.bubbleBot
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PulseDot(scale = dotScale1)
            PulseDot(scale = dotScale2)
            PulseDot(scale = dotScale3)
        }
    }
}

@Composable
private fun PulseDot(scale: Float) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .scale(scale)
            .background(color = Neutral400, shape = CircleShape)
    )
}
