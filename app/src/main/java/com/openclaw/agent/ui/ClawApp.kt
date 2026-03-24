package com.openclaw.agent.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.openclaw.agent.ui.chat.ChatScreen
import com.openclaw.agent.ui.memory.MemoryScreen
import com.openclaw.agent.ui.mijia.MijiaLoginActivity
import com.openclaw.agent.ui.sessions.SessionListScreen
import com.openclaw.agent.ui.settings.SettingsScreen
import com.openclaw.agent.ui.skills.SkillsScreen

object Routes {
    const val SESSIONS = "sessions"
    const val CHAT = "chat/{sessionId}"
    const val SETTINGS = "settings"
    const val MEMORY = "memory"
    const val SKILLS = "skills"

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
                },
                onSkillsClick = {
                    navController.navigate(Routes.SKILLS)
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
            val context = LocalContext.current
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onMijiaLogin = {
                    context.startActivity(Intent(context, MijiaLoginActivity::class.java))
                }
            )
        }

        composable(Routes.MEMORY) {
            MemoryScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SKILLS) {
            SkillsScreen(onBack = { navController.popBackStack() })
        }
    }
}
