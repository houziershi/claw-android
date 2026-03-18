package com.openclaw.agent.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.openclaw.agent.ui.chat.ChatScreen
import com.openclaw.agent.ui.memory.MemoryScreen
import com.openclaw.agent.ui.sessions.SessionListScreen
import com.openclaw.agent.ui.settings.SettingsScreen

object Routes {
    const val SESSIONS = "sessions"
    const val CHAT = "chat/{sessionId}"
    const val SETTINGS = "settings"
    const val MEMORY = "memory"

    fun chat(sessionId: String) = "chat/$sessionId"
}

@Composable
fun ClawApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.SESSIONS
    ) {
        composable(Routes.SESSIONS) {
            SessionListScreen(
                onSessionClick = { sessionId ->
                    navController.navigate(Routes.chat(sessionId))
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
                },
                onMemoryClick = {
                    navController.navigate(Routes.MEMORY)
                }
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            ChatScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.MEMORY) {
            MemoryScreen(onBack = { navController.popBackStack() })
        }
    }
}
