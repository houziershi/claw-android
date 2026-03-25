package com.openclaw.agent.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object ClawSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    // Chat-specific
    val bubbleHPadding = 14.dp
    val bubbleVPadding = 10.dp
    val bubbleSpacing = 4.dp
    val groupSpacing = 16.dp
    val avatarSize = 32.dp
    val avatarGap = 10.dp
}

object ClawShapes {
    val bubbleUser = RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
    val bubbleBot = RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
    val bubbleContinue = RoundedCornerShape(20.dp)
    val card = RoundedCornerShape(12.dp)
    val cardSmall = RoundedCornerShape(8.dp)
    val input = RoundedCornerShape(24.dp)
    val button = RoundedCornerShape(12.dp)
    val chip = RoundedCornerShape(8.dp)
    val sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
}
