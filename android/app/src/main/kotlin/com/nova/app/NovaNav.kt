package com.nova.app

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import com.nova.app.data.SessionToken
import com.nova.app.data.createNovaApi

@Composable
fun NovaNav(session: SessionToken, onAccentChanged: () -> Unit = {}) {
  val nav = rememberNavController()
  val api = remember { createNovaApi(session) }
  var authenticated by remember { mutableStateOf(session.get() != null) }
  if (!authenticated) {
    LoginScreen(api) { token -> session.save(token); authenticated = true }
    return
  }
  Scaffold(
    containerColor = MaterialTheme.colorScheme.surface,
  ) { pad ->
    NavHost(nav, startDestination = "chat", Modifier.padding(pad)) {
      composable(
        "chat",
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(150)) },
      ) { ChatScreen(api, session, onSettingsClick = { nav.navigate("settings") }) }
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
      ) {
        SettingsScreen(
          session,
          onLogout = { session.clear(); authenticated = false },
          onBack = { nav.popBackStack() },
          onAccentChanged = onAccentChanged,
        )
      }
    }
  }
}
