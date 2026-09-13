package com.nova.app

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.*
import com.nova.app.data.SessionToken
import com.nova.app.data.createNovaApi

private data class Tab(val route: String, val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector)

private val tabs = listOf(
  Tab("chat", "Chat", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
  Tab("agents", "Agents", Icons.Filled.AccountTree, Icons.Outlined.AccountTree),
  Tab("activity", "Activity", Icons.Filled.History, Icons.Outlined.History),
  Tab("connections", "Connections", Icons.Filled.Link, Icons.Outlined.Link),
  Tab("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

// §5 primary navigation: Chat | Agents | Activity | Connections | Settings. Chat first + New Chat.
@Composable
fun NovaNav(session: SessionToken) {
  val nav = rememberNavController()
  val api = remember { createNovaApi(session) }
  var authenticated by remember { mutableStateOf(session.get() != null) }
  if (!authenticated) {
    LoginScreen(api) { token -> session.save(token); authenticated = true }
    return
  }
  val currentRoute by nav.currentBackStackEntryAsState()
  Scaffold(
    bottomBar = {
      NavigationBar {
        val current = currentRoute?.destination?.route
        tabs.forEach { t ->
          val selected = current == t.route
          NavigationBarItem(
            selected = selected,
            onClick = {
              if (current != t.route) {
                nav.navigate(t.route) {
                  popUpTo("chat") { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              }
            },
            label = { Text(t.label) },
            icon = {
              Icon(
                if (selected) t.selectedIcon else t.unselectedIcon,
                contentDescription = t.label,
              )
            },
          )
        }
      }
    }
  ) { pad ->
    NavHost(nav, startDestination = "chat", Modifier.padding(pad)) {
      composable(
        "chat",
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(150)) },
      ) { ChatScreen(api, session) }
      composable(
        "agents",
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(150)) },
      ) { AgentsScreen(api) }
      composable(
        "activity",
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(150)) },
      ) { ActivityScreen(api) }
      composable(
        "connections",
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(150)) },
      ) { ConnectionsScreen() }
      composable(
        "settings",
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(150)) },
      ) { SettingsScreen(session) { session.clear(); authenticated = false } }
    }
  }
}
