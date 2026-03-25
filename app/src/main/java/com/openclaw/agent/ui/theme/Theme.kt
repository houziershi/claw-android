package com.openclaw.agent.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

data class ClawExtraColors(
    val userBubble: Color,
    val userBubbleText: Color,
    val botBubble: Color,
    val botBubbleText: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
)

val LocalClawColors = staticCompositionLocalOf {
    ClawExtraColors(
        userBubble = UserBubble,
        userBubbleText = UserBubbleText,
        botBubble = BotBubble,
        botBubbleText = BotBubbleText,
        success = Success,
        warning = Warning,
        info = Info,
    )
}

private val ClawDarkColorScheme = darkColorScheme(
    primary = Claw400,
    onPrimary = Color.White,
    primaryContainer = Claw700,
    onPrimaryContainer = Claw100,
    secondary = Claw300,
    onSecondary = Color.White,
    secondaryContainer = Neutral750,
    onSecondaryContainer = Neutral200,
    background = Neutral900,
    surface = Neutral850,
    surfaceVariant = Neutral800,
    surfaceContainerHighest = Neutral750,
    onBackground = Neutral200,
    onSurface = Neutral200,
    onSurfaceVariant = Neutral400,
    outline = Neutral700,
    outlineVariant = Neutral700,
    error = Error,
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val ClawLightColorScheme = lightColorScheme(
    primary = Color(0xFFE65100),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFCC80),
    onPrimaryContainer = Color(0xFF331200),
    secondary = Color(0xFF8B5E3C),
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceAlt,
    onSecondaryContainer = LightText,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceAlt,
    surfaceContainerHighest = LightBorder,
    onBackground = LightText,
    onSurface = LightText,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = Error,
    errorContainer = Color(0xFFFFDAD6),
    onError = Color.White,
    onErrorContainer = Color(0xFF93000A),
)

@Composable
fun ClawTheme(
    themeMode: String = "system",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ClawDarkColorScheme
        else -> ClawLightColorScheme
    }

    val extraColors = if (darkTheme) {
        ClawExtraColors(
            userBubble = UserBubble,
            userBubbleText = UserBubbleText,
            botBubble = BotBubble,
            botBubbleText = BotBubbleText,
            success = Success,
            warning = Warning,
            info = Info,
        )
    } else {
        ClawExtraColors(
            userBubble = LightUserBubble,
            userBubbleText = Color.White,
            botBubble = LightBotBubble,
            botBubbleText = LightBotBubbleText,
            success = Success,
            warning = Warning,
            info = Info,
        )
    }

    CompositionLocalProvider(LocalClawColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ClawTypography,
            content = content
        )
    }
}
